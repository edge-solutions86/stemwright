package io.github.rkaushikdev.stemwright.domain.report;

import static io.github.rkaushikdev.stemwright.support.Drill.AGER;
import static io.github.rkaushikdev.stemwright.support.Drill.BELLUM;
import static io.github.rkaushikdev.stemwright.support.Drill.DRILL_CONTEXT;
import static io.github.rkaushikdev.stemwright.support.Drill.PUELLA;
import static io.github.rkaushikdev.stemwright.support.Drill.REX;
import static io.github.rkaushikdev.stemwright.support.Profiles.LATIN_NOUN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.stemwright.domain.drill.Deck;
import io.github.rkaushikdev.stemwright.domain.drill.DeckInput;
import io.github.rkaushikdev.stemwright.domain.drill.DeckScopeInput;
import io.github.rkaushikdev.stemwright.domain.drill.Membership;
import io.github.rkaushikdev.stemwright.domain.lexicon.Lexeme;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DeckStatsTest {

    private static final LocalDate DAY = CivilDates.parseCivilDate("2024-10-21");
    private static final List<Lexeme> LEXEMES = List.of(PUELLA, BELLUM, AGER, REX);

    /** A deck's statistics together with the membership they were counted from. */
    private record Counted(DeckStats stats, Membership membership) {}

    private static Counted statsFor(DeckInput input) {
        Deck built = Deck.of(LATIN_NOUN, input);
        Membership membership = Membership.resolve(DRILL_CONTEXT, built, DAY);
        return new Counted(DeckStats.of(built, membership, LEXEMES), membership);
    }

    private static Counted all() {
        return statsFor(DeckInput.of("all"));
    }

    private static Counted none() {
        return statsFor(DeckInput.builder("none")
                .scope(DeckScopeInput.builder().cells("case=nom,case!=nom").build())
                .build());
    }

    /**
     * The statistics with the bundle counts replaced, as an editor would see a lopsided deck. The
     * order of the counts is immaterial: every reading of them ranks or totals them.
     */
    private static DeckStats skewed(DeckStats stats, int lexemes, Map<String, Integer> perBundle) {
        return new DeckStats(
                stats.deckKey(),
                lexemes,
                stats.cells(),
                stats.bundles(),
                stats.cellsPerLexeme(),
                stats.skipped(),
                stats.classes(),
                perBundle);
    }

    private static DeckStats skewed(DeckStats stats, Map<String, Integer> perBundle) {
        return skewed(stats, stats.lexemes(), perBundle);
    }

    @Nested
    class Of {

        @Test
        void countsLexemesCellsAndBundles() {
            DeckStats stats = all().stats();
            assertThat(stats.lexemes()).isEqualTo(3);
            assertThat(stats.cells()).isEqualTo(30);
            assertThat(stats.bundles()).isEqualTo(10);
            assertThat(stats.cellsPerLexeme()).isEqualTo(10.0);
        }

        @Test
        void countsTheLexemesOfEachClass() {
            DeckStats stats = all().stats();
            assertThat(stats.classes()).containsEntry("first-declension", 1);
            assertThat(stats.classes()).containsEntry("second-declension.neuter", 1);
        }

        @Test
        void countsTheCellsOfEachBundle() {
            assertThat(all().stats().perBundle()).containsEntry("case=gen;number=sg", 3);
        }

        @Test
        void countsWhatWasSkippedForAReasonWorthReporting() {
            assertThat(all().stats().skipped()).isEqualTo(1);
        }

        @Test
        void reportsZerosForADeckThatSelectsNothing() {
            DeckStats stats = none().stats();
            assertThat(stats.cells()).isEqualTo(0);
            assertThat(stats.cellsPerLexeme()).isEqualTo(0.0);
            assertThat(stats.bundles()).isEqualTo(0);
        }
    }

    @Nested
    class BundleImbalance {

        @Test
        void isOneForAPerfectlyEvenDeck() {
            assertThat(all().stats().bundleImbalance()).isCloseTo(1.0, byLessThan(0.005));
        }

        @Test
        void isZeroForADeckWithNoCells() {
            assertThat(none().stats().bundleImbalance()).isEqualTo(0.0);
        }

        @Test
        void risesWhenOneBundleDominates() {
            DeckStats stats = skewed(all().stats(), Map.of("a", 90, "b", 5, "c", 5));
            assertThat(stats.bundleImbalance()).isGreaterThan(2);
        }
    }

    @Nested
    class BusiestAndThinnest {

        private final DeckStats skewed = skewed(all().stats(), Map.of("a", 9, "b", 5, "c", 1));

        @Test
        void ranksByCount() {
            assertThat(skewed.busiestBundles(2))
                    .containsExactly(new DeckStats.BundleCount("a", 9), new DeckStats.BundleCount("b", 5));
            assertThat(skewed.thinnestBundles(2))
                    .containsExactly(new DeckStats.BundleCount("c", 1), new DeckStats.BundleCount("b", 5));
        }

        @Test
        void honoursTheLimit() {
            assertThat(skewed.busiestBundles(1)).hasSize(1);
        }
    }

    @Nested
    class Warnings {

        @Test
        void warnsAboutADeckWithNoCellsAndStopsThere() {
            Counted counted = none();
            assertThat(counted.stats().warnings(counted.membership()))
                    .containsExactly("the deck holds no cells at all");
        }

        @Test
        void warnsAboutASmallDeck() {
            Counted counted = all();
            assertThat(counted.stats().warnings(counted.membership())).contains("the deck holds only 3 lexemes");
        }

        @Test
        void warnsAboutALexemeWithNoParadigmBound() {
            Counted counted = all();
            assertThat(counted.stats().warnings(counted.membership()))
                    .contains("1 lexemes were skipped for having no paradigm bound");
        }

        @Test
        void warnsAboutAnImbalancedDeck() {
            Counted counted = all();
            DeckStats stats = skewed(
                    counted.stats(),
                    40,
                    Map.of("case=nom;number=sg", 90, "case=gen;number=sg", 5, "case=dat;number=sg", 5));
            List<String> warnings = stats.warnings(counted.membership());
            assertThat(warnings).anyMatch(line -> line.contains("above the average"));
        }
    }

    @Nested
    class Describe {

        @Test
        void readsTheCountsOut() {
            assertThat(all().stats().describe()).isEqualTo("all holds 30 cells across 3 lexemes and 10 bundles");
        }
    }
}
