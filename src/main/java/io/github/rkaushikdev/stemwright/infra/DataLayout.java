package io.github.rkaushikdev.stemwright.infra;

import io.github.rkaushikdev.stemwright.domain.text.Text;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The file layout. Everything the desk keeps lives under one directory, which is what makes a backup
 * a copy of a folder rather than a procedure.
 *
 * <pre>
 * &lt;root&gt;/journal.ndjson     every write, in order
 * &lt;root&gt;/snapshots/&lt;n&gt;.json a point the replay can start from
 * </pre>
 *
 * <p>Writes that are not appends go through {@link DataFiles#atomicWrite}, so a reader never sees a
 * half written snapshot.
 *
 * @param root the directory everything lives under
 * @param journalPath the journal, one entry per line
 * @param snapshotDir the directory the snapshots are written to
 */
public record DataLayout(Path root, Path journalPath, Path snapshotDir) {

    /** The width a snapshot's sequence is padded to, so the file names sort in sequence order. */
    private static final int SEQ_WIDTH = 12;

    private static final Pattern SNAPSHOT_NAME = Pattern.compile("([0-9]{" + SEQ_WIDTH + "})\\.json");

    public DataLayout {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(journalPath, "journalPath");
        Objects.requireNonNull(snapshotDir, "snapshotDir");
    }

    /** The standard layout under a root: {@code journal.ndjson} and {@code snapshots} beside it. */
    public static DataLayout of(Path root) {
        return new DataLayout(root, root.resolve("journal.ndjson"), root.resolve("snapshots"));
    }

    /** Where the snapshot taken at a sequence lives, such as {@code snapshots/000000000007.json}. */
    public Path snapshotPath(long seq) {
        return snapshotDir.resolve(Text.padStart(Long.toString(seq), SEQ_WIDTH, '0') + ".json");
    }

    /** Reads the sequence a snapshot file name encodes, or nothing if it is not one. */
    public static Optional<Long> snapshotSeq(String fileName) {
        Matcher match = SNAPSHOT_NAME.matcher(fileName);
        return match.matches() ? Optional.of(Long.parseLong(match.group(1))) : Optional.empty();
    }
}
