package io.github.rkaushikdev.stemwright.infra;

import java.util.List;

/**
 * What a {@link Backup#compact compaction} did.
 *
 * @param wroteSnapshot whether a snapshot was written, false when the newest one was already current
 * @param atSeq the sequence of the snapshot written, or of the newest one when none was
 * @param removed the sequences of the older snapshots pruned, oldest first
 * @param journalEntries how many entries the journal held, all of which it still holds
 */
public record CompactReport(boolean wroteSnapshot, long atSeq, List<Long> removed, int journalEntries) {

    public CompactReport {
        removed = List.copyOf(removed);
    }

    /**
     * One line for an operator: {@code snapshot written at 12 over 12 journal entries, dropped 1
     * older snapshots}, or {@code nothing to compact, the newest snapshot is already at 12}.
     */
    public String describe() {
        if (!wroteSnapshot) {
            return "nothing to compact, the newest snapshot is already at " + atSeq;
        }
        String pruned = removed.isEmpty() ? "" : ", dropped " + removed.size() + " older snapshots";
        return "snapshot written at " + atSeq + " over " + journalEntries + " journal entries" + pruned;
    }
}
