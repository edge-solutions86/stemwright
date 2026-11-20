package io.github.rkaushikdev.stemwright.app;

import io.github.rkaushikdev.stemwright.app.DomainEvent.LexemeAdded;
import io.github.rkaushikdev.stemwright.app.DomainEvent.ProfilePublished;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import io.github.rkaushikdev.stemwright.domain.time.Instants;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * One line of the {@link AuditLog}: who did what to which thing, and when.
 *
 * @param at the instant as {@link Instants#toIsoString} stamps it, such as {@code
 *     2025-03-03T09:00:00.000Z}
 * @param on the utc study day of that instant
 * @param actorId the account responsible
 * @param action the kind of event, such as {@code lexeme.added}
 * @param subject what was acted on: a lexeme id or a profile key
 * @param detail the rest of what happened, in words
 */
public record AuditEntry(String at, LocalDate on, String actorId, String action, String subject, String detail) {

    public AuditEntry {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(on, "on");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(detail, "detail");
    }

    /**
     * The entry for an editorial event, and nothing for any other, as {@link AuditLog#isAudited}
     * decides.
     */
    public static Optional<AuditEntry> of(DomainEvent event) {
        return switch (event) {
            case LexemeAdded added -> Optional.of(stamped(added, added.lexemeId(), "added to " + added.language()));
            case ProfilePublished published ->
                Optional.of(stamped(
                        published,
                        published.profileKey(),
                        "revision " + published.revisionId() + " takes effect " + published.effectiveFrom()));
            default -> Optional.empty();
        };
    }

    private static AuditEntry stamped(DomainEvent event, String subject, String detail) {
        return new AuditEntry(
                Instants.toIsoString(event.at()),
                CivilDates.today(event.at()),
                event.actorId(),
                event.kind().key(),
                subject,
                detail);
    }

    /** Reads the entry out: {@code <at> <actor> <action> <subject>: <detail>}. */
    public String format() {
        return at + " " + actorId + " " + action + " " + subject + ": " + detail;
    }

    /**
     * The actor, refusing an entry that has none.
     *
     * @throws ValidationException when the actor id is the empty string
     */
    public String requireActor() {
        if (actorId.isEmpty()) {
            throw ValidationException.field("actorId", "is blank on an audit entry");
        }
        return actorId;
    }
}
