package io.github.rkaushikdev.stemwright.domain.drill;

import static io.github.rkaushikdev.stemwright.support.Drill.DRILL_CONTEXT;
import static io.github.rkaushikdev.stemwright.support.Drill.PUELLA;
import static io.github.rkaushikdev.stemwright.support.Profiles.LATIN_NOUN;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.domain.paradigm.Cell;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BurialSetTest {

    private static final LocalDate TERM = d("2024-10-01");
    private static final LocalDate LATER = d("2025-03-01");

    private static final Membership MEMBERSHIP =
            Membership.resolve(DRILL_CONTEXT, Deck.of(LATIN_NOUN, DeckInput.of("all")), TERM);

    private static final Cell GEN_SG = cellOf(PUELLA.id(), "case=gen;number=sg");
    private static final Cell DAT_SG = cellOf(PUELLA.id(), "case=dat;number=sg");
    private static final Cell ACC_SG = cellOf(PUELLA.id(), "case=acc;number=sg");

    private static LocalDate d(String value) {
        return CivilDates.parseCivilDate(value);
    }

    private static Cell cellOf(String lexemeId, String bundle) {
        return MEMBERSHIP.cells().stream()
                .filter(cell ->
                        cell.lexemeId().equals(lexemeId) && cell.bundle().equals(bundle))
                .findFirst()
                .orElseThrow();
    }

    /** The first cell in the bundle that is not puella's. */
    private static Cell otherThanPuella(String bundle) {
        return MEMBERSHIP.cells().stream()
                .filter(cell -> cell.bundle().equals(bundle) && !cell.lexemeId().equals(PUELLA.id()))
                .findFirst()
                .orElseThrow();
    }

    private static BurialSet buried(Cell cell) {
        return BurialSet.NONE.bury(DRILL_CONTEXT, cell, TERM);
    }

    @Nested
    class TwinCellsOf {

        @Test
        void namesTheCellsTheSyncretismMapTiesToThisOne() {
            List<String> twins = BurialSet.twinCellsOf(DRILL_CONTEXT, GEN_SG, TERM);
            assertThat(twins).contains(Cell.idFor(PUELLA.id(), "case=dat;number=sg"));
            assertThat(twins).contains(Cell.idFor(PUELLA.id(), "case=nom;number=pl"));
            assertThat(twins).hasSize(2);
        }

        @Test
        void neverNamesTheCellItself() {
            assertThat(BurialSet.twinCellsOf(DRILL_CONTEXT, GEN_SG, TERM)).doesNotContain(GEN_SG.id());
        }

        @Test
        void givesNothingForACellInNoGroup() {
            assertThat(BurialSet.twinCellsOf(DRILL_CONTEXT, ACC_SG, TERM)).isEmpty();
        }

        @Test
        void givesNothingBeforeTheProfileWasPublished() {
            assertThat(BurialSet.twinCellsOf(DRILL_CONTEXT, GEN_SG, d("2024-01-01")))
                    .isEmpty();
        }

        @Test
        void readsADifferentSetOnceALaterRevisionIsInForce() {
            Cell bellumNomSg = otherThanPuella("case=nom;number=sg");
            assertThat(BurialSet.twinCellsOf(DRILL_CONTEXT, bellumNomSg, TERM)).isEmpty();
            assertThat(BurialSet.twinCellsOf(DRILL_CONTEXT, bellumNomSg, LATER)).hasSize(1);
        }
    }

    @Nested
    class Bury {

        @Test
        void buriesTheCellAndItsTwins() {
            BurialSet set = buried(GEN_SG);
            assertThat(set.isBuried(GEN_SG.id())).isTrue();
            assertThat(set.isBuried(DAT_SG.id())).isTrue();
            assertThat(set.size()).isEqualTo(3);
        }

        @Test
        void buriesOnlyTheCellItselfWhenItHasNoTwins() {
            assertThat(buried(ACC_SG).size()).isEqualTo(1);
        }

        @Test
        void accumulatesAcrossSeveralAnswers() {
            BurialSet once = buried(GEN_SG);
            BurialSet twice = once.bury(DRILL_CONTEXT, ACC_SG, TERM);
            assertThat(twice.size()).isEqualTo(4);
        }

        @Test
        void leavesThePreviousSetAlone() {
            buried(GEN_SG);
            assertThat(BurialSet.NONE.size()).isEqualTo(0);
        }

        @Test
        void unburiesOneCellAndToleratesOneThatWasNeverBuried() {
            BurialSet set = buried(GEN_SG);
            assertThat(set.unbury(DAT_SG.id()).size()).isEqualTo(2);
            assertThat(set.unbury("cell_zzzzzz")).isSameAs(set);
        }
    }

    @Nested
    class BlockedAndSurviving {

        private final BurialSet set = buried(GEN_SG);
        private final List<Cell> candidates = List.of(GEN_SG, DAT_SG, ACC_SG);

        @Test
        void splitsTheCandidatesIntoBlockedAndSurviving() {
            assertThat(set.blocked(candidates).stream().map(Cell::bundle).toList())
                    .containsExactly("case=gen;number=sg", "case=dat;number=sg");
            assertThat(set.surviving(candidates).stream().map(Cell::bundle).toList())
                    .containsExactly("case=acc;number=sg");
        }

        @Test
        void letsEverythingThroughWhenNothingIsBuried() {
            assertThat(BurialSet.NONE.surviving(candidates)).hasSize(3);
        }
    }

    @Nested
    class RowKeyOf {

        @Test
        void dropsTheDimensionTheRowRunsAcross() {
            assertThat(RowSpacing.rowKeyOf(LATIN_NOUN, GEN_SG, "number")).isEqualTo(PUELLA.id() + "|case=gen");
        }

        @Test
        void putsTwoCellsOfOneRowUnderTheSameKey() {
            Cell genPl = cellOf(PUELLA.id(), "case=gen;number=pl");
            assertThat(RowSpacing.rowKeyOf(LATIN_NOUN, GEN_SG, "number"))
                    .isEqualTo(RowSpacing.rowKeyOf(LATIN_NOUN, genPl, "number"));
        }

        @Test
        void keepsTwoLexemesApartEvenInTheSameRow() {
            Cell otherGenSg = otherThanPuella("case=gen;number=sg");
            assertThat(RowSpacing.rowKeyOf(LATIN_NOUN, GEN_SG, "number"))
                    .isNotEqualTo(RowSpacing.rowKeyOf(LATIN_NOUN, otherGenSg, "number"));
        }

        @Test
        void readsTheValueTheRowRunsAcross() {
            assertThat(RowSpacing.valueInRow(LATIN_NOUN, GEN_SG, "number")).contains("sg");
            assertThat(RowSpacing.valueInRow(LATIN_NOUN, GEN_SG, "gender")).isEmpty();
        }
    }

    @Nested
    class RespectsSpacing {

        private final Cell genPl = cellOf(PUELLA.id(), "case=gen;number=pl");

        @Test
        void isAlwaysTrueWhenTheGapIsZero() {
            assertThat(RowSpacing.respectsSpacing(LATIN_NOUN, List.of(GEN_SG), genPl, "number", 0))
                    .isTrue();
        }

        @Test
        void refusesACellWhoseRowIsInsideTheWindow() {
            assertThat(RowSpacing.respectsSpacing(LATIN_NOUN, List.of(GEN_SG), genPl, "number", 2))
                    .isFalse();
        }

        @Test
        void allowsItOnceTheRowHasSlidOutOfTheWindow() {
            List<Cell> asked = List.of(GEN_SG, ACC_SG, DAT_SG);
            assertThat(RowSpacing.respectsSpacing(LATIN_NOUN, asked, genPl, "number", 2))
                    .isTrue();
        }

        @Test
        void allowsARowThatHasNotBeenAskedAtAll() {
            assertThat(RowSpacing.respectsSpacing(LATIN_NOUN, List.of(ACC_SG), genPl, "number", 5))
                    .isTrue();
        }

        @Test
        void allowsAnythingWhenNothingHasBeenAskedYet() {
            assertThat(RowSpacing.respectsSpacing(LATIN_NOUN, List.of(), genPl, "number", 5))
                    .isTrue();
        }
    }
}
