package io.github.rkaushikdev.stemwright.domain.report;

import static io.github.rkaushikdev.stemwright.support.Drill.DRILL_CONTEXT;
import static io.github.rkaushikdev.stemwright.support.Drill.PUELLA;
import static io.github.rkaushikdev.stemwright.support.Profiles.LATIN_NOUN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.stemwright.domain.drill.Deck;
import io.github.rkaushikdev.stemwright.domain.drill.DeckInput;
import io.github.rkaushikdev.stemwright.domain.drill.Membership;
import io.github.rkaushikdev.stemwright.domain.mastery.CellStrength;
import io.github.rkaushikdev.stemwright.domain.mastery.Ladder;
import io.github.rkaushikdev.stemwright.domain.mastery.LadderInput;
import io.github.rkaushikdev.stemwright.domain.mastery.MasteryLedger;
import io.github.rkaushikdev.stemwright.domain.paradigm.Cell;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CoverageTest {

    private static final LocalDate TERM = d("2024-10-01");
    private static final Ladder FLAT =
            Ladder.of(LadderInput.builder("flat", 1, 3).spread(0).build());

    private static final Membership MEMBERSHIP =
            Membership.resolve(DRILL_CONTEXT, Deck.of(LATIN_NOUN, DeckInput.of("all")), TERM);
    private static final List<Cell> CELLS = MEMBERSHIP.cells();

    private static final MasteryLedger LEDGER = MasteryLedger.of(
            "lrn_one",
            List.of(
                    mature(cellOf("case=nom;number=sg").id()),
                    learning(cellOf("case=gen;number=sg").id()),
                    CellStrength.fresh(cellOf("case=dat;number=sg").id())
                            .recordLapse(FLAT, TERM)
                            .suspend()));

    private static LocalDate d(String value) {
        return CivilDates.parseCivilDate(value);
    }

    private static Cell cellOf(String bundle) {
        return CELLS.stream()
                .filter(cell ->
                        cell.lexemeId().equals(PUELLA.id()) && cell.bundle().equals(bundle))
                .findFirst()
                .orElseThrow();
    }

    private static CellStrength mature(String cellId) {
        return CellStrength.fresh(cellId).recordCorrect(FLAT, d("2024-09-01")).recordCorrect(FLAT, d("2024-09-02"));
    }

    private static CellStrength learning(String cellId) {
        return CellStrength.fresh(cellId).recordCorrect(FLAT, d("2024-09-30"));
    }

    private static CoverageRow rowFor(List<CoverageRow> rows, String key) {
        return rows.stream().filter(row -> row.key().equals(key)).findFirst().orElseThrow();
    }

    private static List<String> keysOf(List<CoverageRow> rows) {
        return rows.stream().map(CoverageRow::key).toList();
    }

    @Nested
    class ByBundle {

        private final List<CoverageRow> rows = Coverage.byBundle(CELLS, LEDGER, FLAT, TERM);

        @Test
        void hasOneRowPerBundle() {
            assertThat(rows).hasSize(10);
        }

        @Test
        void countsThreeLexemesInEveryBundle() {
            assertThat(rows.stream().allMatch(row -> row.total() == 3)).isTrue();
        }

        @Test
        void splitsABundleIntoItsBuckets() {
            CoverageRow nomSg = rowFor(rows, "case=nom;number=sg");
            assertThat(nomSg.mature()).isEqualTo(1);
            assertThat(nomSg.untouched()).isEqualTo(2);
            assertThat(nomSg.suspended()).isEqualTo(0);
        }

        @Test
        void countsASuspendedCellAsSuspendedAndNothingElse() {
            CoverageRow datSg = rowFor(rows, "case=dat;number=sg");
            assertThat(datSg.suspended()).isEqualTo(1);
            assertThat(datSg.untouched()).isEqualTo(2);
            assertThat(datSg.learning()).isEqualTo(0);
        }

        @Test
        void countsADueCell() {
            CoverageRow genSg = rowFor(rows, "case=gen;number=sg");
            assertThat(genSg.learning()).isEqualTo(1);
            assertThat(genSg.due()).isEqualTo(1);
        }

        @Test
        void averagesAccuracyOverTheCellsThatWereSeen() {
            assertThat(rowFor(rows, "case=nom;number=sg").accuracy()).isEqualTo(1.0);
            assertThat(rowFor(rows, "case=acc;number=pl").accuracy()).isEqualTo(0.0);
        }
    }

    @Nested
    class ByLexeme {

        private final List<CoverageRow> rows = Coverage.byLexeme(CELLS, LEDGER, FLAT, TERM);

        @Test
        void hasOneRowPerLexeme() {
            assertThat(rows).hasSize(3);
        }

        @Test
        void countsTenCellsForEach() {
            assertThat(rows.stream().allMatch(row -> row.total() == 10)).isTrue();
        }

        @Test
        void putsTheAnsweredCellsUnderTheRightLexeme() {
            CoverageRow row = rowFor(rows, PUELLA.id());
            assertThat(row.mature()).isEqualTo(1);
            assertThat(row.learning()).isEqualTo(1);
            assertThat(row.suspended()).isEqualTo(1);
            assertThat(row.untouched()).isEqualTo(7);
        }
    }

    @Nested
    class Total {

        @Test
        void addsTheBucketsAcrossEveryRow() {
            CoverageRow total = Coverage.total(Coverage.byBundle(CELLS, LEDGER, FLAT, TERM));
            assertThat(total.total()).isEqualTo(30);
            assertThat(total.mature()).isEqualTo(1);
            assertThat(total.learning()).isEqualTo(1);
            assertThat(total.suspended()).isEqualTo(1);
            assertThat(total.untouched()).isEqualTo(27);
        }

        @Test
        void reportsZerosForNoRowsAtAll() {
            assertThat(Coverage.total(List.of())).isEqualTo(new CoverageRow("all", 0, 0, 0, 0, 0, 0, 0));
        }
    }

    @Nested
    class MaturityAndTouchedShare {

        private final CoverageRow row = rowFor(Coverage.byLexeme(CELLS, LEDGER, FLAT, TERM), PUELLA.id());

        @Test
        void readsMaturityAsAShareOfTheRow() {
            assertThat(row.maturity()).isCloseTo(0.1, byLessThan(0.005));
        }

        @Test
        void readsHowMuchOfTheRowHasBeenMetAtAll() {
            assertThat(row.touchedShare()).isCloseTo(0.3, byLessThan(0.005));
        }

        @Test
        void isNoughtForAnEmptyRow() {
            CoverageRow empty =
                    new CoverageRow(row.key(), 0, 0, row.learning(), 0, row.due(), row.suspended(), row.accuracy());
            assertThat(empty.maturity()).isEqualTo(0.0);
            assertThat(empty.touchedShare()).isEqualTo(0.0);
        }
    }

    @Nested
    class WeakestRows {

        private final List<CoverageRow> rows = Coverage.byBundle(CELLS, LEDGER, FLAT, TERM);

        @Test
        void putsTheLeastMatureFirst() {
            List<CoverageRow> weakest = Coverage.weakestRows(rows, 3);
            assertThat(weakest.stream().allMatch(row -> row.mature() == 0)).isTrue();
        }

        @Test
        void takesOnlyAsManyAsItWasAskedFor() {
            assertThat(Coverage.weakestRows(rows, 2)).hasSize(2);
            assertThat(Coverage.weakestRows(rows, 99)).hasSize(10);
        }

        @Test
        void leavesTheMatureRowOutOfTheWeakestFew() {
            assertThat(keysOf(Coverage.weakestRows(rows, 3))).doesNotContain("case=nom;number=sg");
        }

        @Test
        void returnsNothingForNoRows() {
            assertThat(Coverage.weakestRows(List.of(), 5)).isEmpty();
        }

        @Test
        void isStableForAFreshLearnerFallingBackToTheKey() {
            List<CoverageRow> fresh = Coverage.byBundle(CELLS, MasteryLedger.empty("lrn_two"), FLAT, TERM);
            assertThat(keysOf(Coverage.weakestRows(fresh, 2)))
                    .isEqualTo(keysOf(fresh).stream().sorted().limit(2).toList());
        }
    }
}
