package io.github.rkaushikdev.stemwright.cli;

import static io.github.rkaushikdev.stemwright.cli.Program.EXIT_FAILED;
import static io.github.rkaushikdev.stemwright.cli.Program.EXIT_MISUSED;
import static io.github.rkaushikdev.stemwright.cli.Program.EXIT_OK;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.app.Application;
import io.github.rkaushikdev.stemwright.app.ApplicationOptions;
import io.github.rkaushikdev.stemwright.app.Stores;
import io.github.rkaushikdev.stemwright.cli.DeckCommand.SpreadRow;
import io.github.rkaushikdev.stemwright.domain.drill.Deck;
import io.github.rkaushikdev.stemwright.domain.drill.DeckInput;
import io.github.rkaushikdev.stemwright.domain.drill.DeckScopeInput;
import io.github.rkaushikdev.stemwright.domain.drill.DrillContext;
import io.github.rkaushikdev.stemwright.domain.drill.Membership;
import io.github.rkaushikdev.stemwright.domain.report.DeckStats;
import io.github.rkaushikdev.stemwright.domain.text.Text;
import io.github.rkaushikdev.stemwright.support.App;
import io.github.rkaushikdev.stemwright.support.Drill;
import io.github.rkaushikdev.stemwright.support.Profiles;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DeckCommandTest {

    private static final CommandContext CONTEXT = new CommandContext(line -> {}, App.NOW);
    private static final Pattern SPACES = Pattern.compile(" +");
    private static final Pattern COLUMN_GAP = Pattern.compile(" {2,}");
    private static final Pattern CELLS_HELD = Pattern.compile("holds (\\d+) cells");

    /** The application the commands run over, and a program holding {@code deck} and {@code decks}. */
    private record Built(Application app, Program program) {}

    private static Built build() {
        Stores stores = App.seededStores();
        stores.decks().put(Deck.of(Profiles.LATIN_NOUN, DeckInput.of("all")));
        stores.decks()
                .put(Deck.of(
                        Profiles.LATIN_NOUN,
                        DeckInput.builder("singulars")
                                .scope(DeckScopeInput.builder()
                                        .cells("number=sg")
                                        .build())
                                .build()));
        stores.decks()
                .put(Deck.of(
                        Profiles.LATIN_NOUN,
                        DeckInput.builder("one")
                                .scope(DeckScopeInput.builder()
                                        .lexemeIds(Drill.PUELLA.id())
                                        .build())
                                .build()));
        App.Harness harness = App.testHarness();
        Application app = Application.create(stores, ApplicationOptions.of(harness.bus(), harness.clock()));
        Program program = new Program("stemwright", "1.0.0", List.of(DeckCommand.of(app), DeckCommand.decks(app)));
        return new Built(app, program);
    }

    private static RunResult invoke(String... argv) {
        return build().program().run(List.of(argv), CONTEXT);
    }

    private static List<String> lines(String output) {
        return Text.split(output, "\n");
    }

    @Nested
    class TheDeckCommand {

        @Test
        void opensWithWhatTheDeckHolds() {
            RunResult result = invoke("deck", "--language", "latin", "--deck", "all");
            assertThat(result.code()).isEqualTo(EXIT_OK);
            assertThat(lines(result.output()).getFirst()).containsPattern("^all holds \\d+ cells across \\d+ lexemes");
        }

        @Test
        void readsTheScopeOfADeckThatConstrainsNothing() {
            RunResult result = invoke("deck", "--language", "latin", "--deck", "all");
            assertThat(result.output()).contains("everything in the language");
        }

        @Test
        void readsTheScopeOfADeckThatNamesCells() {
            RunResult result = invoke("deck", "--language", "latin", "--deck", "singulars");
            assertThat(result.output()).contains("cells where number=sg");
        }

        @Test
        void reportsTheDayItResolvedAgainst() {
            RunResult result = invoke("deck", "--language", "latin", "--deck", "all");
            assertThat(result.output()).contains("2024-10-21");
        }

        @Test
        void takesTheDayItIsGivenInsteadOfTheClock() {
            RunResult result = invoke("deck", "--language", "latin", "--deck", "all", "--on", "2019-01-01");
            assertThat(result.output()).contains("2019-01-01");
            assertThat(result.output()).contains("the deck holds no cells at all");
        }

        @Test
        void listsTheBundlesAtEachEndWithoutNamingOneTwice() {
            RunResult result = invoke("deck", "--language", "latin", "--deck", "all");
            assertThat(result.output()).contains("Bundles at each end");
            assertThat(result.output()).contains("busiest");

            List<String> listed = lines(result.output()).stream()
                    .filter(line -> line.contains("busiest") || line.contains("thinnest"))
                    .map(line -> SPACES.split(Text.trim(line), -1)[0])
                    .toList();
            assertThat(listed).doesNotHaveDuplicates();
        }

        @Test
        void honoursTheSpreadFlag() {
            RunResult narrow = invoke("deck", "--language", "latin", "--deck", "all", "--spread", "1");
            RunResult wide = invoke("deck", "--language", "latin", "--deck", "all", "--spread", "4");
            assertThat(busiestLines(narrow.output())).isEqualTo(1);
            assertThat(busiestLines(wide.output())).isGreaterThan(busiestLines(narrow.output()));
        }

        private static long busiestLines(String output) {
            return lines(output).stream()
                    .filter(line -> line.contains("busiest"))
                    .count();
        }

        @Test
        void breaksTheLexemesDownByClass() {
            RunResult result = invoke("deck", "--language", "latin", "--deck", "all");
            assertThat(result.output()).contains("Lexemes by class");
            assertThat(result.output()).containsPattern("first-declension +1");
        }

        @Test
        void countsTheLexemesItLeftOutByReason() {
            RunResult result = invoke("deck", "--language", "latin", "--deck", "singulars");
            assertThat(result.output()).contains("Lexemes left out");
        }

        @Test
        void countsWhatTheScopeExcludedApartFromWhatWasBroken() {
            RunResult result = invoke("deck", "--language", "latin", "--deck", "one");
            String section = result.output().substring(result.output().indexOf("Lexemes left out"));
            assertThat(section).containsPattern("out-of-scope +3");
            assertThat(section).doesNotContain("unbound-class");
        }

        @Test
        void narrowsWithoutSavingWhenCellsAreGiven() {
            Built built = build();
            RunResult result = built.program()
                    .run(List.of("deck", "--language", "latin", "--deck", "all", "--cells", "number=pl"), CONTEXT);
            assertThat(result.output()).contains("cells where number=pl");

            Deck stored = built.app().decks().get("latin", "all");
            assertThat(stored.scope().cells().clauses()).isEmpty();
        }

        @Test
        void shrinksTheCellCountWhenTheNarrowingBites() {
            RunResult whole = invoke("deck", "--language", "latin", "--deck", "all");
            RunResult half = invoke("deck", "--language", "latin", "--deck", "all", "--cells", "number=sg");
            assertThat(cellsIn(half.output())).isGreaterThan(0);
            assertThat(cellsIn(half.output())).isLessThan(cellsIn(whole.output()));
        }

        private static int cellsIn(String output) {
            Matcher held = CELLS_HELD.matcher(output);
            return held.find() ? Integer.parseInt(held.group(1)) : 0;
        }

        @Test
        void refusesACallWithNoDeck() {
            RunResult result = invoke("deck", "--language", "latin");
            assertThat(result.code()).isEqualTo(EXIT_MISUSED);
            assertThat(result.output()).contains("deck is required");
        }

        @Test
        void refusesASelectorThatIsNotASelector() {
            RunResult result = invoke("deck", "--language", "latin", "--deck", "all", "--cells", "number");
            assertThat(result.code()).isEqualTo(EXIT_FAILED);
        }

        @Test
        void failsForADeckNobodyStored() {
            RunResult result = invoke("deck", "--language", "latin", "--deck", "nowhere");
            assertThat(result.code()).isEqualTo(EXIT_FAILED);
            assertThat(result.output()).contains("was not found");
        }

        @Test
        void failsForALanguageNobodyConfigured() {
            RunResult result = invoke("deck", "--language", "greek", "--deck", "all");
            assertThat(result.code()).isEqualTo(EXIT_FAILED);
        }
    }

    @Nested
    class TheDecksCommand {

        @Test
        void countsTheDecksItFound() {
            RunResult result = invoke("decks", "--language", "latin");
            assertThat(result.code()).isEqualTo(EXIT_OK);
            assertThat(result.output()).contains("3 decks in latin on 2024-10-21");
        }

        @Test
        void listsThemInKeyOrderWithTheirScopes() {
            RunResult result = invoke("decks", "--language", "latin");
            List<String> keys = lines(result.output()).stream()
                    .map(line -> COLUMN_GAP.split(line, -1)[0])
                    .filter(key -> key.equals("all") || key.equals("one") || key.equals("singulars"))
                    .toList();
            assertThat(keys).containsExactly("all", "one", "singulars");
            assertThat(result.output()).contains("everything in the language");
        }

        @Test
        void givesEachDeckTheSizeItResolvesTo() {
            Application app = build().app();
            DrillContext drillContext = app.drill().context("latin");
            Deck entry = app.decks().get("latin", "singulars");
            int expected = DeckStats.of(entry, Membership.resolve(drillContext, entry, App.TODAY), List.of())
                    .cells();

            RunResult result = invoke("decks", "--language", "latin");
            Optional<String> row = lines(result.output()).stream()
                    .filter(line -> line.startsWith("singulars"))
                    .findFirst();
            assertThat(row).hasValueSatisfying(line -> assertThat(line).contains(String.valueOf(expected)));
        }

        @Test
        void failsForALanguageNobodyConfigured() {
            RunResult result = invoke("decks", "--language", "greek");
            assertThat(result.code()).isEqualTo(EXIT_FAILED);
        }
    }

    @Nested
    class SpreadRows {

        private static DeckStats statsOf(Map<String, Integer> perBundle) {
            int cells = perBundle.values().stream().mapToInt(Integer::intValue).sum();
            return new DeckStats("k", 1, cells, perBundle.size(), 1, 0, Map.of(), perBundle);
        }

        @Test
        void neverListsABundleAtBothEnds() {
            List<SpreadRow> rows = DeckCommand.spreadRows(statsOf(Map.of("a", 3, "b", 2, "c", 1)), 3);
            assertThat(rows.stream().map(SpreadRow::bundle).toList()).containsExactly("a", "b", "c");
        }

        @Test
        void putsTheLargestFirstAndTheSmallestLast() {
            List<SpreadRow> rows = DeckCommand.spreadRows(statsOf(Map.of("a", 9, "b", 5, "c", 4, "d", 3, "e", 1)), 2);
            assertThat(rows.getFirst()).isEqualTo(new SpreadRow("a", 9, "busiest"));
            assertThat(rows.getLast()).isEqualTo(new SpreadRow("e", 1, "thinnest"));
        }

        @Test
        void givesNothingBackForADeckWithNoCells() {
            assertThat(DeckCommand.spreadRows(statsOf(Map.of()), 3)).isEmpty();
        }
    }
}
