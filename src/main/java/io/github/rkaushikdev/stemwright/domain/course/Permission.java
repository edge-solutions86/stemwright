package io.github.rkaushikdev.stemwright.domain.course;

import java.util.Arrays;
import java.util.Optional;

/**
 * Something an actor may be allowed to do. Five of them are about one course and are only granted
 * against a {@link CourseScope}; three are not about a course at all.
 *
 * <p>The declaration order is the order {@link Actor#permissions} lists them in.
 */
public enum Permission {
    /** Answer the drills of a course. Course scoped. */
    DRILL("drill"),
    /** Read one's own progress on a course. Course scoped. */
    READ_OWN_PROGRESS("read-own-progress"),
    /** Read the progress of everybody on a course. Course scoped. */
    READ_COHORT("read-cohort"),
    /** Create, rescope and remove the decks of a course. Course scoped. */
    EDIT_DECK("edit-deck"),
    /** Change a course, its roster and its assignments. Course scoped. */
    EDIT_COURSE("edit-course"),
    /** Change the lexicon of a language. Not course scoped. */
    EDIT_LEXICON("edit-lexicon"),
    /** Publish a paradigm profile. Not course scoped. */
    PUBLISH_PROFILE("publish-profile"),
    /** Create and change accounts. Not course scoped, and an admin's alone. */
    MANAGE_ACCOUNTS("manage-accounts");

    private final String key;

    Permission(String key) {
        this.key = key;
    }

    /** The name a refusal message and a permission listing print, such as {@code read-cohort}. */
    public String key() {
        return key;
    }

    public static Optional<Permission> fromKey(String key) {
        return Arrays.stream(values())
                .filter(permission -> permission.key.equals(key))
                .findFirst();
    }
}
