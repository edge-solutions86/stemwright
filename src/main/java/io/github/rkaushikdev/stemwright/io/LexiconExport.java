package io.github.rkaushikdev.stemwright.io;

import io.github.rkaushikdev.stemwright.domain.features.FeaturePair;
import io.github.rkaushikdev.stemwright.domain.lexicon.Lexeme;
import io.github.rkaushikdev.stemwright.domain.lexicon.Stem;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Writing a lexicon back out as the file it came in as.
 *
 * <p>The columns are derived from what the data actually uses rather than fixed, so a language with
 * four stem roles gets four stem columns and one with a single stem gets one. A fixed header would
 * either lose the extra stems or pad every export with empty columns for roles no language in the
 * file has.
 *
 * <p>Round tripping is the property that matters and it is tested: an export read back by {@link
 * LexiconImport} produces the same lexemes.
 */
public final class LexiconExport {

    /** The columns every export starts with, in this order. */
    public static final List<String> BASE_COLUMNS = List.of("lemma", "class", "stem", "gloss", "tags", "homograph");

    private static final String STEM_PREFIX = "stem.";
    private static final String INHERENT_PREFIX = "inherent.";
    private static final String LIST_SEPARATOR = "; ";

    private LexiconExport() {}

    /** A {@code stem.<role>} column for every role other than the base one that some entry uses, sorted. */
    public static List<String> stemColumnsFor(List<Lexeme> entries) {
        return entries.stream()
                .flatMap(entry -> entry.stems().roles().stream())
                .filter(role -> !role.equals(Stem.DEFAULT_ROLE))
                .distinct()
                .sorted()
                .map(role -> STEM_PREFIX + role)
                .toList();
    }

    /** An {@code inherent.<dimension>} column for every inherent dimension some entry uses, sorted. */
    public static List<String> inherentColumnsFor(List<Lexeme> entries) {
        return entries.stream()
                .flatMap(entry -> entry.inherent().pairs().stream())
                .map(FeaturePair::dimension)
                .distinct()
                .sorted()
                .map(dimension -> INHERENT_PREFIX + dimension)
                .toList();
    }

    /** The base columns, then the stem columns, then the inherent columns. */
    public static List<String> columnsFor(List<Lexeme> entries) {
        return Stream.of(BASE_COLUMNS, stemColumnsFor(entries), inherentColumnsFor(entries))
                .flatMap(List::stream)
                .toList();
    }

    /**
     * One lexeme as a row: the base fields, then a cell for every stem and inherent column given, empty
     * where the lexeme has no such stem or dimension. Glosses and tags are joined with {@code "; "}.
     *
     * @return the row keyed by column, the base columns first and then the given columns in order
     */
    public static Map<String, String> rowFor(Lexeme entry, List<String> columns) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("lemma", entry.lemma());
        row.put("class", entry.classKey());
        row.put("stem", formOf(entry, Stem.DEFAULT_ROLE));
        row.put("gloss", String.join(LIST_SEPARATOR, entry.glosses()));
        row.put("tags", String.join(LIST_SEPARATOR, entry.tags()));
        row.put("homograph", Integer.toString(entry.homograph()));

        for (String column : columns) {
            if (column.startsWith(STEM_PREFIX)) {
                row.put(column, formOf(entry, column.substring(STEM_PREFIX.length())));
            } else if (column.startsWith(INHERENT_PREFIX)) {
                row.put(
                        column,
                        entry.inherent()
                                .valueOf(column.substring(INHERENT_PREFIX.length()))
                                .orElse(""));
            }
        }
        return row;
    }

    /** The whole lexicon as csv, in word list order, with the columns {@link #columnsFor} derives. */
    public static String exportLexemes(List<Lexeme> entries) {
        List<Lexeme> sorted = entries.stream().sorted(Lexeme.ORDER).toList();
        List<String> columns = columnsFor(sorted);
        return Csv.writeTable(
                columns, sorted.stream().map(entry -> rowFor(entry, columns)).toList());
    }

    /**
     * A one line summary of a lexeme, for a log or a report rather than a file, such as {@code puella
     * first-declension (gender=fem): girl}.
     */
    public static String describeLexeme(Lexeme entry) {
        String inherent = entry.inherentKey();
        String tail = inherent.isEmpty() ? "" : " (" + inherent + ")";
        return entry.lemma() + " " + entry.classKey() + tail + ": " + String.join(LIST_SEPARATOR, entry.glosses());
    }

    /** The form of the stem with exactly this role, without the fallback to the base stem. */
    private static String formOf(Lexeme entry, String role) {
        return entry.stems().find(role).map(Stem::form).orElse("");
    }
}
