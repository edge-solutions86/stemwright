package io.github.rkaushikdev.stemwright.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.errors.IntegrityException;
import io.github.rkaushikdev.stemwright.domain.text.Text;
import io.github.rkaushikdev.stemwright.domain.time.Instants;
import io.github.rkaushikdev.stemwright.infra.json.Json;
import io.github.rkaushikdev.stemwright.infra.json.JsonObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataFilesTest {

    private static final Instant AT = Instant.parse("2025-03-03T09:00:00Z");

    private static List<JournalAppend> ledgerPut(JsonObject payload) {
        return List.of(new JournalAppend(JournalKind.LEDGER_PUT, payload));
    }

    @Nested
    class DataLayoutOf {

        @Test
        void putsTheJournalAndTheSnapshotsUnderTheRoot() {
            DataLayout layout = DataLayout.of(Path.of("a", "b"));
            assertThat(layout.journalPath().toString()).endsWith("journal.ndjson");
            assertThat(layout.snapshotDir().toString()).endsWith("snapshots");
        }
    }

    @Nested
    class AtomicWriteAndReadIfPresent {

        @Test
        void writesAndReadsBack(@TempDir Path root) {
            Path path = root.resolve("nested").resolve("file.json");
            DataFiles.atomicWrite(path, "hello");
            assertThat(DataFiles.readIfPresent(path)).contains("hello");
        }

        @Test
        void createsTheDirectoryItNeeds(@TempDir Path root) {
            DataFiles.atomicWrite(root.resolve("deep").resolve("deeper").resolve("file.json"), "hello");
            assertThat(DataFiles.readIfPresent(
                            root.resolve("deep").resolve("deeper").resolve("file.json")))
                    .contains("hello");
        }

        @Test
        void replacesAnExistingFile(@TempDir Path root) throws IOException {
            Path path = root.resolve("file.json");
            DataFiles.atomicWrite(path, "first");
            DataFiles.atomicWrite(path, "second");
            assertThat(Files.readString(path, StandardCharsets.UTF_8)).isEqualTo("second");
        }

        @Test
        void leavesNoTemporaryFileBehind(@TempDir Path root) {
            Path path = root.resolve("file.json");
            DataFiles.atomicWrite(path, "hello");
            assertThat(DataFiles.readIfPresent(root.resolve("file.json.tmp"))).isEmpty();
        }

        @Test
        void readsAMissingFileAsUndefined(@TempDir Path root) {
            assertThat(DataFiles.readIfPresent(root.resolve("nowhere.json"))).isEmpty();
        }
    }

    @Nested
    class IsMissing {

        @Test
        void recognisesTheMissingFileCodeAndNothingElse() {
            assertThat(DataFiles.isMissing(new NoSuchFileException("nowhere.json")))
                    .isTrue();
            assertThat(DataFiles.isMissing(new UncheckedIOException(new NoSuchFileException("nowhere.json"))))
                    .isTrue();
            assertThat(DataFiles.isMissing(new AccessDeniedException("locked.json")))
                    .isFalse();
            assertThat(DataFiles.isMissing(new IllegalStateException("nope"))).isFalse();
            assertThat(DataFiles.isMissing(null)).isFalse();
        }
    }

    @Nested
    class RemoveIfPresent {

        @Test
        void removesAFileAndToleratesOneThatIsNotThere(@TempDir Path root) {
            Path path = root.resolve("file.json");
            DataFiles.atomicWrite(path, "hello");
            DataFiles.removeIfPresent(path);
            assertThat(DataFiles.readIfPresent(path)).isEmpty();
            assertThatCode(() -> DataFiles.removeIfPresent(path)).doesNotThrowAnyException();
        }
    }

    @Nested
    class FileJournalOpen {

        @Test
        void startsEmptyInAFreshDirectory(@TempDir Path root) {
            Journal journal = FileJournal.open(DataLayout.of(root));
            assertThat(journal.size()).isZero();
            assertThat(journal.lastSeq()).isZero();
        }

        @Test
        void appendsAndReadsBack(@TempDir Path root) {
            Journal journal = FileJournal.open(DataLayout.of(root));
            journal.append(ledgerPut(Json.object().put("learnerId", "lrn_one").build()), AT);
            assertThat(journal.size()).isEqualTo(1);
            assertThat(journal.read()).first().extracting(JournalEntry::kind).isEqualTo(JournalKind.LEDGER_PUT);
        }

        @Test
        void survivesAReopen(@TempDir Path root) {
            DataLayout layout = DataLayout.of(root);
            Journal first = FileJournal.open(layout);
            first.append(ledgerPut(JsonObject.EMPTY), AT);
            Journal second = FileJournal.open(layout);
            assertThat(second.size()).isEqualTo(1);
            assertThat(second.lastSeq()).isEqualTo(1);
        }

        @Test
        void carriesOnNumberingAfterAReopen(@TempDir Path root) {
            DataLayout layout = DataLayout.of(root);
            Journal first = FileJournal.open(layout);
            first.append(ledgerPut(JsonObject.EMPTY), AT);
            Journal second = FileJournal.open(layout);
            List<JournalEntry> written = second.append(ledgerPut(JsonObject.EMPTY), AT);
            assertThat(written).first().extracting(JournalEntry::seq).isEqualTo(2L);
        }

        @Test
        void appendsRatherThanRewritingTheFile(@TempDir Path root) throws IOException {
            DataLayout layout = DataLayout.of(root);
            Journal journal = FileJournal.open(layout);
            journal.append(ledgerPut(Json.object().put("a", 1).build()), AT);
            journal.append(ledgerPut(Json.object().put("a", 2).build()), AT);
            String text = Files.readString(layout.journalPath(), StandardCharsets.UTF_8);
            assertThat(Text.split(Text.trim(text), "\n")).hasSize(2);
        }

        @Test
        void writesNothingForAnEmptyAppend(@TempDir Path root) {
            DataLayout layout = DataLayout.of(root);
            Journal journal = FileJournal.open(layout);
            assertThat(journal.append(List.of(), AT)).isEmpty();
            assertThat(DataFiles.readIfPresent(layout.journalPath())).isEmpty();
        }

        @Test
        void refusesToOpenAJournalWhoseSequenceNumbersSkip(@TempDir Path root) throws IOException {
            DataLayout layout = DataLayout.of(root);
            DataFiles.ensureDirectory(layout.root());
            String stamp = Instants.toIsoString(AT);
            List<JournalEntry> entries = List.of(
                    new JournalEntry(1, stamp, JournalKind.LEDGER_PUT, JsonObject.EMPTY),
                    new JournalEntry(3, stamp, JournalKind.LEDGER_PUT, JsonObject.EMPTY));
            Files.writeString(layout.journalPath(), Journal.encode(entries), StandardCharsets.UTF_8);
            assertThatThrownBy(() -> FileJournal.open(layout)).isInstanceOf(IntegrityException.class);
        }

        @Test
        void refusesAReadBelowSequenceOne(@TempDir Path root) {
            Journal journal = FileJournal.open(DataLayout.of(root));
            assertThatThrownBy(() -> journal.read(0)).isInstanceOf(IntegrityException.class);
        }
    }

    @Nested
    class SnapshotPaths {

        @Test
        void padsTheSequenceSoTheFilesSortInOrder() {
            DataLayout layout = DataLayout.of(Path.of("root"));
            assertThat(layout.snapshotPath(7).toString()).endsWith("000000000007.json");
        }

        @Test
        void readsTheSequenceBackOffAName() {
            assertThat(DataLayout.snapshotSeq("000000000007.json")).contains(7L);
            assertThat(DataLayout.snapshotSeq("journal.ndjson")).isEmpty();
            assertThat(DataLayout.snapshotSeq("7.json")).isEmpty();
        }
    }
}
