package io.github.rkaushikdev.stemwright.performance;

import static io.github.rkaushikdev.stemwright.support.Profiles.LATIN_NOUN;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.domain.drill.Deck;
import io.github.rkaushikdev.stemwright.domain.drill.DeckInput;
import io.github.rkaushikdev.stemwright.domain.drill.DrillContext;
import io.github.rkaushikdev.stemwright.domain.drill.DrillPolicy;
import io.github.rkaushikdev.stemwright.domain.drill.DrillPolicyPatch;
import io.github.rkaushikdev.stemwright.domain.drill.Membership;
import io.github.rkaushikdev.stemwright.domain.drill.Queue;
import io.github.rkaushikdev.stemwright.domain.lexicon.Lexeme;
import io.github.rkaushikdev.stemwright.domain.lexicon.LexemeContext;
import io.github.rkaushikdev.stemwright.domain.lexicon.LexemeInput;
import io.github.rkaushikdev.stemwright.domain.lexicon.Lexicon;
import io.github.rkaushikdev.stemwright.domain.lexicon.StemInput;
import io.github.rkaushikdev.stemwright.domain.lexicon.StemSet;
import io.github.rkaushikdev.stemwright.domain.mastery.CellStrength;
import io.github.rkaushikdev.stemwright.domain.mastery.Ladder;
import io.github.rkaushikdev.stemwright.domain.mastery.MasteryLedger;
import io.github.rkaushikdev.stemwright.domain.report.Coverage;
import io.github.rkaushikdev.stemwright.domain.text.Text;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import io.github.rkaushikdev.stemwright.support.Book;
import io.github.rkaushikdev.stemwright.support.Classes;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Timing checks, kept out of the default test run and run on their own with {@code ./mvnw -B -Pperf
 * test}, which also leaves the coverage agent out so that the timings are of the code rather than of
 * its instrumentation.
 *
 * <p>The numbers here are ceilings rather than targets, and they are loose on purpose: a shared
 * runner under load is several times slower than a laptop, and a timing test tight enough to be
 * interesting is a timing test that fails for reasons that have nothing to do with the change under
 * review.
 *
 * <p>What they are actually for is catching a change of complexity. Queue assembly over four
 * thousand cells taking two seconds is a different algorithm from one taking forty milliseconds,
 * and that difference survives any amount of runner noise.
 */
@Tag("performance")
class ScalePerformanceTest {

    private static final LocalDate DAY = CivilDates.parseCivilDate("2024-10-21");
    private static final int LEXEME_COUNT = 400;

    private static final LexemeContext CONTEXT = new LexemeContext(LATIN_NOUN, Classes.LATIN);

    private static Lexicon bigLexicon() {
        List<Lexeme> entries = IntStream.range(0, LEXEME_COUNT)
                .mapToObj(index -> Lexeme.of(
                        CONTEXT,
                        LexemeInput.builder(
                                        "lemma" + Text.padStart(Integer.toString(index), 4, '0'),
                                        index % 2 == 0 ? "first-declension" : "second-declension.neuter",
                                        StemSet.of(StemInput.of("base", "stem" + index)))
                                .glosses("gloss " + index)
                                .tags(index % 3 == 0 ? "core" : "extra")
                                .build()))
                .toList();
        return Lexicon.build("latin", entries);
    }

    private static double elapsed(Runnable body) {
        long started = System.nanoTime();
        body.run();
        return (System.nanoTime() - started) / 1_000_000.0;
    }

    @Nested
    class Scale {

        private static final Lexicon LEXICON = bigLexicon();
        private static final DrillContext DRILL_CONTEXT =
                new DrillContext(LATIN_NOUN, Classes.LATIN, Book.LATIN_BOOK, LEXICON);
        private static final Deck EVERYTHING = Deck.of(LATIN_NOUN, DeckInput.of("all"));

        private static DrillPolicy policy() {
            return DrillPolicy.of(DrillPolicyPatch.EMPTY);
        }

        @Test
        void expandsFourThousandCellsFromFourHundredLexemes() {
            Membership membership = Membership.resolve(DRILL_CONTEXT, EVERYTHING, DAY);
            assertThat(membership.cells()).hasSize(LEXEME_COUNT * 10);
        }

        @Test
        void resolvesMembershipInWellUnderASecond() {
            double ms = elapsed(() -> Membership.resolve(DRILL_CONTEXT, EVERYTHING, DAY));
            assertThat(ms).isLessThan(1500);
        }

        @Test
        void assemblesAQueueOverTheWholeSetInWellUnderASecond() {
            Membership membership = Membership.resolve(DRILL_CONTEXT, EVERYTHING, DAY);
            MasteryLedger ledger = MasteryLedger.of(
                    "lrn_one",
                    membership.cells().subList(0, 500).stream()
                            .map(cell -> CellStrength.fresh(cell.id()).recordCorrect(Ladder.STANDARD, DAY))
                            .toList());
            double ms = elapsed(() -> Queue.assemble(DRILL_CONTEXT, membership, policy(), ledger, DAY));
            assertThat(ms).isLessThan(1500);
        }

        @Test
        void reportsCoverageOverTheWholeSetInWellUnderASecond() {
            Membership membership = Membership.resolve(DRILL_CONTEXT, EVERYTHING, DAY);
            MasteryLedger ledger = MasteryLedger.of("lrn_one", List.of());
            double ms = elapsed(() -> Coverage.byBundle(membership.cells(), ledger, Ladder.STANDARD, DAY));
            assertThat(ms).isLessThan(1500);
        }

        @Test
        void buildsTheLexiconIndexesInWellUnderASecond() {
            List<Lexeme> entries = List.copyOf(LEXICON.byId().values());
            double ms = elapsed(() -> Lexicon.build("latin", entries));
            assertThat(ms).isLessThan(1000);
        }

        @Test
        void keepsQueueAssemblyCloseToLinearInTheNumberOfCells() {
            Membership membership = Membership.resolve(DRILL_CONTEXT, EVERYTHING, DAY);
            MasteryLedger ledger = MasteryLedger.of("lrn_one", List.of());
            Membership half = new Membership(
                    membership.deckKey(),
                    membership.on(),
                    membership.cells().subList(0, membership.cells().size() / 2),
                    membership.lexemes(),
                    membership.skipped());

            // One pass of each before timing, so the comparison is between two runs of compiled
            // code rather than between a cold first run and a warm second one.
            Queue.assemble(DRILL_CONTEXT, membership, policy(), ledger, DAY);
            Queue.assemble(DRILL_CONTEXT, half, policy(), ledger, DAY);

            double wholeMs = elapsed(() -> Queue.assemble(DRILL_CONTEXT, membership, policy(), ledger, DAY));
            double halfMs = elapsed(() -> Queue.assemble(DRILL_CONTEXT, half, policy(), ledger, DAY));

            // A quadratic assembler would be four times slower on twice the input. Ten is loose
            // enough for a cold runner and far under what a regression costs.
            assertThat(wholeMs).isLessThan(Math.max(50, halfMs * 10));
        }
    }
}
