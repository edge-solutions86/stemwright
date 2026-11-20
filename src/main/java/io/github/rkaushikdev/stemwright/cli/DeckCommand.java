package io.github.rkaushikdev.stemwright.cli;

import io.github.rkaushikdev.stemwright.app.Application;
import io.github.rkaushikdev.stemwright.app.LanguageBundle;
import io.github.rkaushikdev.stemwright.app.Loaders;
import io.github.rkaushikdev.stemwright.domain.Grouping;
import io.github.rkaushikdev.stemwright.domain.drill.Deck;
import io.github.rkaushikdev.stemwright.domain.drill.DeckScopeInput;
import io.github.rkaushikdev.stemwright.domain.drill.DrillContext;
import io.github.rkaushikdev.stemwright.domain.drill.Membership;
import io.github.rkaushikdev.stemwright.domain.lexicon.Lexeme;
import io.github.rkaushikdev.stemwright.domain.report.DeckStats;
import io.github.rkaushikdev.stemwright.domain.report.DeckStats.BundleCount;
import io.github.rkaushikdev.stemwright.domain.text.Collation;
import io.github.rkaushikdev.stemwright.domain.text.Numbers;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Looking at a deck before a class is given it.
 *
 * <p>The number worth reading is the imbalance. A deck definition that reads perfectly well can
 * still come out ninety percent nominative once the class tree it names has been expanded, and
 * nobody notices until a term of learners has been drilled on one row of the table. The stored
 * definition tells you nothing about that, because the cells only exist after a day has been picked
 * and the profiles in force on it have been resolved.
 *
 * <p>{@code --cells} narrows the stored deck without saving, so an editor can ask what a selector
 * would do before committing to it.
 */
public final class DeckCommand {

    private static final int SPREAD_DEFAULT = 3;

    /** Larger counts first, ties in key order as a reader sorts them. */
    private static final Comparator<Map.Entry<String, Integer>> LARGEST_FIRST =
            Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                    .reversed()
                    .thenComparing(Map.Entry::getKey, Collation.ORDER);

    private DeckCommand() {}

    /**
     * One line of the spread table: a bundle, the cells it holds, and the end of the deck it was
     * listed at.
     *
     * @param bundle the bundle key
     * @param cells how many cells of the deck sit in it
     * @param end {@code busiest} or {@code thinnest}
     */
    public record SpreadRow(String bundle, int cells, String end) {

        public SpreadRow {
            Objects.requireNonNull(bundle, "bundle");
            Objects.requireNonNull(end, "end");
        }
    }

    /**
     * The busiest and the thinnest bundles, with the overlap removed. A deck with four bundles asked
     * for three at each end would otherwise list most of them twice, once under each heading, which
     * reads as though the deck were larger than it is.
     *
     * @return the busiest bundles, busiest first, then the thinnest not already listed, thinnest
     *     last
     */
    public static List<SpreadRow> spreadRows(DeckStats stats, int limit) {
        List<BundleCount> busiest = stats.busiestBundles(limit);
        Set<String> taken = busiest.stream().map(BundleCount::bundle).collect(Collectors.toSet());
        List<BundleCount> thinnest = stats.thinnestBundles(limit).stream()
                .filter(count -> !taken.contains(count.bundle()))
                .toList();

        return Stream.concat(
                        busiest.stream().map(count -> new SpreadRow(count.bundle(), count.cells(), "busiest")),
                        thinnest.reversed().stream()
                                .map(count -> new SpreadRow(count.bundle(), count.cells(), "thinnest")))
                .toList();
    }

    /**
     * {@code deck}: what one deck holds on a day, where it is unbalanced, which lexemes it left out
     * and why, and what an editor should look at.
     */
    public static Command of(Application app) {
        return Command.of(
                "deck",
                "show what a deck holds and where it is unbalanced",
                "stemwright deck --language latin --deck all",
                FlagSpecs.of(
                        Map.entry("language", Flags.LANGUAGE),
                        Map.entry("deck", Flags.DECK),
                        Map.entry("on", Flags.DAY),
                        Map.entry("cells", FlagSpec.of(FlagKind.STRING, "narrow to a cell selector without saving it")),
                        Map.entry("spread", FlagSpec.of(FlagKind.NUMBER, "how many bundles to list at each end"))),
                (args, context) -> describe(app, args));
    }

    /**
     * {@code decks}: every deck of a language with the size it resolves to on a day. This is the
     * listing an operator reaches for first, and it costs a membership resolution per deck, which is
     * why it prints the day it used at the top rather than leaving the reader to assume.
     */
    public static Command decks(Application app) {
        return Command.of(
                "decks",
                "list the decks of a language with the size each one resolves to",
                "stemwright decks --language latin",
                FlagSpecs.of(Map.entry("language", Flags.LANGUAGE), Map.entry("on", Flags.DAY)),
                (args, context) -> list(app, args));
    }

    private static String describe(Application app, ParsedArgs args) {
        String language = args.requireFlag("language");
        LanguageBundle bundle = Loaders.requireLanguage(app.stores(), language);
        LocalDate on = Flags.dayFrom(app.clock(), args);

        Deck stored = app.decks().get(language, args.requireFlag("deck"));
        Deck deck = args.stringFlag("cells")
                .map(narrow -> stored.withScope(
                        bundle.schema(), DeckScopeInput.builder().cells(narrow).build()))
                .orElse(stored);

        DrillContext drill = app.drill().context(language);
        Membership membership = Membership.resolve(drill, deck, on);
        DeckStats stats = DeckStats.of(deck, membership, drill.lexicon().allLexemes());
        int limit = Flags.countFrom(args, "spread", SPREAD_DEFAULT);

        String summary = Render.keyValues(List.of(
                Map.entry("scope", deck.scope().describe()),
                Map.entry("on", on.toString()),
                Map.entry("lexemes", String.valueOf(stats.lexemes())),
                Map.entry("cells", String.valueOf(stats.cells())),
                Map.entry("bundles", String.valueOf(stats.bundles())),
                Map.entry("cells per lexeme", Numbers.toFixed(stats.cellsPerLexeme(), 1)),
                Map.entry("imbalance", Numbers.toFixed(stats.bundleImbalance(), 2))));

        String spread = Render.table(
                List.of(Column.of("bundle"), Column.of("cells", Align.RIGHT), Column.of("end")),
                spreadRows(stats, limit).stream()
                        .map(row -> List.of(row.bundle(), String.valueOf(row.cells()), row.end()))
                        .toList());
        String classes = Render.table(
                List.of(Column.of("class"), Column.of("lexemes", Align.RIGHT)), largestFirst(stats.classes()));
        // Why lexemes fell out, counted by reason. Out of scope is counted here even though the
        // warnings leave it out, because an editor checking a narrow deck wants to see that the
        // selector did the excluding rather than a missing profile.
        List<List<String>> skipped = largestFirst(
                Grouping.countBy(membership.skipped(), entry -> entry.reason().key()));

        return String.join(
                "\n",
                stats.describe(),
                "",
                summary,
                "",
                Render.section("Bundles at each end", spread),
                "",
                Render.section("Lexemes by class", classes),
                "",
                Render.section(
                        "Lexemes left out",
                        skipped.isEmpty()
                                ? ""
                                : Render.table(
                                        List.of(Column.of("reason"), Column.of("lexemes", Align.RIGHT)), skipped)),
                "",
                Render.section("Warnings", String.join("\n", stats.warnings(membership))));
    }

    private static String list(Application app, ParsedArgs args) {
        String language = args.requireFlag("language");
        Loaders.requireLanguage(app.stores(), language);
        LocalDate on = Flags.dayFrom(app.clock(), args);

        DrillContext drill = app.drill().context(language);
        List<Lexeme> lexemes = drill.lexicon().allLexemes();
        List<Deck> decks = app.decks().list(language);

        List<List<String>> rows = decks.stream()
                .map(deck -> {
                    DeckStats stats = DeckStats.of(deck, Membership.resolve(drill, deck, on), lexemes);
                    return List.of(
                            deck.key(),
                            String.valueOf(stats.lexemes()),
                            String.valueOf(stats.cells()),
                            Numbers.toFixed(stats.bundleImbalance(), 2),
                            deck.scope().describe());
                })
                .sorted(Comparator.comparing((List<String> row) -> row.getFirst(), Collation.ORDER))
                .toList();

        return String.join(
                "\n",
                decks.size() + " decks in " + language + " on " + on,
                "",
                Render.table(
                        List.of(
                                Column.of("deck"),
                                Column.of("lexemes", Align.RIGHT),
                                Column.of("cells", Align.RIGHT),
                                Column.of("imbalance", Align.RIGHT),
                                Column.of("scope")),
                        rows));
    }

    /** Each key with its count, the largest count first and ties in key order as a reader sorts them. */
    private static List<List<String>> largestFirst(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .sorted(LARGEST_FIRST)
                .map(entry -> List.of(entry.getKey(), String.valueOf(entry.getValue())))
                .toList();
    }
}
