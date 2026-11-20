package io.github.rkaushikdev.stemwright.domain.drill;

import java.util.Objects;

/**
 * A lexeme that contributed no cell to a deck's membership, and why.
 *
 * @param lexemeId the id of the lexeme
 * @param lemma the lemma, for a reader who cannot resolve the id
 * @param reason why it contributed nothing
 */
public record SkippedLexeme(String lexemeId, String lemma, SkipReason reason) {

    public SkippedLexeme {
        Objects.requireNonNull(lexemeId, "lexemeId");
        Objects.requireNonNull(lemma, "lemma");
        Objects.requireNonNull(reason, "reason");
    }
}
