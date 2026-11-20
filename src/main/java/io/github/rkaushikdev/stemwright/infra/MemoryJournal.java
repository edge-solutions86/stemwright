package io.github.rkaushikdev.stemwright.infra;

import io.github.rkaushikdev.stemwright.domain.errors.ConflictException;
import io.github.rkaushikdev.stemwright.domain.time.Instants;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The journal {@link Journal#inMemory} returns. Every method holds the journal's lock, so two
 * request threads appending at once still get distinct, consecutive sequence numbers.
 */
final class MemoryJournal implements Journal {

    private final List<JournalEntry> entries;

    MemoryJournal(List<JournalEntry> seed) {
        this.entries = new ArrayList<>(seed);
    }

    @Override
    public synchronized List<JournalEntry> append(List<JournalAppend> incoming, Instant at) {
        if (incoming.isEmpty()) {
            return List.of();
        }
        String stamp = Instants.toIsoString(at);
        List<JournalEntry> written = new ArrayList<>(incoming.size());
        for (int index = 0; index < incoming.size(); index += 1) {
            JournalAppend entry = incoming.get(index);
            written.add(new JournalEntry(entries.size() + index + 1L, stamp, entry.kind(), entry.payload()));
        }
        entries.addAll(written);
        return List.copyOf(written);
    }

    /** Refuses a sequence below one as a {@link ConflictException}. */
    @Override
    public synchronized List<JournalEntry> read(long fromSeq) {
        if (fromSeq < 1) {
            throw new ConflictException("a journal read starts at sequence one, not " + fromSeq);
        }
        return entries.stream().filter(entry -> entry.seq() >= fromSeq).toList();
    }

    @Override
    public synchronized int size() {
        return entries.size();
    }

    @Override
    public synchronized long lastSeq() {
        return entries.isEmpty() ? 0 : entries.get(entries.size() - 1).seq();
    }
}
