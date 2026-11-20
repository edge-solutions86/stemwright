package io.github.rkaushikdev.stemwright.domain.drill;

import io.github.rkaushikdev.stemwright.domain.features.FeatureBundle;
import io.github.rkaushikdev.stemwright.domain.features.FeatureSchema;
import io.github.rkaushikdev.stemwright.domain.paradigm.Cell;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The spacing rule, which keeps two cells of one paradigm row from being asked back to back. It is
 * the other half of what burial does: burial hides a cell's twins, spacing keeps its neighbours at
 * a distance, and both exist so a session does not hand the learner the answer to its next
 * question.
 */
public final class RowSpacing {

    private RowSpacing() {}

    /**
     * The row a cell sits in. A row is the cell's bundle with one dimension dropped, so two cells of
     * the same lexeme differing only in number are in the same row and will not be asked back to
     * back. The key reads like {@code lex_xxxx|case=gen}.
     */
    public static String rowKeyOf(FeatureSchema schema, Cell cell, String acrossDimension) {
        FeatureBundle bundle = FeatureBundle.parseKey(schema, cell.bundle());
        String parts = bundle.pairs().stream()
                .filter(pair -> !pair.dimension().equals(acrossDimension))
                .map(pair -> pair.dimension() + "=" + pair.value())
                .collect(Collectors.joining(";"));
        return cell.lexemeId() + "|" + parts;
    }

    /** The value the row runs across, or nothing when the cell's bundle does not fix it. */
    public static Optional<String> valueInRow(FeatureSchema schema, Cell cell, String acrossDimension) {
        return FeatureBundle.parseKey(schema, cell.bundle()).valueOf(acrossDimension);
    }

    /**
     * Checks the spacing rule against the tail of what has already been asked. A gap of nought or
     * less means the rule is off, and a candidate whose row has not been seen in the last {@code gap}
     * questions always passes.
     */
    public static boolean respectsSpacing(
            FeatureSchema schema, List<Cell> asked, Cell candidate, String acrossDimension, int gap) {
        if (gap <= 0) {
            return true;
        }
        String wanted = rowKeyOf(schema, candidate, acrossDimension);
        List<Cell> window = asked.subList(Math.max(0, asked.size() - gap), asked.size());
        return window.stream()
                .noneMatch(cell -> rowKeyOf(schema, cell, acrossDimension).equals(wanted));
    }
}
