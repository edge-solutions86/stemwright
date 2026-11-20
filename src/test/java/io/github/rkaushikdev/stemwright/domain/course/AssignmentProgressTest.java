package io.github.rkaushikdev.stemwright.domain.course;

import static io.github.rkaushikdev.stemwright.support.Course.MICHAELMAS;
import static io.github.rkaushikdev.stemwright.support.Drill.DRILL_CONTEXT;
import static io.github.rkaushikdev.stemwright.support.Profiles.LATIN_NOUN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.stemwright.domain.drill.Deck;
import io.github.rkaushikdev.stemwright.domain.drill.DeckInput;
import io.github.rkaushikdev.stemwright.domain.drill.DeckScopeInput;
import io.github.rkaushikdev.stemwright.domain.mastery.Attempt;
import io.github.rkaushikdev.stemwright.domain.mastery.AttemptInput;
import io.github.rkaushikdev.stemwright.domain.mastery.CellStrength;
import io.github.rkaushikdev.stemwright.domain.mastery.Ladder;
import io.github.rkaushikdev.stemwright.domain.mastery.LadderInput;
import io.github.rkaushikdev.stemwright.domain.mastery.MasteryLedger;
import io.github.rkaushikdev.stemwright.domain.ortho.MatchVerdict;
import io.github.rkaushikdev.stemwright.domain.paradigm.Cell;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AssignmentProgressTest {

    private static final LocalDate TERM = d("2024-10-21");
    private static final Ladder FLAT =
            Ladder.of(LadderInput.builder("flat", 1, 3).spread(0).build());

    private static final Deck ALL_DECK = Deck.of(LATIN_NOUN, DeckInput.of("all"));
    private static final Enrolment STUDENT = Enrolment.of(MICHAELMAS, EnrolmentInput.of("lrn_one"));

    private static final Assignment WEEK_3 = Assignment.of(
            LATIN_NOUN,
            MICHAELMAS,
            AssignmentInput.builder("week-3", "all", d("2024-10-21"), d("2024-10-27"))
                    .narrowing("number=sg")
                    .masteredCells(5)
                    .practiceDays(2)
                    .build());

    private static final List<Cell> CELLS = AssignmentProgress.cellsOf(DRILL_CONTEXT, WEEK_3, ALL_DECK, TERM);

    private int sequence = 0;

    private static LocalDate d(String value) {
        return CivilDates.parseCivilDate(value);
    }

    private static Deck pluralDeck() {
        return Deck.of(
                LATIN_NOUN,
                DeckInput.builder("all")
                        .scope(DeckScopeInput.builder().cells("number=pl").build())
                        .build());
    }

    private static CellStrength mastered(String cellId) {
        return CellStrength.fresh(cellId).recordCorrect(FLAT, TERM).recordCorrect(FLAT, TERM);
    }

    /** A ledger holding the first {@code count} selected cells at the top of the ladder. */
    private static MasteryLedger masteredLedger(int count) {
        return MasteryLedger.of(
                "lrn_one",
                CELLS.subList(0, count).stream()
                        .map(cell -> mastered(cell.id()))
                        .toList());
    }

    private static MasteryLedger emptyLedger() {
        return MasteryLedger.empty("lrn_one");
    }

    private Attempt answeredOn(String cellId, String day) {
        sequence += 1;
        return Attempt.of(AttemptInput.builder()
                .learnerId("lrn_one")
                .sessionId("sess_aaaaaa")
                .cellId(cellId)
                .deckKey("all")
                .on(d(day))
                .sequence(sequence)
                .given("x")
                .expected("x")
                .verdict(MatchVerdict.EXACT)
                .toleranceLevel("standard")
                .revisionId("rev_aaaaaa")
                .build());
    }

    private static AssignmentProgress progressOn(String on) {
        return progressOn(on, emptyLedger());
    }

    private static AssignmentProgress progressOn(String on, MasteryLedger ledger) {
        return progressOn(on, ledger, List.of(), STUDENT, ALL_DECK);
    }

    private static AssignmentProgress progressOn(String on, MasteryLedger ledger, List<Attempt> attempts) {
        return progressOn(on, ledger, attempts, STUDENT, ALL_DECK);
    }

    private static AssignmentProgress progressOn(
            String on, MasteryLedger ledger, List<Attempt> attempts, Enrolment enrolment, Deck chosenDeck) {
        return AssignmentProgress.of(DRILL_CONTEXT, WEEK_3, chosenDeck, enrolment, ledger, attempts, FLAT, d(on));
    }

    @Nested
    class CellsOf {

        @Test
        void intersectsTheDeckScopeWithTheAssignmentNarrowing() {
            assertThat(CELLS).hasSize(15);
            assertThat(CELLS.stream().allMatch(cell -> cell.bundle().endsWith("number=sg")))
                    .isTrue();
        }

        @Test
        void isEmptyWhenTheTwoSelectorsDoNotOverlap() {
            assertThat(AssignmentProgress.cellsOf(DRILL_CONTEXT, WEEK_3, pluralDeck(), TERM))
                    .isEmpty();
        }
    }

    @Nested
    class Of {

        @Test
        void countsNothingForALearnerWhoHasNotStarted() {
            AssignmentProgress progress = progressOn("2024-10-21");
            assertThat(progress.mastered()).isEqualTo(0);
            assertThat(progress.practiceDays()).isEqualTo(0);
            assertThat(progress.met()).isFalse();
            assertThat(progress.cells()).isEqualTo(15);
        }

        @Test
        void countsCellsAtTheTopOfTheLadderAsMastered() {
            AssignmentProgress progress = progressOn("2024-10-21", masteredLedger(5));
            assertThat(progress.mastered()).isEqualTo(5);
            assertThat(progress.masteryMet()).isTrue();
            assertThat(progress.practiceMet()).isFalse();
            assertThat(progress.met()).isFalse();
        }

        @Test
        void countsDistinctDaysOfPracticeInsideTheWindow() {
            List<Attempt> attempts = List.of(
                    answeredOn(CELLS.get(0).id(), "2024-10-21"),
                    answeredOn(CELLS.get(1).id(), "2024-10-21"),
                    answeredOn(CELLS.get(2).id(), "2024-10-23"));
            AssignmentProgress progress = progressOn("2024-10-27", emptyLedger(), attempts);
            assertThat(progress.practiceDays()).isEqualTo(2);
            assertThat(progress.practiceMet()).isTrue();
        }

        @Test
        void ignoresPracticeBeforeTheAssignmentOpened() {
            List<Attempt> attempts = List.of(answeredOn(CELLS.get(0).id(), "2024-10-14"));
            assertThat(progressOn("2024-10-27", emptyLedger(), attempts).practiceDays())
                    .isEqualTo(0);
        }

        @Test
        void ignoresPracticeOutsideTheEnrolmentWindow() {
            Enrolment latecomer = Enrolment.of(
                    MICHAELMAS,
                    EnrolmentInput.builder("lrn_two").from(d("2024-10-25")).build());
            List<Attempt> attempts = List.of(answeredOn(CELLS.get(0).id(), "2024-10-22"));
            AssignmentProgress progress = progressOn("2024-10-27", emptyLedger(), attempts, latecomer, ALL_DECK);
            assertThat(progress.practiceDays()).isEqualTo(0);
        }

        @Test
        void stopsCountingAtTodayRatherThanAtTheDeadline() {
            List<Attempt> attempts = List.of(answeredOn(CELLS.get(0).id(), "2024-10-26"));
            assertThat(progressOn("2024-10-22", emptyLedger(), attempts).practiceDays())
                    .isEqualTo(0);
        }

        @Test
        void reportsBothTargetsMet() {
            List<Attempt> attempts = List.of(
                    answeredOn(CELLS.get(0).id(), "2024-10-21"),
                    answeredOn(CELLS.get(1).id(), "2024-10-23"));
            AssignmentProgress progress = progressOn("2024-10-27", masteredLedger(6), attempts);
            assertThat(progress.met()).isTrue();
            assertThat(progress.unmetTargets()).isEmpty();
        }

        @Test
        void marksTheAssignmentOverdueAfterTheDeadline() {
            AssignmentProgress progress = progressOn("2024-11-01");
            assertThat(progress.overdue()).isTrue();
            assertThat(progress.unmetTargets()).containsExactly("mastery", "practice");
        }

        @Test
        void reportsANarrowingThatSelectsNothingRatherThanNoughtProgress() {
            AssignmentProgress progress = progressOn("2024-10-21", emptyLedger(), List.of(), STUDENT, pluralDeck());
            assertThat(progress.selectsNothing()).isTrue();
            assertThat(progress.describe()).isEqualTo("week-3 selects no cells at all");
        }
    }

    @Nested
    class MasteryShareAndDescribe {

        @Test
        void isTheShareOfTheSelectedCellsThatAreMastered() {
            assertThat(progressOn("2024-10-21", masteredLedger(3)).masteryShare())
                    .isCloseTo(0.2, byLessThan(0.005));
        }

        @Test
        void readsTheStateOut() {
            assertThat(progressOn("2024-10-21").describe())
                    .isEqualTo("week-3 is in progress, 0 of 15 cells mastered over 0 days");
        }
    }
}
