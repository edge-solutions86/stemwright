package io.github.rkaushikdev.stemwright.infra;

import io.github.rkaushikdev.stemwright.domain.errors.IntegrityException;
import io.github.rkaushikdev.stemwright.domain.text.Text;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * An append only log of everything that happened, in the order it happened. It is the only durable
 * thing in the system: every store, every ledger and every report can be rebuilt from it, and
 * nothing else can be rebuilt from them.
 *
 * <p>Entries carry a sequence number that the journal assigns, never the caller. A caller supplied
 * sequence is a caller supplied ordering, and two writers would then disagree about what happened
 * first while both believing they were right.
 *
 * <p>On disk a journal is one {@link JournalEntry#encode() encoded} entry per line, each line ended,
 * which is what {@link #encode} writes and {@link #decode} reads.
 */
public interface Journal {

    /**
     * Writes the entries in order, numbering them on from the entries already held and stamping all
     * of them with the same instant, and returns them as written. Nothing is written for an empty
     * list.
     */
    List<JournalEntry> append(List<JournalAppend> entries, Instant at);

    /** Every entry, in order. */
    default List<JournalEntry> read() {
        return read(1);
    }

    /**
     * The entries from a sequence number onwards, in order, which is empty past the end.
     *
     * @throws io.github.rkaushikdev.stemwright.domain.errors.StemwrightException for a sequence
     *     below one, which no entry has
     */
    List<JournalEntry> read(long fromSeq);

    /** How many entries the journal holds. */
    int size();

    /** The sequence number of the last entry, or zero when there is none. */
    long lastSeq();

    /** A journal held in memory and lost with the process, which is what tests and a dry run want. */
    static Journal inMemory() {
        return new MemoryJournal(List.of());
    }

    /**
     * A journal held in memory that starts with the entries given, as decoded from a file. Appends
     * number on from how many entries it holds.
     */
    static Journal inMemory(List<JournalEntry> seed) {
        return new MemoryJournal(seed);
    }

    /**
     * Reads a whole journal, skipping blank lines, and refuses one whose sequence numbers do not run
     * one, two, three from the first line. Every line is decoded before the order is checked, so a
     * torn line is reported as torn rather than as out of order.
     */
    static List<JournalEntry> decode(String text) {
        List<String> lines = Text.split(text, "\n").stream()
                .filter(line -> !Text.trim(line).isEmpty())
                .toList();
        List<JournalEntry> entries = new ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index += 1) {
            entries.add(JournalEntry.decode(lines.get(index), index + 1));
        }
        for (int index = 0; index < entries.size(); index += 1) {
            long expected = index + 1;
            long found = entries.get(index).seq();
            if (found != expected) {
                throw new IntegrityException("journal is out of order at line " + expected + ", expected sequence "
                        + expected + " and found " + found);
            }
        }
        return List.copyOf(entries);
    }

    /**
     * The entries one per line, ending with a line end so that an append starts clean, and the empty
     * string for no entries.
     */
    static String encode(List<JournalEntry> entries) {
        return entries.isEmpty()
                ? ""
                : entries.stream().map(JournalEntry::encode).collect(Collectors.joining("\n")) + "\n";
    }

    /** Entries of one kind, which is how a replay picks out what it cares about. */
    static List<JournalEntry> entriesOfKind(List<JournalEntry> entries, JournalKind kind) {
        return entries.stream().filter(entry -> entry.kind() == kind).toList();
    }
}
