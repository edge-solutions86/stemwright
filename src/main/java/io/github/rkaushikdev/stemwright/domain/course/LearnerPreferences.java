package io.github.rkaushikdev.stemwright.domain.course;

import io.github.rkaushikdev.stemwright.domain.Identifiers;
import io.github.rkaushikdev.stemwright.domain.drill.DrillPolicy;
import io.github.rkaushikdev.stemwright.domain.drill.DrillPolicyPatch;
import io.github.rkaushikdev.stemwright.domain.errors.ProblemCollector;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a learner has asked for, and where it sits in the order.
 *
 * <p>Four layers set drilling policy and they are not equal. The institution sets a default, a
 * course tightens it for a cohort, an enrolment adjusts it for one person, and the learner asks for
 * the rest. A learner may always ask for less work than the layer above allows and never for more,
 * because an assignment that can be widened by the person being assessed is not an assignment.
 *
 * <p>Build one through {@link #of(PreferencesInput)}, which checks it; the canonical constructor
 * only copies what it is given.
 *
 * @param learnerId the learner asking
 * @param patch the policy fields they want changed
 * @param toleranceLevel the tolerance level they want marking at
 * @param ladderKey the interval ladder they want
 */
public record LearnerPreferences(
        String learnerId, DrillPolicyPatch patch, Optional<String> toleranceLevel, Optional<String> ladderKey) {

    private static final int DAYS_IN_WEEK = 7;

    public LearnerPreferences {
        Objects.requireNonNull(learnerId, "learnerId");
        Objects.requireNonNull(patch, "patch");
        Objects.requireNonNull(toleranceLevel, "toleranceLevel");
        Objects.requireNonNull(ladderKey, "ladderKey");
    }

    /**
     * Checks what a learner asked for. Resting every day of the week is refused; a tolerance level or
     * ladder key that is not a slug fails on the spot.
     */
    public static LearnerPreferences of(PreferencesInput input) {
        ProblemCollector problems = new ProblemCollector();
        DrillPolicyPatch patch = input.patch().orElse(DrillPolicyPatch.EMPTY);

        problems.addIf(
                patch.restDays().filter(days -> days.size() == DAYS_IN_WEEK).isPresent(),
                "patch.restDays",
                "would rest every day of the week");
        problems.throwIfAny("preferences for " + input.learnerId() + " are not usable");

        return new LearnerPreferences(
                input.learnerId(),
                patch,
                input.toleranceLevel().map(level -> Identifiers.asSlug("toleranceLevel", level)),
                input.ladderKey().map(key -> Identifiers.asSlug("ladderKey", key)));
    }

    /** A learner who has asked for nothing. */
    public static LearnerPreferences none(String learnerId) {
        return new LearnerPreferences(learnerId, DrillPolicyPatch.EMPTY, Optional.empty(), Optional.empty());
    }

    /**
     * Trims a patch so it can only reduce the budgets of what it is applied to. A learner asking for
     * more new cells a day than the course allows gets the course number, and the request is
     * reported rather than silently dropped.
     *
     * <p>Only the three budgets are policed: new cells a day, reviews a day and the per lexeme cap.
     * Every other field of the patch passes through untouched.
     */
    public static ClampedPatch clampToCeiling(DrillPolicy base, DrillPolicyPatch patch) {
        List<String> refused = new ArrayList<>();
        Optional<Integer> newPerDay = withinCeiling("newPerDay", patch.newPerDay(), base.newPerDay(), refused);
        Optional<Integer> reviewsPerDay =
                withinCeiling("reviewsPerDay", patch.reviewsPerDay(), base.reviewsPerDay(), refused);
        Optional<Integer> perLexemeCap =
                withinCeiling("perLexemeCap", patch.perLexemeCap(), base.perLexemeCap(), refused);

        DrillPolicyPatch clamped = new DrillPolicyPatch(
                newPerDay,
                reviewsPerDay,
                perLexemeCap,
                patch.rowSpacing(),
                patch.buryTwins(),
                patch.leechThreshold(),
                patch.ladderKey(),
                patch.restDays());
        return new ClampedPatch(clamped, refused);
    }

    /**
     * Applies the layers in order and reports what the learner asked for and did not get. The
     * enrolment patch is not clamped: it was set by an instructor and is allowed to raise as well as
     * lower.
     *
     * @param base the institution's policy
     * @param coursePatch what the course changes for its cohort
     * @param enrolment the learner's enrolment, when they have one
     * @param preferences what the learner asked for
     */
    public static PolicyResolution resolvePolicyFor(
            DrillPolicy base,
            DrillPolicyPatch coursePatch,
            Optional<Enrolment> enrolment,
            LearnerPreferences preferences) {
        DrillPolicy withCourse = base.applyPatch(coursePatch);
        DrillPolicy withEnrolment =
                withCourse.applyPatch(enrolment.map(Enrolment::policyPatch).orElse(DrillPolicyPatch.EMPTY));
        ClampedPatch clamped = clampToCeiling(withEnrolment, preferences.patch());
        return new PolicyResolution(withEnrolment.applyPatch(clamped.patch()), clamped.refused());
    }

    /**
     * These preferences with the patch merged over the old one rather than replacing it, checked
     * again as a whole.
     */
    public LearnerPreferences withPatch(DrillPolicyPatch next) {
        PreferencesInput.Builder input = PreferencesInput.builder(learnerId).patch(patch.mergedWith(next));
        toleranceLevel.ifPresent(input::toleranceLevel);
        ladderKey.ifPresent(input::ladderKey);
        return of(input.build());
    }

    private static Optional<Integer> withinCeiling(
            String field, Optional<Integer> asked, int ceiling, List<String> refused) {
        if (asked.filter(value -> value > ceiling).isPresent()) {
            refused.add(field);
            return Optional.empty();
        }
        return asked;
    }

    /**
     * A learner's patch with every budget above the ceiling taken out.
     *
     * @param patch what is left of the patch
     * @param refused the budgets taken out, in the order new cells, reviews, per lexeme cap
     */
    public record ClampedPatch(DrillPolicyPatch patch, List<String> refused) {

        public ClampedPatch {
            Objects.requireNonNull(patch, "patch");
            refused = List.copyOf(refused);
        }
    }

    /**
     * The policy a learner drills under once every layer has been applied.
     *
     * @param policy the resolved policy
     * @param refused the budgets the learner asked to raise and did not get
     */
    public record PolicyResolution(DrillPolicy policy, List<String> refused) {

        public PolicyResolution {
            Objects.requireNonNull(policy, "policy");
            refused = List.copyOf(refused);
        }

        /** One sentence for the learner saying what was left alone, if anything. */
        public String describeRefusals() {
            if (refused.isEmpty()) {
                return "every preference was applied";
            }
            return String.join(", ", refused) + " were above the ceiling and were left alone";
        }
    }
}
