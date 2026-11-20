package io.github.rkaushikdev.stemwright.domain.course;

import static io.github.rkaushikdev.stemwright.support.Course.MICHAELMAS;
import static io.github.rkaushikdev.stemwright.support.Problems.problemMessages;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.domain.course.LearnerPreferences.ClampedPatch;
import io.github.rkaushikdev.stemwright.domain.course.LearnerPreferences.PolicyResolution;
import io.github.rkaushikdev.stemwright.domain.drill.DrillPolicy;
import io.github.rkaushikdev.stemwright.domain.drill.DrillPolicyPatch;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LearnerPreferencesTest {

    private static final DrillPolicy BASE = DrillPolicy.of(
            patch().newPerDay(10).reviewsPerDay(60).perLexemeCap(3).build());

    private static DrillPolicyPatch.Builder patch() {
        return DrillPolicyPatch.builder();
    }

    private static LearnerPreferences asking(DrillPolicyPatch patch) {
        return LearnerPreferences.of(
                PreferencesInput.builder("lrn_one").patch(patch).build());
    }

    @Nested
    class Of {

        @Test
        void keepsWhatItWasGiven() {
            LearnerPreferences preferences = LearnerPreferences.of(PreferencesInput.builder("lrn_one")
                    .patch(patch().newPerDay(4).build())
                    .toleranceLevel("Lenient")
                    .ladderKey("Slow-Start")
                    .build());
            assertThat(preferences.patch()).isEqualTo(patch().newPerDay(4).build());
            assertThat(preferences.toleranceLevel()).contains("lenient");
            assertThat(preferences.ladderKey()).contains("slow-start");
        }

        @Test
        void startsEmptyWhenNothingIsAskedFor() {
            LearnerPreferences preferences = LearnerPreferences.none("lrn_one");
            assertThat(preferences.patch()).isEqualTo(DrillPolicyPatch.EMPTY);
            assertThat(preferences.toleranceLevel()).isEmpty();
        }

        @Test
        void refusesRestingEveryDay() {
            assertThat(problemMessages(() -> asking(
                            patch().restDays(List.of(1, 2, 3, 4, 5, 6, 7)).build())))
                    .containsExactly("patch.restDays would rest every day of the week");
        }
    }

    @Nested
    class ClampToCeiling {

        @Test
        void letsASmallerNumberThrough() {
            ClampedPatch clamped =
                    LearnerPreferences.clampToCeiling(BASE, patch().newPerDay(4).build());
            assertThat(clamped.patch()).isEqualTo(patch().newPerDay(4).build());
            assertThat(clamped.refused()).isEmpty();
        }

        @Test
        void refusesALargerOneAndSaysWhich() {
            ClampedPatch clamped = LearnerPreferences.clampToCeiling(
                    BASE, patch().newPerDay(40).build());
            assertThat(clamped.patch()).isEqualTo(DrillPolicyPatch.EMPTY);
            assertThat(clamped.refused()).containsExactly("newPerDay");
        }

        @Test
        void letsAnEqualNumberThrough() {
            assertThat(LearnerPreferences.clampToCeiling(
                                    BASE, patch().newPerDay(10).build())
                            .refused())
                    .isEmpty();
        }

        @Test
        void checksEachBudgetSeparately() {
            ClampedPatch clamped = LearnerPreferences.clampToCeiling(
                    BASE,
                    patch().newPerDay(4).reviewsPerDay(900).perLexemeCap(9).build());
            assertThat(clamped.patch()).isEqualTo(patch().newPerDay(4).build());
            assertThat(clamped.refused()).containsExactly("reviewsPerDay", "perLexemeCap");
        }

        @Test
        void leavesAPatchItDoesNotPoliceAlone() {
            assertThat(LearnerPreferences.clampToCeiling(
                                    BASE, patch().buryTwins(false).build())
                            .patch())
                    .isEqualTo(patch().buryTwins(false).build());
        }

        @Test
        void leavesTheOriginalPatchUntouched() {
            DrillPolicyPatch patch = patch().newPerDay(40).build();
            LearnerPreferences.clampToCeiling(BASE, patch);
            assertThat(patch.newPerDay()).contains(40);
        }
    }

    @Nested
    class ResolvePolicyFor {

        private final Enrolment enrolment = Enrolment.of(MICHAELMAS, EnrolmentInput.of("lrn_one"));

        @Test
        void takesTheBaseWhenNothingElseIsSet() {
            PolicyResolution resolution = LearnerPreferences.resolvePolicyFor(
                    BASE, DrillPolicyPatch.EMPTY, Optional.empty(), LearnerPreferences.none("lrn_one"));
            assertThat(resolution.policy()).isEqualTo(BASE);
            assertThat(resolution.refused()).isEmpty();
        }

        @Test
        void letsTheCourseTightenTheBase() {
            PolicyResolution resolution = LearnerPreferences.resolvePolicyFor(
                    BASE, patch().newPerDay(5).build(), Optional.empty(), LearnerPreferences.none("lrn_one"));
            assertThat(resolution.policy().newPerDay()).isEqualTo(5);
        }

        @Test
        void letsAnEnrolmentRaiseWhatTheCourseSet() {
            Enrolment raised = enrolment.withPolicyPatch(patch().newPerDay(20).build());
            PolicyResolution resolution = LearnerPreferences.resolvePolicyFor(
                    BASE, patch().newPerDay(5).build(), Optional.of(raised), LearnerPreferences.none("lrn_one"));
            assertThat(resolution.policy().newPerDay()).isEqualTo(20);
        }

        @Test
        void letsALearnerAskForLess() {
            LearnerPreferences preferences = asking(patch().newPerDay(2).build());
            PolicyResolution resolution = LearnerPreferences.resolvePolicyFor(
                    BASE, patch().newPerDay(5).build(), Optional.of(enrolment), preferences);
            assertThat(resolution.policy().newPerDay()).isEqualTo(2);
        }

        @Test
        void doesNotLetALearnerAskForMoreAndSaysSo() {
            LearnerPreferences preferences = asking(patch().newPerDay(50).build());
            PolicyResolution resolution = LearnerPreferences.resolvePolicyFor(
                    BASE, patch().newPerDay(5).build(), Optional.of(enrolment), preferences);
            assertThat(resolution.policy().newPerDay()).isEqualTo(5);
            assertThat(resolution.refused()).containsExactly("newPerDay");
        }

        @Test
        void clampsAgainstTheEnrolmentRatherThanTheBase() {
            Enrolment raised = enrolment.withPolicyPatch(patch().newPerDay(20).build());
            LearnerPreferences preferences = asking(patch().newPerDay(15).build());
            PolicyResolution resolution =
                    LearnerPreferences.resolvePolicyFor(BASE, DrillPolicyPatch.EMPTY, Optional.of(raised), preferences);
            assertThat(resolution.policy().newPerDay()).isEqualTo(15);
            assertThat(resolution.refused()).isEmpty();
        }

        @Test
        void letsALearnerSetSomethingThatIsNotABudget() {
            LearnerPreferences preferences = asking(patch().restDays(List.of(7)).build());
            PolicyResolution resolution = LearnerPreferences.resolvePolicyFor(
                    DrillPolicy.DEFAULT, DrillPolicyPatch.EMPTY, Optional.of(enrolment), preferences);
            assertThat(resolution.policy().restDays()).containsExactly(7);
        }
    }

    @Nested
    class DescribeRefusals {

        @Test
        void saysSoWhenEverythingWasApplied() {
            PolicyResolution resolution = LearnerPreferences.resolvePolicyFor(
                    BASE, DrillPolicyPatch.EMPTY, Optional.empty(), LearnerPreferences.none("lrn_one"));
            assertThat(resolution.describeRefusals()).isEqualTo("every preference was applied");
        }

        @Test
        void namesWhatWasRefused() {
            LearnerPreferences preferences =
                    asking(patch().newPerDay(90).reviewsPerDay(900).build());
            PolicyResolution resolution =
                    LearnerPreferences.resolvePolicyFor(BASE, DrillPolicyPatch.EMPTY, Optional.empty(), preferences);
            assertThat(resolution.describeRefusals())
                    .isEqualTo("newPerDay, reviewsPerDay were above the ceiling and were left alone");
        }
    }

    @Nested
    class WithPatch {

        @Test
        void mergesRatherThanReplacing() {
            LearnerPreferences once = LearnerPreferences.none("lrn_one")
                    .withPatch(patch().newPerDay(4).build());
            LearnerPreferences twice = once.withPatch(patch().reviewsPerDay(20).build());
            assertThat(twice.patch())
                    .isEqualTo(patch().newPerDay(4).reviewsPerDay(20).build());
        }

        @Test
        void keepsTheToleranceLevelThroughAnEdit() {
            LearnerPreferences preferences = LearnerPreferences.of(
                    PreferencesInput.builder("lrn_one").toleranceLevel("exact").build());
            assertThat(preferences.withPatch(patch().newPerDay(4).build()).toleranceLevel())
                    .contains("exact");
        }
    }
}
