package io.github.rkaushikdev.stemwright.app;

import io.github.rkaushikdev.stemwright.domain.time.DayRange;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Who changed what, kept separately from the journal.
 *
 * <p>The journal answers "what is the state" and this answers "who is responsible for it", and
 * those are different questions with different readers and different retention. An institution
 * deletes learner answers at the end of a degree and keeps the record of who edited a paradigm for
 * as long as anybody might ask why a mark was what it was.
 *
 * <p>Only editorial events are recorded. An attempt is a learner doing what they were asked, and
 * putting it here would bury the four entries a term that anybody actually reads.
 *
 * <p>Every method holds the log's lock, so a log attached to a bus with {@link #auditFrom} records
 * events published from several request threads at once.
 */
public final class AuditLog {

    private static final Set<EventKind> AUDITED = EnumSet.of(EventKind.LEXEME_ADDED, EventKind.PROFILE_PUBLISHED);

    private final List<AuditEntry> entries;

    private AuditLog(List<AuditEntry> seed) {
        this.entries = new ArrayList<>(seed);
    }

    /** An empty log. */
    public static AuditLog create() {
        return create(List.of());
    }

    /** A log that starts with the entries given, as read back from wherever they were kept. */
    public static AuditLog create(List<AuditEntry> seed) {
        return new AuditLog(seed);
    }

    /** Whether the log records events of this one's kind: editorial events, and nothing else. */
    public static boolean isAudited(DomainEvent event) {
        return AUDITED.contains(event.kind());
    }

    /**
     * Attaches the log to a bus, so it records every audited event published there, and hands back
     * the way to detach it.
     */
    public static Subscription auditFrom(EventBus bus, AuditLog log) {
        return bus.onAny(log::record);
    }

    /** Every entry, in the order it was recorded. */
    public synchronized List<AuditEntry> entries() {
        return List.copyOf(entries);
    }

    /** Records the event when it is audited, and hands back the entry it recorded, if any. */
    public synchronized Optional<AuditEntry> record(DomainEvent event) {
        Optional<AuditEntry> entry = AuditEntry.of(event);
        entry.ifPresent(entries::add);
        return entry;
    }

    public List<AuditEntry> byActor(String actorId) {
        return matching(entry -> entry.actorId().equals(actorId));
    }

    public List<AuditEntry> bySubject(String subject) {
        return matching(entry -> entry.subject().equals(subject));
    }

    /** The entries whose study day falls inside the range, either end included. */
    public List<AuditEntry> inRange(DayRange range) {
        return matching(entry -> range.contains(entry.on()));
    }

    /** Forgets every entry. */
    public synchronized void clear() {
        entries.clear();
    }

    private synchronized List<AuditEntry> matching(Predicate<AuditEntry> wanted) {
        return entries.stream().filter(wanted).toList();
    }
}
