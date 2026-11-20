package io.github.rkaushikdev.stemwright.domain.report;

import static io.github.rkaushikdev.stemwright.support.Course.MICHAELMAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.stemwright.domain.course.AccountRole;
import io.github.rkaushikdev.stemwright.domain.course.Actor;
import io.github.rkaushikdev.stemwright.domain.course.AssignmentProgress;
import io.github.rkaushikdev.stemwright.domain.course.CourseScope;
import io.github.rkaushikdev.stemwright.domain.course.Enrolment;
import io.github.rkaushikdev.stemwright.domain.course.EnrolmentInput;
import io.github.rkaushikdev.stemwright.domain.course.Roster;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CohortReportTest {

    private static final LocalDate ON = CivilDates.parseCivilDate("2024-10-27");

    private static final Actor STUDENT = new Actor("lrn_one", AccountRole.LEARNER);
    private static final Actor TEACHER = new Actor("lrn_teacher", AccountRole.INSTRUCTOR);
    private static final Actor STRANGER = new Actor("lrn_other", AccountRole.INSTRUCTOR);

    private static final CourseScope SCOPE = new CourseScope(
            MICHAELMAS, Roster.of(MICHAELMAS, List.of(enrol("lrn_one"), enrol("lrn_two"), enrol("lrn_three"))));

    private static final List<AssignmentProgress> ROWS = List.of(
            progress("lrn_one", 3, 2, false), progress("lrn_two", 9, 5, true), progress("lrn_three", 6, 3, false));

    private static Enrolment enrol(String learnerId) {
        return Enrolment.of(MICHAELMAS, EnrolmentInput.of(learnerId));
    }

    private static AssignmentProgress progress(String learnerId, int mastered, int practiceDays, boolean met) {
        return new AssignmentProgress("week-3", learnerId, ON, 10, mastered, practiceDays, met, met, met, false, false);
    }

    private static CohortReport reportFor(Actor actor) {
        return CohortReport.of(actor, SCOPE, ROWS, "week-3", ON);
    }

    private static CohortReport emptyReport() {
        return CohortReport.of(TEACHER, SCOPE, List.of(), "week-3", ON);
    }

    private static List<String> learnersOf(List<CohortRow> rows) {
        return rows.stream().map(CohortRow::learnerId).toList();
    }

    @Nested
    class Median {

        @Test
        void takesTheMiddleOfAnOddList() {
            assertThat(CohortReport.median(List.of(1.0, 3.0, 2.0))).isEqualTo(2.0);
        }

        @Test
        void averagesTheMiddleTwoOfAnEvenList() {
            assertThat(CohortReport.median(List.of(1.0, 2.0, 3.0, 4.0))).isEqualTo(2.5);
        }

        @Test
        void isZeroForAnEmptyList() {
            assertThat(CohortReport.median(List.of())).isEqualTo(0.0);
        }
    }

    @Nested
    class Of {

        @Test
        void showsEveryRowToTheInstructor() {
            CohortReport report = reportFor(TEACHER);
            assertThat(report.rows()).hasSize(3);
            assertThat(report.hidden()).isEqualTo(0);
            assertThat(report.isRedacted()).isFalse();
        }

        @Test
        void sortsByMasteryShareHighestFirst() {
            assertThat(learnersOf(reportFor(TEACHER).rows())).containsExactly("lrn_two", "lrn_three", "lrn_one");
        }

        @Test
        void showsALearnerOnlyTheirOwnRowAndCountsWhatItHid() {
            CohortReport report = reportFor(STUDENT);
            assertThat(learnersOf(report.rows())).containsExactly("lrn_one");
            assertThat(report.hidden()).isEqualTo(2);
            assertThat(report.isRedacted()).isTrue();
        }

        @Test
        void hidesEverythingFromAnInstructorOfAnotherCourse() {
            CohortReport report = reportFor(STRANGER);
            assertThat(report.rows()).isEmpty();
            assertThat(report.hidden()).isEqualTo(3);
        }

        @Test
        void countsHowManyMetTheTargetAmongTheRowsItShows() {
            assertThat(reportFor(TEACHER).metCount()).isEqualTo(1);
            assertThat(reportFor(STUDENT).metCount()).isEqualTo(0);
        }

        @Test
        void readsTheMedianShareOffTheVisibleRows() {
            assertThat(reportFor(TEACHER).medianShare()).isCloseTo(0.6, byLessThan(0.005));
        }

        @Test
        void reportsZerosForAnEmptyCohort() {
            CohortReport report = emptyReport();
            assertThat(report.rows()).isEmpty();
            assertThat(report.medianShare()).isEqualTo(0.0);
            assertThat(report.metShare()).isEqualTo(0.0);
        }

        @Test
        void carriesTheAssignmentKeyAndTheDay() {
            CohortReport report = reportFor(TEACHER);
            assertThat(report.assignmentKey()).isEqualTo("week-3");
            assertThat(report.on()).isEqualTo(CivilDates.parseCivilDate("2024-10-27"));
        }
    }

    @Nested
    class MetShare {

        @Test
        void isTheShareOfVisibleRowsThatMetTheTarget() {
            assertThat(reportFor(TEACHER).metShare()).isCloseTo(1.0 / 3, byLessThan(0.005));
        }
    }

    @Nested
    class FurthestBehind {

        @Test
        void ranksTheUnmetRowsByHowFarTheyHaveToGo() {
            assertThat(learnersOf(reportFor(TEACHER).furthestBehind())).containsExactly("lrn_one", "lrn_three");
        }

        @Test
        void leavesOutRowsThatMetTheTarget() {
            assertThat(learnersOf(reportFor(TEACHER).furthestBehind())).doesNotContain("lrn_two");
        }

        @Test
        void honoursTheLimit() {
            assertThat(reportFor(TEACHER).furthestBehind(1)).hasSize(1);
        }
    }

    @Nested
    class Describe {

        @Test
        void readsTheCountsOut() {
            assertThat(reportFor(TEACHER).describe()).isEqualTo("1 of 3 have met week-3, 33 percent");
        }

        @Test
        void saysHowManyRowsWereHidden() {
            assertThat(reportFor(STUDENT).describe())
                    .isEqualTo("0 of 1 have met week-3, 0 percent, 2 rows hidden from you");
        }

        @Test
        void saysSoWhenEverythingIsHidden() {
            assertThat(reportFor(STRANGER).describe()).isEqualTo("every row of week-3 is hidden from you");
        }

        @Test
        void saysSoWhenNobodyIsWorkingOnIt() {
            assertThat(emptyReport().describe()).isEqualTo("nobody is working on week-3");
        }
    }
}
