package io.github.rkaushikdev.stemwright.io;

import io.github.rkaushikdev.stemwright.domain.errors.ErrorDetail;
import io.github.rkaushikdev.stemwright.domain.errors.StemwrightException;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.lexicon.Lexeme;
import io.github.rkaushikdev.stemwright.domain.lexicon.LexemeContext;
import io.github.rkaushikdev.stemwright.domain.lexicon.LexemeInput;
import io.github.rkaushikdev.stemwright.domain.lexicon.Stem;
import io.github.rkaushikdev.stemwright.domain.lexicon.StemInput;
import io.github.rkaushikdev.stemwright.domain.lexicon.StemSet;
import io.github.rkaushikdev.stemwright.domain.text.Normalise;
import io.github.rkaushikdev.stemwright.domain.text.Numbers;
import io.github.rkaushikdev.stemwright.domain.text.Text;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Importing a lexicon from a spreadsheet, which is how every one of these arrives.
 *
 * <p>The run never stops at the first bad row. A department sends a file of four hundred nouns and
 * wants the list of what is wrong with it, not the first thing; an import that fails at row seven
 * means seven rounds of editing. So every row is tried, the failures are collected with their line
 * numbers, and the caller decides whether to keep what parsed.
 *
 * <p>A row is {@code lemma}, {@code class} and {@code stem}, which every file must have, then any of
 * {@code gloss} and {@code tags} (each a list separated by semicolons), {@code homograph}, a {@code
 * stem.<role>} column per extra stem and an {@code inherent.<dimension>} column per inherent feature.
 * An empty extra stem or inherent cell is left out rather than refused.
 */
public final class LexiconImport {

    /** The columns a file must have, whatever else it carries. */
    public static final List<String> REQUIRED_COLUMNS = List.of("lemma", "class", "stem");

    private static final String STEM_PREFIX = "stem.";
    private static final String INHERENT_PREFIX = "inherent.";
    private static final String HOMOGRAPH = "homograph";

    private LexiconImport() {}

    /**
     * Tries every row of the table as a lexeme, numbering a row by its line in the file, the header
     * being line one.
     *
     * @throws ValidationException when the table lacks a {@linkplain #REQUIRED_COLUMNS required
     *     column}, which no row can be read without
     */
    public static ImportReport importTable(LexemeContext context, CsvTable table) {
        table.requireColumns(REQUIRED_COLUMNS);

        List<Lexeme> accepted = new ArrayList<>();
        List<ImportRow> rejected = new ArrayList<>();
        List<Map<String, String>> rows = table.rows();
        for (int index = 0; index < rows.size(); index += 1) {
            Map<String, String> row = rows.get(index);
            try {
                accepted.add(lexemeFrom(context, row));
            } catch (RuntimeException error) {
                rejected.add(new ImportRow(index + 2, row.getOrDefault("lemma", ""), problemsOf(error)));
            }
        }
        return new ImportReport(accepted, rejected, rows.size());
    }

    /**
     * Reads the text as csv with a header and imports it.
     *
     * @throws ValidationException when the text is not a usable csv table or lacks a required column
     */
    public static ImportReport importCsv(LexemeContext context, String text) {
        return importTable(context, Csv.readTable(text));
    }

    /**
     * One row as a lexeme. The homograph cell is read as JavaScript's {@code Number} reads it, so
     * {@code 2.0} is two and {@code two} is NaN. A value an {@code int} cannot carry, a fraction, a
     * NaN or one past {@link Integer#MAX_VALUE}, goes in as zero, which {@link Lexeme#of} refuses at
     * the same point among its checks as any other bad homograph; the refusal is then restated for the
     * value the cell held, so the report reads {@code must be a positive whole number: 2.5}.
     */
    private static Lexeme lexemeFrom(LexemeContext context, Map<String, String> row) {
        LexemeInput.Builder input = LexemeInput.builder(
                        row.getOrDefault("lemma", ""), row.getOrDefault("class", ""), StemSet.of(stemsFrom(row)))
                .inherent(inherentFrom(row))
                .glosses(listFrom(row.get("gloss")))
                .tags(listFrom(row.get("tags")));

        String cell = row.get(HOMOGRAPH);
        if (cell == null || cell.isEmpty()) {
            return Lexeme.of(context, input.build());
        }
        double homograph = Numbers.parse(cell);
        if (Numbers.fitsInt(homograph)) {
            return Lexeme.of(context, input.homograph((int) homograph).build());
        }
        try {
            return Lexeme.of(context, input.homograph(0).build());
        } catch (ValidationException error) {
            throw error.restating(HOMOGRAPH, homographProblem(homograph));
        }
    }

    /** The base stem from the {@code stem} column, then every non blank {@code stem.<role>} column. */
    private static List<StemInput> stemsFrom(Map<String, String> row) {
        List<StemInput> stems = new ArrayList<>();
        stems.add(StemInput.of(Stem.DEFAULT_ROLE, row.getOrDefault("stem", "")));
        row.forEach((column, value) -> {
            if (column.startsWith(STEM_PREFIX)
                    && !Normalise.normaliseInput(value).isEmpty()) {
                stems.add(StemInput.of(column.substring(STEM_PREFIX.length()), value));
            }
        });
        return stems;
    }

    /** The parts of a semicolon separated cell, normalised, without the blank ones. */
    private static List<String> listFrom(String value) {
        if (value == null || Normalise.normaliseInput(value).isEmpty()) {
            return List.of();
        }
        return Text.split(value, ";").stream()
                .map(Normalise::normaliseInput)
                .filter(part -> !part.isEmpty())
                .toList();
    }

    /** Every non blank {@code inherent.<dimension>} cell, normalised, in column order. */
    private static Map<String, String> inherentFrom(Map<String, String> row) {
        Map<String, String> inherent = new LinkedHashMap<>();
        row.forEach((column, value) -> {
            String cleaned = Normalise.normaliseInput(value);
            if (column.startsWith(INHERENT_PREFIX) && !cleaned.isEmpty()) {
                inherent.put(column.substring(INHERENT_PREFIX.length()), cleaned);
            }
        });
        return inherent;
    }

    /** The details of a domain failure that has them, otherwise one problem under {@code row}. */
    private static List<ErrorDetail> problemsOf(RuntimeException error) {
        if (error instanceof StemwrightException failure && !failure.details().isEmpty()) {
            return failure.details();
        }
        return List.of(new ErrorDetail("row", String.valueOf(error.getMessage())));
    }

    /** What is wrong with a homograph cell an {@code int} cannot carry. */
    private static String homographProblem(double value) {
        if (Numbers.isInteger(value) && value > Integer.MAX_VALUE) {
            return "must be at most " + Integer.MAX_VALUE + ": " + Numbers.format(value);
        }
        return "must be a positive whole number: " + Numbers.text(value);
    }
}
