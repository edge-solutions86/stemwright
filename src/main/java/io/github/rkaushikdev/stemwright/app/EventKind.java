package io.github.rkaushikdev.stemwright.app;

import java.util.Arrays;
import java.util.Optional;

/**
 * The six kinds of {@link DomainEvent}, each with the name it is known by outside the process, such
 * as {@code lexeme.added}, and the record that carries it.
 */
public enum EventKind {
    LEXEME_ADDED("lexeme.added", DomainEvent.LexemeAdded.class),
    PROFILE_PUBLISHED("profile.published", DomainEvent.ProfilePublished.class),
    ATTEMPT_RECORDED("attempt.recorded", DomainEvent.AttemptRecorded.class),
    CELL_SUSPENDED("cell.suspended", DomainEvent.CellSuspended.class),
    SESSION_CLOSED("session.closed", DomainEvent.SessionClosed.class),
    ASSIGNMENT_MET("assignment.met", DomainEvent.AssignmentMet.class);

    private final String key;
    private final Class<? extends DomainEvent> type;

    EventKind(String key, Class<? extends DomainEvent> type) {
        this.key = key;
        this.type = type;
    }

    /** The dotted name an audit entry and a log line carry, such as {@code profile.published}. */
    public String key() {
        return key;
    }

    /** The record every event of this kind is. */
    public Class<? extends DomainEvent> type() {
        return type;
    }

    public static Optional<EventKind> fromKey(String key) {
        return Arrays.stream(values()).filter(kind -> kind.key.equals(key)).findFirst();
    }

    /**
     * The kind whose events are of the given record type.
     *
     * @throws IllegalArgumentException for a type that is not one of the six records, such as {@code
     *     DomainEvent} itself, which no event is an instance of exactly
     */
    public static EventKind of(Class<? extends DomainEvent> type) {
        return Arrays.stream(values())
                .filter(kind -> kind.type == type)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("not a kind of domain event: " + type.getName()));
    }
}
