package io.github.rkaushikdev.stemwright.domain.lexicon;

import java.util.Arrays;
import java.util.Optional;

/** What happened to a lexeme between two versions of a lexicon. */
public enum ChangeKind {
    ADDED("added"),
    REMOVED("removed"),
    CHANGED("changed");

    private final String key;

    ChangeKind(String key) {
        this.key = key;
    }

    /** The lower case name a diff is written out with, such as {@code added}. */
    public String key() {
        return key;
    }

    public static Optional<ChangeKind> fromKey(String key) {
        return Arrays.stream(values()).filter(kind -> kind.key.equals(key)).findFirst();
    }
}
