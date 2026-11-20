package io.github.rkaushikdev.stemwright.domain.course;

import io.github.rkaushikdev.stemwright.domain.errors.ProblemCollector;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Everybody enrolled on one course, each at most once.
 *
 * <p>Build one through {@link #of(Course, List)}, which checks it; the canonical constructor only
 * copies what it is given, which is how an empty roster for a course not yet stored is written.
 *
 * @param courseKey the key of the course
 * @param enrolments the enrolments in the order given
 */
public record Roster(String courseKey, List<Enrolment> enrolments) {

    public Roster {
        Objects.requireNonNull(courseKey, "courseKey");
        enrolments = List.copyOf(enrolments);
    }

    /**
     * Checks that every enrolment belongs to the course and that nobody is on it twice. Each problem
     * is reported under the learner's id.
     */
    public static Roster of(Course course, List<Enrolment> enrolments) {
        ProblemCollector problems = new ProblemCollector();
        Set<String> seen = new HashSet<>();
        for (Enrolment enrolment : enrolments) {
            if (!enrolment.courseKey().equals(course.key())) {
                problems.add(
                        enrolment.learnerId(), "is enrolled on " + enrolment.courseKey() + ", not " + course.key());
                continue;
            }
            if (!seen.add(enrolment.learnerId())) {
                problems.add(enrolment.learnerId(), "is enrolled twice");
            }
        }
        problems.throwIfAny("roster for " + course.key() + " is not usable");
        return new Roster(course.key(), enrolments);
    }

    public Optional<Enrolment> enrolmentOf(String learnerId) {
        return enrolments.stream()
                .filter(enrolment -> enrolment.learnerId().equals(learnerId))
                .findFirst();
    }

    /** The enrolments active on the day, in roster order. */
    public List<Enrolment> activeOn(LocalDate on) {
        return enrolments.stream().filter(enrolment -> enrolment.isActiveOn(on)).toList();
    }

    /** The ids of the learners who are graded, in id order. */
    public List<String> gradedLearners() {
        return enrolments.stream()
                .filter(Enrolment::isGraded)
                .map(Enrolment::learnerId)
                .sorted()
                .toList();
    }
}
