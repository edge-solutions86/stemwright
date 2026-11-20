package io.github.rkaushikdev.stemwright.infra;

import io.github.rkaushikdev.stemwright.app.Stores;
import io.github.rkaushikdev.stemwright.domain.errors.IntegrityException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Backing up and tidying, which are the two operational jobs this store needs and the only two
 * places that touch files nobody is currently reading.
 *
 * <p>Compaction never removes a journal entry. Truncating an append only log is the one way to make
 * a replay produce a different answer to the one it produced yesterday, and the space it would save
 * is measured in megabytes while the failure it would cause is measured in a term's marks. What it
 * does instead is write a fresh snapshot so the replay is short, and drop the snapshots nobody needs
 * any more.
 */
public final class Backup {

    /** How many snapshots a compaction keeps unless told otherwise. */
    private static final int DEFAULT_KEEP = 3;

    private Backup() {}

    /**
     * Copies the layout's journal and every snapshot into the destination, which then is a data
     * directory in the same layout that a start up can read. The journal is written in one step, so
     * a reader of the destination never sees half of it. An empty store copies nothing and does not
     * fail.
     *
     * @throws UncheckedIOException when a file cannot be read or written
     */
    public static BackupReport copy(DataLayout layout, Path destination) {
        DataFiles.ensureDirectory(destination);
        DataLayout target = DataLayout.of(destination);
        Optional<String> journalText = DataFiles.readIfPresent(layout.journalPath());
        journalText.ifPresent(text -> DataFiles.atomicWrite(target.journalPath(), text));

        List<Long> seqs = Snapshot.list(layout);
        if (!seqs.isEmpty()) {
            DataFiles.ensureDirectory(target.snapshotDir());
        }
        for (long seq : seqs) {
            copyFile(layout.snapshotPath(seq), target.snapshotPath(seq));
        }

        return new BackupReport(destination, journalText.isPresent(), seqs.size());
    }

    /** {@link #compact(DataLayout, Journal, Stores, List, Instant, int)} keeping three snapshots. */
    public static CompactReport compact(
            DataLayout layout, Journal journal, Stores stores, List<String> learnerIds, Instant at) {
        return compact(layout, journal, stores, learnerIds, at, DEFAULT_KEEP);
    }

    /**
     * Writes a snapshot of the learners at the journal's last sequence and prunes all but the newest
     * {@code keep}. Doing nothing is a legitimate outcome: a journal that has not moved since the last
     * snapshot has nothing to compact, and writing an identical snapshot every night would only make
     * the pruning harder to reason about.
     *
     * <p>The position is read from the journal rather than taken from the caller. Labelling a snapshot
     * with a sequence lower than the state it captured would make a start up apply those entries a
     * second time.
     *
     * @throws IntegrityException when {@code keep} is below one
     */
    public static CompactReport compact(
            DataLayout layout, Journal journal, Stores stores, List<String> learnerIds, Instant at, int keep) {
        if (keep < 1) {
            throw new IntegrityException("a compaction must keep at least one snapshot, not " + keep);
        }

        long seq = journal.lastSeq();
        Optional<Snapshot> newest = Snapshot.latest(layout);
        int entries = journal.size();

        if (newest.isPresent() && newest.get().seq() >= seq) {
            return new CompactReport(false, newest.get().seq(), List.of(), entries);
        }

        Snapshot.write(layout, Snapshot.build(stores, learnerIds, seq, at));
        List<Long> removed = pruneSnapshots(layout, keep);
        return new CompactReport(true, seq, removed, entries);
    }

    /**
     * Removes all but the newest {@code keep} snapshots and reports the sequences it dropped, oldest
     * first.
     *
     * @throws IntegrityException when {@code keep} is below one
     */
    public static List<Long> pruneSnapshots(DataLayout layout, int keep) {
        if (keep < 1) {
            throw new IntegrityException("a prune must keep at least one snapshot, not " + keep);
        }
        List<Long> seqs = Snapshot.list(layout);
        List<Long> doomed = seqs.subList(0, Math.max(0, seqs.size() - keep));
        for (long seq : doomed) {
            DataFiles.removeIfPresent(layout.snapshotPath(seq));
        }
        return List.copyOf(doomed);
    }

    private static void copyFile(Path source, Path target) {
        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
