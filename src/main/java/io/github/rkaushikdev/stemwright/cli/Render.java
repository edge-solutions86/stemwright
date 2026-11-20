package io.github.rkaushikdev.stemwright.cli;

import io.github.rkaushikdev.stemwright.domain.text.Normalise;
import io.github.rkaushikdev.stemwright.domain.text.Numbers;
import io.github.rkaushikdev.stemwright.domain.text.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Terminal output. Everything is plain text and nothing is coloured, because the output of this cli
 * is read as often through a pipe and a log file as it is on a terminal, and escape codes in a log
 * are noise a human has to learn to ignore.
 *
 * <p>Widths are measured in code points rather than utf-16 units. A Greek word with a breathing is
 * fewer characters than its length suggests, and a column padded by the wrong count is a table that
 * stops lining up exactly where the interesting data is.
 */
public final class Render {

    private static final int DEFAULT_BAR_WIDTH = 20;
    private static final int DEFAULT_INDENT = 2;
    private static final String COLUMN_GAP = "  ";

    private Render() {}

    /** Pads on the right, so the value sits on the left. */
    public static String padTo(String value, int width) {
        return padTo(value, width, Align.LEFT);
    }

    /** Pads with spaces to the width in code points, leaving a value already that wide alone. */
    public static String padTo(String value, int width, Align align) {
        String gap = " ".repeat(Math.max(0, width - Normalise.codePointLength(value)));
        return align == Align.RIGHT ? gap + value : value + gap;
    }

    /** The widest cell of each column in code points, over rows that may be of different lengths. */
    public static List<Integer> columnWidths(List<? extends List<String>> rows) {
        List<Integer> widths = new ArrayList<>();
        for (List<String> row : rows) {
            for (int index = 0; index < row.size(); index += 1) {
                int width = Normalise.codePointLength(row.get(index));
                if (index == widths.size()) {
                    widths.add(width);
                } else {
                    widths.set(index, Math.max(widths.get(index), width));
                }
            }
        }
        return List.copyOf(widths);
    }

    /**
     * A table with a rule under the header, columns two spaces apart, and no trailing space on any
     * line. Rows shorter than the header are padded out rather than refused, because a report that
     * grew a column should still print rather than crash while somebody is looking at it; cells past
     * the last column are left out.
     */
    public static String table(List<Column> columns, List<? extends List<String>> rows) {
        List<String> headers = columns.stream().map(Column::header).toList();
        List<List<String>> filled = rows.stream()
                .map(row -> IntStream.range(0, columns.size())
                        .mapToObj(index -> index < row.size() ? row.get(index) : "")
                        .toList())
                .toList();
        List<Integer> widths =
                columnWidths(Stream.concat(Stream.of(headers), filled.stream()).toList());

        String rule = widths.stream().map("-"::repeat).collect(Collectors.joining(COLUMN_GAP));
        List<String> lines = new ArrayList<>();
        lines.add(line(columns, widths, headers));
        lines.add(rule);
        filled.forEach(cells -> lines.add(line(columns, widths, cells)));
        return String.join("\n", lines);
    }

    private static String line(List<Column> columns, List<Integer> widths, List<String> cells) {
        String joined = IntStream.range(0, cells.size())
                .mapToObj(index -> padTo(
                        cells.get(index),
                        index < widths.size() ? widths.get(index) : 0,
                        columns.get(index).align()))
                .collect(Collectors.joining(COLUMN_GAP));
        return Text.trimEnd(joined);
    }

    /** Pairs one to a line, the values lined up two spaces past the longest key. */
    public static String keyValues(List<Map.Entry<String, String>> pairs) {
        int width = pairs.stream()
                .mapToInt(pair -> Normalise.codePointLength(pair.getKey()))
                .max()
                .orElse(0);
        return pairs.stream()
                .map(pair -> padTo(pair.getKey(), width) + "  " + pair.getValue())
                .collect(Collectors.joining("\n"));
    }

    /** {@link #bar(double, int, String, String)} twenty wide, in {@code #} and {@code .}. */
    public static String bar(double share) {
        return bar(share, DEFAULT_BAR_WIDTH);
    }

    /** {@link #bar(double, int, String, String)} in {@code #} and {@code .}. */
    public static String bar(double share, int width) {
        return bar(share, width, "#", ".");
    }

    /**
     * A share as a bar, for a coverage report that is read at a glance. The share is held between
     * zero and one, and a share that is not a number is read as zero.
     */
    public static String bar(double share, int width, String filledChar, String emptyChar) {
        double clamped = Math.max(0, Math.min(1, Double.isFinite(share) ? share : 0));
        int filled = (int) Numbers.round(clamped * width);
        return filledChar.repeat(filled) + emptyChar.repeat(Math.max(0, width - filled));
    }

    /** {@link #percent(double, int)} with no decimal places. */
    public static String percent(double share) {
        return percent(share, 0);
    }

    /** A share as a percentage with the given places, such as {@code 33.3%}, and zero for a share that is not finite. */
    public static String percent(double share, int places) {
        double clamped = Double.isFinite(share) ? share : 0;
        return Numbers.toFixed(clamped * 100, places) + "%";
    }

    /** The count with the noun, the plural being the singular with an {@code s}. */
    public static String pluralise(int count, String singular) {
        return pluralise(count, singular, singular + "s");
    }

    /** The count with the singular for exactly one and the plural for anything else, zero included. */
    public static String pluralise(int count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }

    /** {@link #indent(String, int)} by two spaces. */
    public static String indent(String text) {
        return indent(text, DEFAULT_INDENT);
    }

    /** Indents every line that is not empty, so a blank line stays blank. */
    public static String indent(String text, int spaces) {
        String prefix = " ".repeat(spaces);
        return Text.split(text, "\n").stream()
                .map(line -> line.isEmpty() ? line : prefix + line)
                .collect(Collectors.joining("\n"));
    }

    /** A title over its indented body, saying so when the body is empty rather than printing nothing. */
    public static String section(String title, String body) {
        return body.isEmpty() ? title + "\n  nothing to show" : title + "\n" + indent(body);
    }

    /**
     * Cuts a value to the width in code points, marking the cut with a {@code ~} in the last place.
     * At a width of one or less there is no room for the mark, and the value is simply cut.
     */
    public static String truncate(String value, int width) {
        if (Normalise.codePointLength(value) <= width) {
            return value;
        }
        if (width <= 1) {
            return firstCodePoints(value, Math.max(0, width));
        }
        return firstCodePoints(value, width - 1) + "~";
    }

    private static String firstCodePoints(String value, int count) {
        return value.substring(0, value.offsetByCodePoints(0, count));
    }
}
