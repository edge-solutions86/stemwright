package io.github.rkaushikdev.stemwright.domain.course;

import java.util.Arrays;
import java.util.Optional;

/**
 * The role an account holds everywhere, as opposed to its relationship to one course. It is fixed
 * for the account and it is the first of the two things {@link Actor#can} checks.
 */
public enum AccountRole {
    /** Drills on the courses it is enrolled on and reads its own work. */
    LEARNER("learner"),
    /** Teaches the courses that list it, and edits the lexicon and its profiles. */
    INSTRUCTOR("instructor"),
    /** May do anything anywhere, including manage accounts. */
    ADMIN("admin");

    private final String key;

    AccountRole(String key) {
        this.key = key;
    }

    /** The lower case name an account directory and a token carry, such as {@code instructor}. */
    public String key() {
        return key;
    }

    public static Optional<AccountRole> fromKey(String key) {
        return Arrays.stream(values()).filter(role -> role.key.equals(key)).findFirst();
    }

    /** True for exactly the keys of the three roles, compared as written. */
    public static boolean isAccountRole(String value) {
        return fromKey(value).isPresent();
    }
}
