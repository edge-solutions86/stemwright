package io.github.rkaushikdev.stemwright.io;

import io.github.rkaushikdev.stemwright.domain.errors.ErrorDetail;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.lexicon.Lexeme;
import io.github.rkaushikdev.stemwright.domain.text.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a lexicon import made of a file: the lexemes that parsed and the rows that did not, so the
 * caller decides whether to keep what parsed.
 *
 * @param accepted the lexemes that parsed, in file order
 * @param rejected the rows that did not, in file order
 * @param total how many rows the file had under its header
 */
public record ImportReport(List<Lexeme> accepted, List<ImportRow> rejected, int total) {

    public ImportReport {
        accepted = List.copyOf(accepted);
        rejected = List.copyOf(rejected);
    }

    /** True when every row parsed. */
    public boolean isClean() {
        return rejected.isEmpty();
    }

    /**
     * The accepted lexemes, for a caller that wants all or nothing.
     *
     * @throws ValidationException unless every row parsed, carrying each problem of each rejected row
     *     under {@code line <n>.<path>}
     */
    public List<Lexeme> requireClean() {
        if (!isClean()) {
            List<ErrorDetail> details = rejected.stream()
                    .flatMap(row -> row.problems().stream()
                            .map(problem ->
                                    new ErrorDetail("line " + row.line() + "." + problem.path(), problem.message())))
                    .toList();
            throw new ValidationException(rejected.size() + " of " + total + " rows were rejected", details);
        }
        return accepted;
    }

    /**
     * The report read out: a count of the usable rows, then one line per rejection naming its line,
     * its lemma or {@code (no lemma)}, and its problems joined with {@code "; "}.
     */
    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        lines.add(accepted.size() + " of " + total + " rows are usable");
        for (ImportRow row : rejected) {
            String summary = row.problems().stream()
                    .map(problem -> problem.path() + " " + problem.message())
                    .collect(Collectors.joining("; "));
            String lemma = row.lemma().isEmpty() ? "(no lemma)" : row.lemma();
            lines.add("line " + row.line() + " " + lemma + ": " + summary);
        }
        return List.copyOf(lines);
    }

    /**
     * The lemmas of accepted rows whose lemma, whatever its case, and homograph index collide with a
     * lexeme already stored, sorted.
     */
    public List<String> collisionsWith(List<Lexeme> existing) {
        Set<String> seen = existing.stream().map(ImportReport::collisionKey).collect(Collectors.toSet());
        return accepted.stream()
                .filter(entry -> seen.contains(collisionKey(entry)))
                .map(Lexeme::lemma)
                .sorted()
                .toList();
    }

    private static String collisionKey(Lexeme entry) {
        return Text.lower(entry.lemma()) + "/" + entry.homograph();
    }
}
