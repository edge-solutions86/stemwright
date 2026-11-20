package io.github.rkaushikdev.stemwright.domain.drill;

import java.util.Arrays;
import java.util.Optional;

/** Why a lexeme of the language contributed no cell to a deck's membership. */
public enum SkipReason {
    /** The deck's scope leaves the lexeme out, which is the normal case. */
    OUT_OF_SCOPE("out-of-scope"),
    /** No class up the lexeme's chain is bound to a profile. */
    UNBOUND_CLASS("unbound-class"),
    /** The lexeme's profile has no revision in force on the day. */
    PROFILE_NOT_PUBLISHED("profile-not-published"),
    /** The lexeme is in scope, but none of its cells matches the deck's cell selector. */
    NO_CELL_IN_SCOPE("no-cell-in-scope");

    private final String key;

    SkipReason(String key) {
        this.key = key;
    }

    /** The reason as it is printed and stored, such as {@code unbound-class}. */
    public String key() {
        return key;
    }

    public static Optional<SkipReason> fromKey(String key) {
        return Arrays.stream(values()).filter(reason -> reason.key.equals(key)).findFirst();
    }
}
