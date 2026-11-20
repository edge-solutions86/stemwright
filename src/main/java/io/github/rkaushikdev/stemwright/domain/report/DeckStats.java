package io.github.rkaushikdev.stemwright.domain.report;

import io.github.rkaushikdev.stemwright.domain.Grouping;
import io.github.rkaushikdev.stemwright.domain.drill.Deck;
import io.github.rkaushikdev.stemwright.domain.drill.Membership;
import io.github.rkaushikdev.stemwright.domain.drill.SkipReason;
import io.github.rkaushikdev.stemwright.domain.lexicon.Lexeme;
import io.github.rkaushikdev.stemwright.domain.paradigm.Cell;
import io.github.rkaushikdev.stemwright.domain.text.Collation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IntSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What a deck actually contains, for the editor who wrote it rather than the learner who drills it.
 *
 * <p>The number that matters is the balance across bundles. A deck of four hundred nouns whose cells
 * are ninety percent nominative teaches the nominative, and the deck definition that produced it
 * usually looks fine: a selector that narrows by class picks up whatever cells those classes happen
 * to define, and an unbalanced class tree quietly becomes an unbalanced deck.
 *
 * @param deckKey the key of the deck
 * @param lexemes how many lexemes contributed a cell
 * @param cells how many cells the deck takes
 * @param bundles how many different bundles those cells sit in
 * @param cellsPerLexeme the mean number of cells a lexeme contributed, nought for no lexemes
 * @param skipped how many lexemes were skipped for a reason other than being out of scope
 * @param classes how many contributing lexemes each class has, in the order the classes first appear
 * @param perBundle how many cells each bundle holds, in the order the bundles first appear
 */
public record DeckStats(
        String deckKey,
        int lexemes,
        int cells,
        int bundles,
        double cellsPerLexeme,
        int skipped,
        Map<String, Integer> classes,
        Map<String, Integer> perBundle) {

    private static final int BUNDLES_SHOWN = 5;
    private static final int FEW_LEXEMES = 5;
    private static final double UNEVEN = 2;

    private static final Comparator<BundleCount> BUSIEST_FIRST =
            Comparator.comparingInt(BundleCount::cells).reversed().thenComparing(BundleCount::bundle, Collation.ORDER);

    private static final Comparator<BundleCount> THINNEST_FIRST =
            Comparator.comparingInt(BundleCount::cells).thenComparing(BundleCount::bundle, Collation.ORDER);

    public DeckStats {
        Objects.requireNonNull(deckKey, "deckKey");
        classes = Collections.unmodifiableMap(new LinkedHashMap<>(classes));
        perBundle = Collections.unmodifiableMap(new LinkedHashMap<>(perBundle));
    }

    /**
     * Counts what the membership holds. Each contributing lexeme is counted under the class of its
     * entry in {@code lexemes} when it has one there, a later entry winning a repeated id, and under
     * its own class otherwise.
     */
    public static DeckStats of(Deck entry, Membership membership, List<Lexeme> lexemes) {
        Map<String, Lexeme> byLexeme = Grouping.indexBy(lexemes, Lexeme::id);
        Map<String, Integer> classes = new LinkedHashMap<>();
        for (Lexeme row : membership.lexemes()) {
            Lexeme known = byLexeme.getOrDefault(row.id(), row);
            classes.merge(known.classKey(), 1, Integer::sum);
        }

        Map<String, Integer> perBundle = Grouping.countBy(membership.cells(), Cell::bundle);
        int lexemeCount = membership.lexemes().size();
        int cellCount = membership.cells().size();

        return new DeckStats(
                entry.key(),
                lexemeCount,
                cellCount,
                perBundle.size(),
                lexemeCount == 0 ? 0 : (double) cellCount / lexemeCount,
                (int) membership.skipped().stream()
                        .filter(row -> row.reason() != SkipReason.OUT_OF_SCOPE)
                        .count(),
                classes,
                perBundle);
    }

    /**
     * How far the busiest bundle is above the mean, as a multiple. One means the deck is perfectly
     * even and anything above two is worth a look; a deck with no bundles is nought.
     */
    public double bundleImbalance() {
        if (perBundle.isEmpty()) {
            return 0;
        }
        IntSummaryStatistics counts =
                perBundle.values().stream().mapToInt(Integer::intValue).summaryStatistics();
        double mean = counts.getAverage();
        return mean == 0 ? 0 : counts.getMax() / mean;
    }

    /** {@link #busiestBundles(int)} keeping five. */
    public List<BundleCount> busiestBundles() {
        return busiestBundles(BUNDLES_SHOWN);
    }

    /**
     * The bundles holding the most cells, ties in bundle key order as a reader sorts them.
     *
     * @param limit how many to keep; a negative limit keeps every bundle but that many from the
     *     thin end
     */
    public List<BundleCount> busiestBundles(int limit) {
        return Ranking.head(bundleCounts().stream().sorted(BUSIEST_FIRST).toList(), limit);
    }

    /** {@link #thinnestBundles(int)} keeping five. */
    public List<BundleCount> thinnestBundles() {
        return thinnestBundles(BUNDLES_SHOWN);
    }

    /**
     * The bundles holding the fewest cells, ties in bundle key order as a reader sorts them.
     *
     * @param limit how many to keep; a negative limit keeps every bundle but that many from the
     *     busy end
     */
    public List<BundleCount> thinnestBundles(int limit) {
        return Ranking.head(bundleCounts().stream().sorted(THINNEST_FIRST).toList(), limit);
    }

    private List<BundleCount> bundleCounts() {
        return perBundle.entrySet().stream()
                .map(entry -> new BundleCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * What an editor should look at. A deck with no cells gets that one warning and nothing else;
     * otherwise a deck of few lexemes, an uneven deck and lexemes skipped for want of a paradigm are
     * each warned about, in that order.
     *
     * @param membership the membership these statistics were counted from
     */
    public List<String> warnings(Membership membership) {
        if (cells == 0) {
            return List.of("the deck holds no cells at all");
        }
        List<String> warnings = new ArrayList<>();
        if (lexemes < FEW_LEXEMES) {
            warnings.add("the deck holds only " + lexemes + " lexemes");
        }
        if (bundleImbalance() > UNEVEN) {
            BundleCount busiest = busiestBundles(1).getFirst();
            warnings.add(busiest.bundle() + " holds " + busiest.cells() + " cells, well above the average");
        }
        int unbound = membership.skippedFor(SkipReason.UNBOUND_CLASS).size();
        if (unbound > 0) {
            warnings.add(unbound + " lexemes were skipped for having no paradigm bound");
        }
        return List.copyOf(warnings);
    }

    /** The counts read out, such as {@code all holds 30 cells across 3 lexemes and 10 bundles}. */
    public String describe() {
        return deckKey + " holds " + cells + " cells across " + lexemes + " lexemes and " + bundles + " bundles";
    }

    /**
     * One bundle and how many cells of the deck sit in it.
     *
     * @param bundle the bundle key
     * @param cells how many cells of the deck sit in it
     */
    public record BundleCount(String bundle, int cells) {

        public BundleCount {
            Objects.requireNonNull(bundle, "bundle");
        }
    }
}
