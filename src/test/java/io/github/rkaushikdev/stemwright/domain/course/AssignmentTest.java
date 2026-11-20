package io.github.rkaushikdev.stemwright.domain.course;

import static io.github.rkaushikdev.stemwright.support.Course.MICHAELMAS;
import static io.github.rkaushikdev.stemwright.support.Problems.problemMessages;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.domain.IdKind;
import io.github.rkaushikdev.stemwright.domain.Identifiers;
import io.github.rkaushikdev.stemwright.domain.drill.DrillPolicyPatch;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import io.github.rkaushikdev.stemwright.domain.time.DayRange;
import io.github.rkaushikdev.stemwright.support.Profiles;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AssignmentTest {

    private static final Assignment WEEK_3 = make(input("week-3", "all", "2024-10-21", "2024-10-27")
            .title("Singular cases")
            .narrowing("number=sg")
            .masteredCells(12));

    private static LocalDate d(String value) {
        return CivilDates.parseCivilDate(value);
    }

    private static AssignmentInput.Builder input(String key, String deckKey, String from, String to) {
        return AssignmentInput.builder(key, deckKey, d(from), d(to));
    }

    private static Assignment make(AssignmentInput.Builder input) {
        return Assignment.of(Profiles.LATIN_NOUN, MICHAELMAS, input.build());
    }

    private static Assignment bareWeek4() {
        return make(input("week-4", "core", "2024-10-28", "2024-11-03").practiceDays(4));
    }

    @Nested
    class Of {

        @Test
        void keepsTheKeyDeckAndWindow() {
            assertThat(WEEK_3.key()).isEqualTo("week-3");
            assertThat(WEEK_3.deckKey()).isEqualTo("all");
            assertThat(WEEK_3.window()).isEqualTo(DayRange.of(d("2024-10-21"), d("2024-10-27")));
            assertThat(Identifiers.isId(IdKind.ASSIGNMENT, WEEK_3.id())).isTrue();
        }

        @Test
        void derivesTheIdFromTheCourseAndTheKey() {
            assertThat(WEEK_3.id()).isEqualTo(Assignment.idFor("latin-1a", "week-3"));
        }

        @Test
        void parsesTheNarrowingSelector() {
            assertThat(WEEK_3.narrowing().format()).isEqualTo("number=sg");
        }

        @Test
        void defaultsTheTitleToTheKeyAndTheNarrowingToNothing() {
            Assignment bare = bareWeek4();
            assertThat(bare.title()).isEqualTo("week-4");
            assertThat(bare.narrowing().clauses()).isEmpty();
        }

        @Test
        void refusesADeckTheCourseDoesNotTeach() {
            assertThat(problemMessages(() -> make(
                            input("x", "third-year", "2024-10-21", "2024-10-27").practiceDays(1))))
                    .containsExactly("deckKey is not a deck of latin-1a: third-year");
        }

        @Test
        void refusesAWindowThatMissesTheTermEntirely() {
            assertThat(problemMessages(() ->
                            make(input("x", "all", "2025-02-01", "2025-02-07").practiceDays(1))))
                    .containsExactly("window falls outside the term 2024-10-07 to 2024-12-13");
        }

        @Test
        void acceptsAWindowThatOverlapsTheTermAtOneEnd() {
            Assignment straddle =
                    make(input("revision", "all", "2024-12-09", "2024-12-20").practiceDays(5));
            assertThat(straddle.window().to()).isEqualTo(d("2024-12-20"));
        }

        @Test
        void refusesAWindowThatEndsBeforeItStarts() {
            assertThat(problemMessages(() ->
                            make(input("x", "all", "2024-10-27", "2024-10-21").practiceDays(1))))
                    .hasSize(1);
        }

        @Test
        void refusesANarrowingTheSchemaRejects() {
            assertThat(problemMessages(() -> make(input("x", "all", "2024-10-21", "2024-10-27")
                            .narrowing("tense=pres")
                            .practiceDays(1))))
                    .containsExactly("narrowing tense is not a dimension of latin");
        }

        @Test
        void refusesAnAssignmentWithNoTargetAtAll() {
            assertThat(problemMessages(() -> make(input("x", "all", "2024-10-21", "2024-10-27"))))
                    .containsExactly("target must set at least one of masteredCells and practiceDays");
        }

        @Test
        void refusesATargetBelowOne() {
            assertThat(problemMessages(() ->
                            make(input("x", "all", "2024-10-21", "2024-10-27").masteredCells(0))))
                    .anyMatch(message -> message.contains("masteredCells"));
        }

        @Test
        void takesBothTargetsAtOnce() {
            Assignment both = make(input("week-5", "all", "2024-11-04", "2024-11-10")
                    .masteredCells(8)
                    .practiceDays(5));
            assertThat(both.target()).isEqualTo(new AssignmentTarget(Optional.of(8), Optional.of(5)));
        }

        @Test
        void carriesAToleranceOverrideAndAPolicyPatch() {
            Assignment strict = make(input("exam", "all", "2024-12-09", "2024-12-13")
                    .masteredCells(20)
                    .toleranceLevel("exact")
                    .policyPatch(DrillPolicyPatch.builder().newPerDay(0).build()));
            assertThat(strict.toleranceLevel()).contains("exact");
            assertThat(strict.policyPatch())
                    .isEqualTo(DrillPolicyPatch.builder().newPerDay(0).build());
        }
    }

    @Nested
    class TheWindowPredicates {

        @Test
        void isOpenOnBothEndpoints() {
            assertThat(WEEK_3.isOpenOn(d("2024-10-21"))).isTrue();
            assertThat(WEEK_3.isOpenOn(d("2024-10-27"))).isTrue();
            assertThat(WEEK_3.isOpenOn(d("2024-10-28"))).isFalse();
        }

        @Test
        void isOverdueOnlyAfterTheLastDay() {
            assertThat(WEEK_3.isOverdueOn(d("2024-10-27"))).isFalse();
            assertThat(WEEK_3.isOverdueOn(d("2024-10-28"))).isTrue();
        }

        @Test
        void hasStartedFromTheFirstDayOn() {
            assertThat(WEEK_3.hasStartedOn(d("2024-10-20"))).isFalse();
            assertThat(WEEK_3.hasStartedOn(d("2024-10-21"))).isTrue();
            assertThat(WEEK_3.hasStartedOn(d("2024-11-01"))).isTrue();
        }
    }

    @Nested
    class DescribeTarget {

        @Test
        void readsOneTargetOut() {
            assertThat(new AssignmentTarget(Optional.of(12), Optional.empty()).describe())
                    .isEqualTo("12 cells to the top of the ladder");
            assertThat(new AssignmentTarget(Optional.empty(), Optional.of(4)).describe())
                    .isEqualTo("4 days of practice");
        }

        @Test
        void joinsTwoTargets() {
            assertThat(new AssignmentTarget(Optional.of(8), Optional.of(5)).describe())
                    .isEqualTo("8 cells to the top of the ladder and 5 days of practice");
        }
    }

    @Nested
    class Describe {

        @Test
        void namesTheNarrowingWhenThereIsOne() {
            assertThat(WEEK_3.describe())
                    .isEqualTo("Singular cases: all narrowed to number=sg, 12 cells to the top of the ladder,"
                            + " due 2024-10-27");
        }

        @Test
        void namesTheBareDeckWhenThereIsNot() {
            assertThat(bareWeek4().describe()).isEqualTo("week-4: core, 4 days of practice, due 2024-11-03");
        }
    }
}
