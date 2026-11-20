package io.github.rkaushikdev.stemwright.infra;

import io.github.rkaushikdev.stemwright.domain.errors.IntegrityException;
import io.github.rkaushikdev.stemwright.domain.time.Instants;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A journal on the file system. It caches the entries it has read so a replay does not reparse the
 * whole log on every append, and it appends to the file rather than rewriting it, which is the only
 * reason the log is cheap enough to keep forever.
 *
 * <p>Every method holds the journal's lock, so two request threads appending at once still get
 * distinct, consecutive sequence numbers, and their lines reach the file one append after the other.
 */
public final class FileJournal implements Journal {

    private final Path journalPath;
    private final List<JournalEntry> entries;

    private FileJournal(Path journalPath, List<JournalEntry> entries) {
        this.journalPath = journalPath;
        this.entries = new ArrayList<>(entries);
    }

    /**
     * Opens the journal of a layout, creating its root directory when needed and reading every entry
     * already written. A journal file that does not exist yet is an empty journal.
     *
     * @throws IntegrityException when the file holds a torn or unreadable line or its sequence numbers
     *     do not run one, two, three
     * @throws UncheckedIOException when the directory cannot be created or the file cannot be read
     */
    public static FileJournal open(DataLayout layout) {
        DataFiles.ensureDirectory(layout.root());
        String text = DataFiles.readIfPresent(layout.journalPath()).orElse("");
        return new FileJournal(layout.journalPath(), Journal.decode(text));
    }

    /**
     * Appends the entries to the file as whole lines, then to the cache. An empty list writes nothing,
     * and does not create the file.
     *
     * @throws UncheckedIOException when the file cannot be written, in which case the cache is unchanged
     */
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
        try {
            Files.writeString(
                    journalPath,
                    Journal.encode(written),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        entries.addAll(written);
        return List.copyOf(written);
    }

    /** Refuses a sequence below one as an {@link IntegrityException}. */
    @Override
    public synchronized List<JournalEntry> read(long fromSeq) {
        if (fromSeq < 1) {
            throw new IntegrityException("a journal read starts at sequence one, not " + fromSeq);
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
