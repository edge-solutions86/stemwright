package io.github.rkaushikdev.stemwright.infra;

import java.nio.file.Path;
import java.util.Objects;

/**
 * What a {@link Backup#copy backup} copied.
 *
 * @param destination the directory the backup was written to, as it was given
 * @param journalCopied whether there was a journal to copy
 * @param snapshotsCopied how many snapshots were copied
 */
public record BackupReport(Path destination, boolean journalCopied, int snapshotsCopied) {

    public BackupReport {
        Objects.requireNonNull(destination, "destination");
    }
}
