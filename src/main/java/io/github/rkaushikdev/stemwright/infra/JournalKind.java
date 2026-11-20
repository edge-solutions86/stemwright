package io.github.rkaushikdev.stemwright.infra;

import java.util.Arrays;
import java.util.Optional;

/**
 * What a journal entry records. The key is what the journal file stores, so a key is never renamed
 * and never reused for a new meaning.
 */
public enum JournalKind {
    LEXEME_PUT("lexeme.put"),
    LEXEME_REMOVED("lexeme.removed"),
    BOOK_PUT("book.put"),
    DECK_PUT("deck.put"),
    DECK_REMOVED("deck.removed"),
    ATTEMPT_APPENDED("attempt.appended"),
    LEDGER_PUT("ledger.put"),
    SESSION_PUT("session.put"),
    COURSE_PUT("course.put"),
    ENROLMENT_PUT("enrolment.put"),
    ASSIGNMENT_PUT("assignment.put");

    private final String key;

    JournalKind(String key) {
        this.key = key;
    }

    /** The stored form, such as {@code lexeme.put}. */
    public String key() {
        return key;
    }

    /** The kind a stored key names, or nothing for a key this build does not declare. */
    public static Optional<JournalKind> fromKey(String key) {
        return Arrays.stream(values()).filter(kind -> kind.key.equals(key)).findFirst();
    }
}
