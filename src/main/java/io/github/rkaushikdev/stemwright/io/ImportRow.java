package io.github.rkaushikdev.stemwright.io;

import io.github.rkaushikdev.stemwright.domain.errors.ErrorDetail;
import java.util.List;
import java.util.Objects;

/**
 * One row of a lexicon import that was refused, with what was wrong with it.
 *
 * @param line the line of the file the row is on, counting the header as line one
 * @param lemma the lemma cell as written, empty when the row has none
 * @param problems everything the row was refused for, at least one
 */
public record ImportRow(int line, String lemma, List<ErrorDetail> problems) {

    public ImportRow {
        Objects.requireNonNull(lemma, "lemma");
        problems = List.copyOf(problems);
    }
}
