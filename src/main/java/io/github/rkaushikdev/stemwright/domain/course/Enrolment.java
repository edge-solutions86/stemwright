package io.github.rkaushikdev.stemwright.domain.course;

import io.github.rkaushikdev.stemwright.domain.drill.DrillPolicyPatch;
import io.github.rkaushikdev.stemwright.domain.errors.ConflictException;
import io.github.rkaushikdev.stemwright.domain.errors.ProblemCollector;
import io.github.rkaushikdev.stemwright.domain.mastery.ShieldSet;
import io.github.rkaushikdev.stemwright.domain.time.DayRange;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * A learner's membership of a course. It carries a window of its own, because people join late and
 * leave early, and everything the learner is measured on is measured inside that window rather than
 * the course term.
 *
 * <p>The window is clipped to the term rather than checked against it. Somebody enrolled from a
 * date before the course starts is enrolled from the first day of the course, which is what the
 * registry means and what an error message about it would only be pedantry about.
 *
 * <p>Build one through {@link #of(Course, EnrolmentInput)}, which checks it; the canonical
 * constructor only copies what it is given.
 *
 * @param courseKey the key of the course
 * @param learnerId the account enrolled
 * @param role what they are on the course for
 * @param window the days they are measured on, never outside the term
 * @param shields the days they are excused from
 * @param policyPatch an instructor's adjustment of the drilling policy for this one person
 * @param withdrawn whether they have left the course
 */
public record Enrolment(
        String courseKey,
        String learnerId,
        EnrolmentRole role,
        DayRange window,
        ShieldSet shields,
        DrillPolicyPatch policyPatch,
        boolean withdrawn) {

    public Enrolment {
        Objects.requireNonNull(courseKey, "courseKey");
        Objects.requireNonNull(learnerId, "learnerId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(shields, "shields");
        Objects.requireNonNull(policyPatch, "policyPatch");
    }

    /**
     * Enrols somebody on the course. The window defaults to the whole term and is clipped to it.
     *
     * @throws io.github.rkaushikdev.stemwright.domain.errors.ValidationException for a window that
     *     ends before it starts or lies entirely outside the term
     */
    public static Enrolment of(Course course, EnrolmentInput input) {
        ProblemCollector problems = new ProblemCollector();
        EnrolmentRole role = input.role().orElse(EnrolmentRole.LEARNER);
        DayRange term = course.term();

        Optional<DayRange> asked = problems.absorbFlat(
                "window",
                () -> new DayRange(input.from().orElse(term.from()), input.to().orElse(term.to())));
        Optional<DayRange> clipped = asked.flatMap(range -> range.intersect(term));
        problems.addIf(
                asked.isPresent() && clipped.isEmpty(),
                "window",
                "lies entirely outside the term " + term.from() + " to " + term.to());

        problems.throwIfAny("enrolment of " + input.learnerId() + " is not usable");

        return new Enrolment(
                course.key(),
                input.learnerId(),
                role,
                clipped.orElseThrow(),
                input.shields().orElse(ShieldSet.NONE),
                input.policyPatch().orElse(DrillPolicyPatch.EMPTY),
                false);
    }

    /** Whether the learner is on the course that day: inside the window and not withdrawn. */
    public boolean isActiveOn(LocalDate on) {
        return !withdrawn && window.contains(on);
    }

    /**
     * The same enrolment, withdrawn.
     *
     * @throws ConflictException when it has already been withdrawn
     */
    public Enrolment withdraw() {
        if (withdrawn) {
            throw new ConflictException(learnerId + " has already withdrawn from " + courseKey);
        }
        return new Enrolment(courseKey, learnerId, role, window, shields, policyPatch, true);
    }

    /** The same enrolment, active again; this very instance when it was never withdrawn. */
    public Enrolment reinstate() {
        return withdrawn ? new Enrolment(courseKey, learnerId, role, window, shields, policyPatch, false) : this;
    }

    /** The same enrolment with these shields in place of the old ones. */
    public Enrolment withShields(ShieldSet next) {
        return new Enrolment(courseKey, learnerId, role, window, next, policyPatch, withdrawn);
    }

    /**
     * The same enrolment with the patch merged over the one it had, rather than replacing it: a
     * field the new patch sets wins and a field it leaves out keeps its old value.
     */
    public Enrolment withPolicyPatch(DrillPolicyPatch patch) {
        return new Enrolment(courseKey, learnerId, role, window, shields, policyPatch.mergedWith(patch), withdrawn);
    }

    /** Only a learner is graded. An auditor drills and an assistant helps teach. */
    public boolean isGraded() {
        return role == EnrolmentRole.LEARNER && !withdrawn;
    }
}
