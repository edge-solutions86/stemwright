package io.github.rkaushikdev.stemwright.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.app.Clock;
import io.github.rkaushikdev.stemwright.app.Stores;
import io.github.rkaushikdev.stemwright.domain.errors.IntegrityException;
import io.github.rkaushikdev.stemwright.domain.mastery.Attempt;
import io.github.rkaushikdev.stemwright.domain.mastery.AttemptInput;
import io.github.rkaushikdev.stemwright.domain.mastery.CellStrength;
import io.github.rkaushikdev.stemwright.domain.mastery.MasteryLedger;
import io.github.rkaushikdev.stemwright.domain.ortho.MatchVerdict;
import io.github.rkaushikdev.stemwright.domain.text.Text;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackupTest {

    private static final LocalDate DAY = CivilDates.parseCivilDate("2025-03-03");
    private static final Instant AT = Instant.parse("2025-03-03T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(AT, DAY);

    private static final Attempt BASE = Attempt.of(AttemptInput.builder()
            .learnerId("lrn_one")
            .sessionId("sess_aaaaaa")
            .cellId("cell_aaaaaa")
            .deckKey("first-year")
            .on(DAY)
            .sequence(1)
            .given("puellae")
            .expected("puellae")
            .verdict(MatchVerdict.EXACT)
            .toleranceLevel("standard")
            .revisionId("rev_aaaaaa")
            .build());

    private record Seeded(DataLayout layout, Journal journal, Stores stores) {}

    /** A data directory holding one attempt and one ledger put, both journalled to disk. */
    private static Seeded seeded(Path root) {
        DataLayout layout = DataLayout.of(root);
        Journal journal = FileJournal.open(layout);
        Stores stores =
                JournalledStores.of(MemoryStores.create(), journal, CLOCK).stores();
        stores.attempts().append(List.of(BASE));
        stores.ledgers().put(MasteryLedger.of("lrn_one", List.of(CellStrength.fresh("cell_aaaaaa"))));
        return new Seeded(layout, journal, stores);
    }

    private static void snapshotAt(Seeded seeded, long seq) {
        Snapshot.write(seeded.layout(), Snapshot.build(seeded.stores(), List.of("lrn_one"), seq, AT));
    }

    private static CompactReport compact(Seeded seeded) {
        return Backup.compact(seeded.layout(), seeded.journal(), seeded.stores(), List.of("lrn_one"), AT);
    }

    @Nested
    class Copy {

        @Test
        void copiesTheJournal(@TempDir Path root, @TempDir Path destination) {
            Seeded seeded = seeded(root);
            BackupReport report = Backup.copy(seeded.layout(), destination);
            assertThat(report.journalCopied()).isTrue();
            assertThat(DataFiles.readIfPresent(destination.resolve("journal.ndjson")))
                    .hasValueSatisfying(text -> assertThat(text).contains("attempt.appended"));
        }

        @Test
        void copiesEverySnapshot(@TempDir Path root, @TempDir Path destination) {
            Seeded seeded = seeded(root);
            snapshotAt(seeded, 1);
            snapshotAt(seeded, 2);
            assertThat(Backup.copy(seeded.layout(), destination).snapshotsCopied())
                    .isEqualTo(2);
            assertThat(Snapshot.list(DataLayout.of(destination))).containsExactly(1L, 2L);
        }

        @Test
        void copiesNothingFromAnEmptyStoreWithoutFailing(@TempDir Path root, @TempDir Path destination) {
            DataLayout layout = DataLayout.of(root);
            BackupReport report = Backup.copy(layout, destination);
            assertThat(report.journalCopied()).isFalse();
            assertThat(report.snapshotsCopied()).isZero();
        }

        @Test
        void producesADirectoryABootstrapCanRead(@TempDir Path root, @TempDir Path destination) {
            Seeded seeded = seeded(root);
            Backup.copy(seeded.layout(), destination);

            DataLayout copied = DataLayout.of(destination);
            Journal journal = FileJournal.open(copied);
            Stores rebuilt = MemoryStores.create();
            assertThat(Snapshot.bootstrap(copied, journal, rebuilt).applied()).isEqualTo(2);
        }
    }

    @Nested
    class Compact {

        @Test
        void writesASnapshotAtTheCurrentPosition(@TempDir Path root) {
            Seeded seeded = seeded(root);
            CompactReport report = compact(seeded);
            assertThat(report.wroteSnapshot()).isTrue();
            assertThat(report.atSeq()).isEqualTo(2);
            assertThat(Snapshot.latest(seeded.layout())).map(Snapshot::seq).contains(2L);
        }

        @Test
        void doesNothingWhenTheJournalHasNotMoved(@TempDir Path root) {
            Seeded seeded = seeded(root);
            compact(seeded);
            CompactReport second = compact(seeded);
            assertThat(second.wroteSnapshot()).isFalse();
            assertThat(Snapshot.list(seeded.layout())).hasSize(1);
        }

        @Test
        void neverRemovesAJournalEntry(@TempDir Path root) {
            Seeded seeded = seeded(root);
            compact(seeded);
            assertThat(seeded.journal().size()).isEqualTo(2);
            assertThat(DataFiles.readIfPresent(seeded.layout().journalPath()))
                    .hasValueSatisfying(text ->
                            assertThat(Text.split(Text.trim(text), "\n")).hasSize(2));
        }

        @Test
        void dropsTheSnapshotsItNoLongerNeeds(@TempDir Path root) {
            Seeded seeded = seeded(root);
            for (long seq : List.of(1L, 2L)) {
                snapshotAt(seeded, seq);
            }
            seeded.stores().ledgers().put(MasteryLedger.of("lrn_one", List.of(CellStrength.fresh("cell_b"))));
            CompactReport report =
                    Backup.compact(seeded.layout(), seeded.journal(), seeded.stores(), List.of("lrn_one"), AT, 2);
            assertThat(report.wroteSnapshot()).isTrue();
            assertThat(report.atSeq()).isEqualTo(3);
            assertThat(report.removed()).containsExactly(1L);
            assertThat(Snapshot.list(seeded.layout())).containsExactly(2L, 3L);
        }

        @Test
        void refusesToKeepFewerThanOneSnapshot(@TempDir Path root) {
            Seeded seeded = seeded(root);
            assertThatThrownBy(() -> Backup.compact(
                            seeded.layout(), seeded.journal(), seeded.stores(), List.of("lrn_one"), AT, 0))
                    .isInstanceOf(IntegrityException.class);
        }

        @Test
        void leavesTheStateReadableAfterCompaction(@TempDir Path root) {
            Seeded seeded = seeded(root);
            compact(seeded);
            Stores rebuilt = MemoryStores.create();
            BootstrapReport report = Snapshot.bootstrap(seeded.layout(), seeded.journal(), rebuilt);
            assertThat(report.fromSnapshot()).contains(2L);
            assertThat(rebuilt.attempts().listByLearner("lrn_one")).hasSize(1);
        }
    }

    @Nested
    class PruneSnapshots {

        @Test
        void keepsTheNewestFew(@TempDir Path root) {
            Seeded seeded = seeded(root);
            for (long seq : List.of(1L, 2L, 3L)) {
                snapshotAt(seeded, seq);
            }
            assertThat(Backup.pruneSnapshots(seeded.layout(), 2)).containsExactly(1L);
            assertThat(Snapshot.list(seeded.layout())).containsExactly(2L, 3L);
        }

        @Test
        void dropsNothingWhenThereAreAlreadyFewEnough(@TempDir Path root) {
            Seeded seeded = seeded(root);
            assertThat(Backup.pruneSnapshots(seeded.layout(), 3)).isEmpty();
        }

        @Test
        void refusesToKeepFewerThanOne(@TempDir Path root) {
            Seeded seeded = seeded(root);
            assertThatThrownBy(() -> Backup.pruneSnapshots(seeded.layout(), 0)).isInstanceOf(IntegrityException.class);
        }
    }

    @Nested
    class DescribeCompaction {

        @Test
        void saysSoWhenThereWasNothingToDo(@TempDir Path root) {
            Seeded seeded = seeded(root);
            compact(seeded);
            CompactReport second = compact(seeded);
            assertThat(second.describe()).contains("nothing to compact");
        }

        @Test
        void namesThePositionAndTheJournalSize(@TempDir Path root) {
            Seeded seeded = seeded(root);
            CompactReport report = compact(seeded);
            assertThat(report.describe()).isEqualTo("snapshot written at 2 over 2 journal entries");
        }
    }
}
