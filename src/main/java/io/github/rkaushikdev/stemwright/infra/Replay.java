package io.github.rkaushikdev.stemwright.infra;

import io.github.rkaushikdev.stemwright.app.Stores;
import io.github.rkaushikdev.stemwright.domain.errors.IntegrityException;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.infra.json.JsonObject;
import java.util.Comparator;
import java.util.List;

/**
 * Walking a journal back into a set of stores, the other half of {@link JournalledStores}: one
 * writes every change down before applying it, and this applies what was written down.
 *
 * <p>Like the wrapper, a replay handles the records with a codec, attempts and ledgers. Every other
 * kind the journal declares is counted and passed over.
 */
public final class Replay {

    private static final Comparator<JournalEntry> BY_SEQ = Comparator.comparingLong(JournalEntry::seq);

    private Replay() {}

    /**
     * Applies the entries in sequence order, whatever order they were handed over in. An entry of a
     * kind this build does not replay is counted and skipped rather than refused, because a journal
     * written by a newer version has to be readable by an older one for long enough to roll back. An
     * entry that is malformed is a different thing and stops the replay.
     *
     * @throws ValidationException when an entry's payload is not a usable record, naming it under
     *     {@code journal.<seq>}; the entries before it have been applied
     */
    public static ReplayReport apply(List<JournalEntry> entries, Stores stores) {
        int applied = 0;
        int skipped = 0;
        long lastSeq = 0;

        for (JournalEntry entry : entries.stream().sorted(BY_SEQ).toList()) {
            lastSeq = entry.seq();
            String path = "journal." + entry.seq();
            switch (entry.kind()) {
                case ATTEMPT_APPENDED -> {
                    stores.attempts().append(List.of(Records.decodeAttempt(entry.payload(), path)));
                    applied += 1;
                }
                case LEDGER_PUT -> {
                    stores.ledgers().put(Records.decodeLedger(entry.payload(), path));
                    applied += 1;
                }
                default -> skipped += 1;
            }
        }

        return new ReplayReport(applied, skipped, lastSeq);
    }

    /** {@link #apply(List, Stores)} over every entry the journal holds. */
    public static ReplayReport apply(Journal journal, Stores stores) {
        return apply(journal.read(), stores);
    }

    /**
     * Reads a journal without applying it, one line per entry, which is what an operator wants when
     * a replay has failed and the question is which entry broke it: {@code 3
     * 2025-03-03T09:00:00.000Z attempt cell_aaaaaa by lrn_one}, {@code 4 2025-03-03T09:00:00.000Z
     * ledger for lrn_one}, and the kind alone for anything else.
     *
     * @throws ValidationException when an attempt or ledger entry lacks the fields its line names
     */
    public static List<String> inspect(List<JournalEntry> entries) {
        return entries.stream().map(Replay::describe).toList();
    }

    /**
     * Checks that the entries are numbered one, two, three from the first, with no gap and no repeat.
     *
     * @throws IntegrityException naming the first position whose sequence number is not its own
     */
    public static void assertContiguous(List<JournalEntry> entries) {
        for (int index = 0; index < entries.size(); index += 1) {
            long seq = entries.get(index).seq();
            if (seq != index + 1) {
                throw new IntegrityException(
                        "journal is not contiguous at position " + (index + 1) + ", found sequence " + seq);
            }
        }
    }

    private static String describe(JournalEntry entry) {
        String head = entry.seq() + " " + entry.at() + " ";
        return switch (entry.kind()) {
            case ATTEMPT_APPENDED -> {
                JsonObject row = entry.payloadObject();
                yield head + "attempt " + Codec.readString(row, "payload", "cellId") + " by "
                        + Codec.readString(row, "payload", "learnerId");
            }
            case LEDGER_PUT -> head + "ledger for " + Codec.readString(entry.payloadObject(), "payload", "learnerId");
            default -> head + entry.kind().key();
        };
    }
}
