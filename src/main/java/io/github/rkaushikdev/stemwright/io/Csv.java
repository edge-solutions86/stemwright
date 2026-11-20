package io.github.rkaushikdev.stemwright.io;

import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Comma separated values, written out rather than installed, because the shape this project needs is
 * small and every csv library disagrees about the same three things: what a quote inside a quoted
 * field means, whether a trailing newline makes an empty last row, and what to do with a row of the
 * wrong width.
 *
 * <p>The answers here are the ones a spreadsheet gives. A doubled quote inside a quoted field is one
 * quote, a trailing newline is not a row, and a row of the wrong width is an error naming the line,
 * because a lexicon import that silently drops a column produces a lexeme with the wrong gloss and
 * nobody finds out until a learner is marked wrong.
 */
public final class Csv {

    private Csv() {}

    /** The field as written: quoted, with its quotes doubled, when it holds a quote, comma or line end. */
    public static String formatField(String value) {
        if (needsQuotes(value)) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static boolean needsQuotes(String value) {
        for (int index = 0; index < value.length(); index += 1) {
            char character = value.charAt(index);
            if (character == '"' || character == ',' || character == '\r' || character == '\n') {
                return true;
            }
        }
        return false;
    }

    public static String formatRow(List<String> cells) {
        return cells.stream().map(Csv::formatField).collect(Collectors.joining(","));
    }

    /** The rows one per line, each line ended, and the empty string for no rows. */
    public static String formatCsv(List<? extends List<String>> rows) {
        return rows.isEmpty() ? "" : rows.stream().map(Csv::formatRow).collect(Collectors.joining("\n")) + "\n";
    }

    /**
     * Reads every row. Carriage returns are read as line ends first, so a file saved on any platform
     * reads the same, and a line end inside a quoted field stays in the field as {@code \n}.
     *
     * @throws ValidationException for a quote that opens in the middle of a field, naming its line,
     *     and for a file that ends inside a quoted field
     */
    public static List<List<String>> parseCsv(String text) {
        ParseState state = new ParseState();
        String normalised = text.replace("\r\n", "\n").replace('\r', '\n');

        for (int index = 0; index < normalised.length(); index += 1) {
            char character = normalised.charAt(index);

            if (state.inQuotes) {
                if (character == '"') {
                    if (index + 1 < normalised.length() && normalised.charAt(index + 1) == '"') {
                        state.field.append('"');
                        index += 1;
                    } else {
                        state.inQuotes = false;
                    }
                    continue;
                }
                state.field.append(character);
                continue;
            }

            if (character == '"') {
                if (!state.field.isEmpty()) {
                    throw new ValidationException(
                            "a quote opens in the middle of a field on line " + (state.rows.size() + 1));
                }
                state.inQuotes = true;
                state.sawQuote = true;
                continue;
            }
            if (character == ',') {
                state.endField();
                continue;
            }
            if (character == '\n') {
                state.endField();
                state.endRow();
                continue;
            }
            state.field.append(character);
        }

        if (state.inQuotes) {
            throw new ValidationException("the file ends inside a quoted field");
        }
        if (!state.field.isEmpty() || !state.row.isEmpty() || state.sawQuote) {
            state.endField();
            state.endRow();
        }
        return Collections.unmodifiableList(state.rows);
    }

    /**
     * Reads a file with a header row into named records. Every row has to be the width of the header,
     * and a row that is not names its line number.
     */
    public static CsvTable readTable(String text) {
        List<List<String>> grid = parseCsv(text);
        if (grid.isEmpty()) {
            throw new ValidationException("the file has no header row");
        }
        List<String> headers = grid.get(0);
        if (new HashSet<>(headers).size() != headers.size()) {
            throw new ValidationException("the header row names a column twice");
        }

        List<Map<String, String>> rows = new ArrayList<>();
        for (int index = 1; index < grid.size(); index += 1) {
            List<String> cells = grid.get(index);
            if (cells.size() != headers.size()) {
                throw new ValidationException("line " + (index + 1) + " has " + cells.size()
                        + " fields where the header has " + headers.size());
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int column = 0; column < headers.size(); column += 1) {
                row.put(headers.get(column), cells.get(column));
            }
            rows.add(row);
        }
        return new CsvTable(headers, rows);
    }

    /** Writes the header and then each row in header order, with an empty cell for a missing column. */
    public static String writeTable(List<String> headers, List<? extends Map<String, String>> rows) {
        List<List<String>> grid = Stream.concat(
                        Stream.of(headers), rows.stream().map(row -> headers.stream()
                                .map(header -> row.getOrDefault(header, ""))
                                .toList()))
                .toList();
        return formatCsv(grid);
    }

    /** The parser's place in the text: the rows so far, the row and field being read, the quoting. */
    private static final class ParseState {

        private final List<List<String>> rows = new ArrayList<>();
        private List<String> row = new ArrayList<>();
        private final StringBuilder field = new StringBuilder();
        private boolean inQuotes;
        private boolean sawQuote;

        private void endField() {
            row.add(field.toString());
            field.setLength(0);
            sawQuote = false;
        }

        private void endRow() {
            rows.add(Collections.unmodifiableList(row));
            row = new ArrayList<>();
        }
    }
}
