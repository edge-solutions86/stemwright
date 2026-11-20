package io.github.rkaushikdev.stemwright.domain.report;

import io.github.rkaushikdev.stemwright.domain.mastery.CellStrength;
import io.github.rkaushikdev.stemwright.domain.mastery.Ladder;
import io.github.rkaushikdev.stemwright.domain.mastery.MasteryLedger;
import io.github.rkaushikdev.stemwright.domain.paradigm.Cell;
import io.github.rkaushikdev.stemwright.domain.text.Collation;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Coverage answers "how much of this paradigm does the learner hold", and it has to be asked in two
 * directions because they disagree. Down the bundles it says which cells of the table are weak
 * across every word. Down the lexemes it says which words are weak across the whole table.
 *
 * <p>A learner can look fine on one and terrible on the other. Somebody who knows the nominative of
 * four hundred nouns and nothing else scores well per lexeme and badly per bundle, and the second
 * reading is the one that says what to drill next.
 */
public final class Coverage {

    private static final int WEAKEST_SHOWN = 5;

    /** Shares closer than this are the same share, so the next key decides between them. */
    private static final double SAME_SHARE = 1e-9;

    private static final String TOTAL_KEY = "all";

    private Coverage() {}

    /** One row for every bundle the cells sit in, in bundle key order as a reader sorts them. */
    public static List<CoverageRow> byBundle(List<Cell> cells, MasteryLedger ledger, Ladder ladder, LocalDate on) {
        return rowsBy(cells, Cell::bundle, ledger, ladder, on);
    }

    /** One row for every lexeme the cells belong to, in lexeme id order as a reader sorts them. */
    public static List<CoverageRow> byLexeme(List<Cell> cells, MasteryLedger ledger, Ladder ladder, LocalDate on) {
        return rowsBy(cells, Cell::lexemeId, ledger, ladder, on);
    }

    private static List<CoverageRow> rowsBy(
            List<Cell> cells, Function<Cell, String> key, MasteryLedger ledger, Ladder ladder, LocalDate on) {
        Map<String, Bucket> buckets = new LinkedHashMap<>();
        for (Cell cell : cells) {
            buckets.computeIfAbsent(key.apply(cell), Bucket::new).add(ledger.strengthFor(cell.id()), ladder, on);
        }
        return buckets.values().stream()
                .map(Bucket::row)
                .sorted(Comparator.comparing(CoverageRow::key, Collation.ORDER))
                .toList();
    }

    /**
     * The rows added up under the key {@code all}. Its accuracy is the mean of the row accuracies
     * above nought, so a row nobody has answered does not drag the total down.
     */
    public static CoverageRow total(List<CoverageRow> rows) {
        int total = 0;
        int untouched = 0;
        int learning = 0;
        int mature = 0;
        int due = 0;
        int suspended = 0;
        double accuracySum = 0;
        int seenRows = 0;
        for (CoverageRow row : rows) {
            total += row.total();
            untouched += row.untouched();
            learning += row.learning();
            mature += row.mature();
            due += row.due();
            suspended += row.suspended();
            if (row.accuracy() > 0) {
                accuracySum += row.accuracy();
                seenRows += 1;
            }
        }
        double accuracy = seenRows == 0 ? 0 : accuracySum / seenRows;
        return new CoverageRow(TOTAL_KEY, total, untouched, learning, mature, due, suspended, accuracy);
    }

    /** {@link #weakestRows(List, int)} keeping the five weakest. */
    public static List<CoverageRow> weakestRows(List<CoverageRow> rows) {
        return weakestRows(rows, WEAKEST_SHOWN);
    }

    /**
     * Rows ranked by how much work they still need, heaviest first: least mature first, then least
     * accurate, then in key order as a reader sorts them.
     *
     * @param limit how many rows to keep, the weakest first; a negative limit keeps every row but
     *     that many from the strong end
     */
    public static List<CoverageRow> weakestRows(List<CoverageRow> rows, int limit) {
        return Ranking.head(rows.stream().sorted(Coverage::weakestFirst).toList(), limit);
    }

    private static int weakestFirst(CoverageRow a, CoverageRow b) {
        if (Math.abs(a.maturity() - b.maturity()) > SAME_SHARE) {
            return Double.compare(a.maturity(), b.maturity());
        }
        if (Math.abs(a.accuracy() - b.accuracy()) > SAME_SHARE) {
            return Double.compare(a.accuracy(), b.accuracy());
        }
        return Collation.compare(a.key(), b.key());
    }

    /** The running counts of one row while the cells are read. */
    private static final class Bucket {

        private final String key;
        private int total;
        private int untouched;
        private int learning;
        private int mature;
        private int due;
        private int suspended;
        private double accuracySum;
        private int accuracyCount;

        private Bucket(String key) {
            this.key = key;
        }

        private void add(CellStrength strength, Ladder ladder, LocalDate on) {
            total += 1;
            if (strength.suspended()) {
                suspended += 1;
            } else if (strength.isNew()) {
                untouched += 1;
            } else if (ladder.isAtTop(strength.rung())) {
                mature += 1;
            } else {
                learning += 1;
            }

            if (strength.isDue(on)) {
                due += 1;
            }

            if (strength.seen() > 0) {
                accuracySum += strength.accuracy();
                accuracyCount += 1;
            }
        }

        private CoverageRow row() {
            double accuracy = accuracyCount == 0 ? 0 : accuracySum / accuracyCount;
            return new CoverageRow(key, total, untouched, learning, mature, due, suspended, accuracy);
        }
    }
}
