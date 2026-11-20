package io.github.rkaushikdev.stemwright.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * The kinds of record that carry an id, each with the prefix its ids start with.
 *
 * <p>The prefix is load bearing: a journal entry names the kind nowhere else, and the http layer
 * refuses a {@code deck_} where a {@code lex_} belongs without reaching the store.
 */
public enum IdKind {
    LEXEME("lexeme", "lex"),
    PROFILE("profile", "prof"),
    REVISION("revision", "rev"),
    DECK("deck", "deck"),
    CELL("cell", "cell"),
    ATTEMPT("attempt", "att"),
    SESSION("session", "sess"),
    COURSE("course", "crs"),
    ASSIGNMENT("assignment", "asg"),
    LEARNER("learner", "lrn");

    private final String key;
    private final String prefix;

    IdKind(String key, String prefix) {
        this.key = key;
        this.prefix = prefix;
    }

    /** The lower case name used in messages and field paths, such as {@code lexeme}. */
    public String key() {
        return key;
    }

    /** What an id of this kind starts with, before the underscore. */
    public String prefix() {
        return prefix;
    }

    public static Optional<IdKind> withPrefix(String prefix) {
        return Arrays.stream(values())
                .filter(kind -> kind.prefix.equals(prefix))
                .findFirst();
    }
}
