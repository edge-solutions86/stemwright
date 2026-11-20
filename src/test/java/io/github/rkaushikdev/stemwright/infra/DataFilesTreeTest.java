package io.github.rkaushikdev.stemwright.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A data directory is removed as a whole. The tests here run on a directory JUnit removes, so the
 * tree removal needs a test of its own.
 */
class DataFilesTreeTest {

    @Test
    void removesADirectoryAndEverythingInIt(@TempDir Path root) {
        Path data = root.resolve("data");
        DataLayout layout = DataLayout.of(data);
        DataFiles.atomicWrite(layout.journalPath(), "{}\n");
        DataFiles.atomicWrite(layout.snapshotPath(7), "{}");
        DataFiles.removeIfPresent(data);
        assertThat(Files.exists(data)).isFalse();
        assertThat(Files.exists(root)).isTrue();
    }
}
