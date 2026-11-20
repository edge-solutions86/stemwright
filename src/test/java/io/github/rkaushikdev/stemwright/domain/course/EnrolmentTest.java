package io.github.rkaushikdev.stemwright.domain.course;

import static io.github.rkaushikdev.stemwright.support.Course.MICHAELMAS;
import static io.github.rkaushikdev.stemwright.support.Problems.problemMessages;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.drill.DrillPolicyPatch;
import io.github.rkaushikdev.stemwright.domain.errors.ConflictException;
import io.github.rkaushikdev.stemwright.domain.mastery.ShieldInput;
import io.github.rkaushikdev.stemwright.domain.mastery.ShieldSet;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import io.github.rkaushikdev.stemwright.domain.time.DayRange;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EnrolmentTest {

    private static final Enrolment STUDENT = Enrolment.of(MICHAELMAS, EnrolmentInput.of("lrn_one"));
    private static final Enrolment LATECOMER = Enrolment.of(
            MICHAELMAS, EnrolmentInput.builder("lrn_two").from(d("2024-11-01")).build());
    private static final Enrolment AUDITOR = Enrolment.of(
            MICHAELMAS,
            EnrolmentInput.builder("lrn_three").role(EnrolmentRole.AUDITOR).build());

    private static LocalDate d(String value) {
        return CivilDates.parseCivilDate(value);
    }

    private static DayRange r(String from, String to) {
        return DayRange.of(d(from), d(to));
    }

    private static DrillPolicyPatch.Builder patch() {
        return DrillPolicyPatch.builder();
    }

    @Nested
    class Of {

        @Test
        void defaultsTheWindowToTheWholeTerm() {
            assertThat(STUDENT.window()).isEqualTo(MICHAELMAS.term());
        }

        @Test
        void defaultsTheRoleToLearner() {
            assertThat(STUDENT.role()).isEqualTo(EnrolmentRole.LEARNER);
        }

        @Test
        void takesANarrowerWindow() {
            assertThat(LATECOMER.window()).isEqualTo(r("2024-11-01", "2024-12-13"));
        }

        @Test
        void clipsAWindowThatOverhangsTheTermRatherThanRefusingIt() {
            Enrolment early = Enrolment.of(
                    MICHAELMAS,
                    EnrolmentInput.builder("lrn_four")
                            .from(d("2024-09-01"))
                            .to(d("2025-01-31"))
                            .build());
            assertThat(early.window()).isEqualTo(MICHAELMAS.term());
        }

        @Test
        void refusesAWindowEntirelyOutsideTheTerm() {
            assertThat(problemMessages(() -> Enrolment.of(
                            MICHAELMAS,
                            EnrolmentInput.builder("lrn_five")
                                    .from(d("2025-02-01"))
                                    .to(d("2025-02-28"))
                                    .build())))
                    .containsExactly("window lies entirely outside the term 2024-10-07 to 2024-12-13");
        }

        @Test
        void refusesAWindowThatEndsBeforeItStarts() {
            assertThat(problemMessages(() -> Enrolment.of(
                            MICHAELMAS,
                            EnrolmentInput.builder("lrn_six")
                                    .from(d("2024-12-01"))
                                    .to(d("2024-11-01"))
                                    .build())))
                    .hasSize(1);
        }

        @Test
        void startsWithNoShieldsAndNoPolicyPatch() {
            assertThat(STUDENT.shields().windows()).isEmpty();
            assertThat(STUDENT.policyPatch()).isEqualTo(DrillPolicyPatch.EMPTY);
        }

        @Test
        void namesTheCourseItBelongsTo() {
            assertThat(STUDENT.courseKey()).isEqualTo("latin-1a");
        }
    }

    @Nested
    class IsEnrolmentRole {

        @Test
        void acceptsTheThreeRolesAndNothingElse() {
            assertThat(EnrolmentRole.isEnrolmentRole("learner")).isTrue();
            assertThat(EnrolmentRole.isEnrolmentRole("auditor")).isTrue();
            assertThat(EnrolmentRole.isEnrolmentRole("assistant")).isTrue();
            assertThat(EnrolmentRole.isEnrolmentRole("lecturer")).isFalse();
        }
    }

    @Nested
    class IsActiveOn {

        @Test
        void isTrueInsideTheWindow() {
            assertThat(STUDENT.isActiveOn(d("2024-11-01"))).isTrue();
        }

        @Test
        void isFalseBeforeALateEnrolmentStarts() {
            assertThat(LATECOMER.isActiveOn(d("2024-10-20"))).isFalse();
            assertThat(LATECOMER.isActiveOn(d("2024-11-01"))).isTrue();
        }

        @Test
        void isFalseOnceWithdrawnWhateverTheWindowSays() {
            assertThat(STUDENT.withdraw().isActiveOn(d("2024-11-01"))).isFalse();
        }
    }

    @Nested
    class WithdrawAndReinstate {

        @Test
        void withdrawsOnceAndRefusesASecondTime() {
            Enrolment gone = STUDENT.withdraw();
            assertThat(gone.withdrawn()).isTrue();
            assertThatThrownBy(gone::withdraw).isInstanceOf(ConflictException.class);
        }

        @Test
        void reinstatesAndLeavesAnActiveEnrolmentAlone() {
            assertThat(STUDENT.withdraw().reinstate().withdrawn()).isFalse();
            assertThat(STUDENT.reinstate()).isSameAs(STUDENT);
        }
    }

    @Nested
    class WithShieldsAndWithPolicyPatch {

        @Test
        void attachesShields() {
            Enrolment shielded =
                    STUDENT.withShields(ShieldSet.of(List.of(ShieldInput.of(r("2024-11-04", "2024-11-08")))));
            assertThat(shielded.shields().windows()).hasSize(1);
            assertThat(STUDENT.shields().windows()).isEmpty();
        }

        @Test
        void mergesPolicyPatchesRatherThanReplacingThem() {
            Enrolment once = STUDENT.withPolicyPatch(patch().newPerDay(4).build());
            Enrolment twice = once.withPolicyPatch(patch().reviewsPerDay(20).build());
            assertThat(twice.policyPatch())
                    .isEqualTo(patch().newPerDay(4).reviewsPerDay(20).build());
        }

        @Test
        void letsALaterPatchOverwriteAnEarlierOne() {
            Enrolment once = STUDENT.withPolicyPatch(patch().newPerDay(4).build());
            assertThat(once.withPolicyPatch(patch().newPerDay(9).build())
                            .policyPatch()
                            .newPerDay())
                    .contains(9);
        }
    }

    @Nested
    class IsGraded {

        @Test
        void gradesALearnerAndNobodyElse() {
            assertThat(STUDENT.isGraded()).isTrue();
            assertThat(AUDITOR.isGraded()).isFalse();
            assertThat(STUDENT.withdraw().isGraded()).isFalse();
        }
    }

    @Nested
    class RosterOf {

        private final Roster list = Roster.of(MICHAELMAS, List.of(STUDENT, LATECOMER, AUDITOR));

        @Test
        void holdsEveryEnrolment() {
            assertThat(list.enrolments()).hasSize(3);
            assertThat(list.enrolmentOf("lrn_two")).containsSame(LATECOMER);
            assertThat(list.enrolmentOf("lrn_nine")).isEmpty();
        }

        @Test
        void refusesAnEnrolmentFromAnotherCourse() {
            Enrolment stray = new Enrolment(
                    "greek-2",
                    STUDENT.learnerId(),
                    STUDENT.role(),
                    STUDENT.window(),
                    STUDENT.shields(),
                    STUDENT.policyPatch(),
                    STUDENT.withdrawn());
            assertThat(problemMessages(() -> Roster.of(MICHAELMAS, List.of(stray))))
                    .containsExactly("lrn_one is enrolled on greek-2, not latin-1a");
        }

        @Test
        void refusesTheSameLearnerTwice() {
            assertThat(problemMessages(() -> Roster.of(MICHAELMAS, List.of(STUDENT, STUDENT))))
                    .containsExactly("lrn_one is enrolled twice");
        }

        @Test
        void reportsWhoIsActiveOnADay() {
            assertThat(list.activeOn(d("2024-10-20")).stream().map(Enrolment::learnerId))
                    .containsExactly("lrn_one", "lrn_three");
            assertThat(list.activeOn(d("2024-11-05"))).hasSize(3);
        }

        @Test
        void namesTheGradedLearnersInIdOrder() {
            assertThat(list.gradedLearners()).containsExactly("lrn_one", "lrn_two");
        }
    }
}
