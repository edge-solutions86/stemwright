package io.github.rkaushikdev.stemwright.app;

import io.github.rkaushikdev.stemwright.domain.lexicon.Lexeme;
import java.util.List;
import java.util.Optional;

/** The lexemes of every language, keyed by lexeme id. */
public interface LexemeStore {

    Optional<Lexeme> get(String id);

    /** Every lexeme of the language, in {@link Lexeme#ORDER}: by lemma as a reader sorts it, then by homograph. */
    List<Lexeme> listByLanguage(String language);

    /** Stores the lexeme under its id, in place of any lexeme stored there before. */
    void put(Lexeme entry);

    /** Removes the lexeme with the id, doing nothing when there is none. */
    void remove(String id);
}
