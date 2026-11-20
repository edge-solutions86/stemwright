package io.github.rkaushikdev.stemwright.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.app.Clock;
import io.github.rkaushikdev.stemwright.app.Stores;
import io.github.rkaushikdev.stemwright.domain.errors.IntegrityException;
import io.github.rkaushikdev.stemwright.domain.mastery.Attempt;
import io.github.rkaushikdev.stemwright.domain.mastery.AttemptInput;
import io.github.rkaushikdev.stemwright.domain.mastery.CellStrength;
import io.github.rkaushikdev.stemwright.domain.mastery.Ladder;
import io.github.rkaushikdev.stemwright.domain.mastery.LadderInput;
import io.github.rkaushikdev.stemwright.domain.mastery.MasteryLedger;
import io.github.rkaushikdev.stemwright.domain.ortho.MatchVerdict;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import io.github.rkaushikdev.stemwright.infra.json.Json;
import io.github.rkaushikdev.stemwright.infra.json.JsonArray;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotTest {

    private static final LocalDate DAY = CivilDates.parseCivilDate("2025-03-03");
    private static final Instant AT = Instant.parse("2025-03-03T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(AT, DAY);
    private static final Ladder FLAT =
            Ladder.of(LadderInput.builder("flat", 1, 3).spread(0).build());

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

    private static Stores seededStore() {
        Stores stores = MemoryStores.create();
        stores.attempts().append(List.of(BASE));
        stores.ledgers()
                .put(MasteryLedger.of(
                        "lrn_one", List.of(CellStrength.fresh("cell_aaaaaa").recordCorrect(FLAT, DAY))));
        return stores;
    }

    private static Stores journalled(Journal journal) {
        return JournalledStores.of(MemoryStores.create(), journal, CLOCK).stores();
    }

    @Nested
    class BuildSnapshot {

        @Test
        void capturesTheLedgersAndAttemptsOfTheLearnersItWasGiven() {
            Snapshot snapshot = Snapshot.build(seededStore(), List.of("lrn_one"), 2, AT);
            assertThat(snapshot.seq()).isEqualTo(2);
            assertThat(snapshot.learners()).containsExactly("lrn_one");
            assertThat(snapshot.at()).isEqualTo("2025-03-03T09:00:00.000Z");
        }

        @Test
        void skipsALearnerWithNoLedger() {
            Snapshot snapshot = Snapshot.build(MemoryStores.create(), List.of("lrn_one"), 1, AT);
            assertThat(snapshot.payload().get("ledgers")).contains(JsonArray.EMPTY);
            assertThat(snapshot.payload().get("attempts")).contains(JsonArray.EMPTY);
        }

        @Test
        void sortsTheLearnersSoTwoSnapshotsOfOneStateCompareEqual() {
            Stores stores = seededStore();
            Snapshot first = Snapshot.build(stores, List.of("lrn_two", "lrn_one"), 2, AT);
            Snapshot second = Snapshot.build(stores, List.of("lrn_one", "lrn_two"), 2, AT);
            assertThat(first.encode()).isEqualTo(second.encode());
        }
    }

    @Nested
    class EncodeAndDecode {

        @Test
        void roundTrips() {
            Snapshot snapshot = Snapshot.build(seededStore(), List.of("lrn_one"), 2, AT);
            Snapshot decoded = Snapshot.decode(snapshot.encode());
            assertThat(decoded.seq()).isEqualTo(2);
            assertThat(decoded.learners()).containsExactly("lrn_one");
        }

        @Test
        void refusesTextThatIsNotJson() {
            assertThatThrownBy(() -> Snapshot.decode("{")).isInstanceOf(IntegrityException.class);
        }

        @Test
        void refusesASnapshotWithNoLearnersArray() {
            String text =
                    Json.stringify(Json.object().put("seq", 1).put("at", "x").build());
            assertThatThrownBy(() -> Snapshot.decode(text)).hasMessageContaining("learners is not an array");
        }
    }

    @Nested
    class WriteSnapshotAndListSnapshots {

        @Test
        void writesAFileNamedAfterTheSequence(@TempDir Path root) {
            DataLayout layout = DataLayout.of(root);
            Snapshot snapshot = Snapshot.build(seededStore(), List.of("lrn_one"), 2, AT);
            Path path = Snapshot.write(layout, snapshot);
            assertThat(path.toString()).endsWith("000000000002.json");
            assertThat(Snapshot.list(layout)).containsExactly(2L);
        }

        @Test
        void listsNothingBeforeAnySnapshotIsWritten(@TempDir Path root) {
            assertThat(Snapshot.list(DataLayout.of(root))).isEmpty();
        }

        @Test
        void takesTheNewestOfSeveral(@TempDir Path root) {
            DataLayout layout = DataLayout.of(root);
            Stores stores = seededStore();
            Snapshot.write(layout, Snapshot.build(stores, List.of("lrn_one"), 2, AT));
            Snapshot.write(layout, Snapshot.build(stores, List.of("lrn_one"), 9, AT));
            assertThat(Snapshot.list(layout)).containsExactly(2L, 9L);
            assertThat(Snapshot.latest(layout)).map(Snapshot::seq).contains(9L);
        }

        @Test
        void givesNothingWhenThereIsNoSnapshotAtAll(@TempDir Path root) {
            assertThat(Snapshot.latest(DataLayout.of(root))).isEmpty();
        }
    }

    @Nested
    class Restore {

        @Test
        void putsTheLedgersAndAttemptsIntoFreshStores() {
            Snapshot snapshot = Snapshot.build(seededStore(), List.of("lrn_one"), 2, AT);
            Stores rebuilt = MemoryStores.create();
            snapshot.restore(rebuilt);
            assertThat(rebuilt.attempts().listByLearner("lrn_one")).hasSize(1);
            assertThat(rebuilt.ledgers()
                            .get("lrn_one")
                            .orElseThrow()
                            .strengthFor("cell_aaaaaa")
                            .seen())
                    .isEqualTo(1);
        }

        @Test
        void toleratesASnapshotHoldingNothing() {
            Snapshot snapshot = Snapshot.build(MemoryStores.create(), List.of(), 1, AT);
            assertThatCode(() -> snapshot.restore(MemoryStores.create())).doesNotThrowAnyException();
        }
    }

    @Nested
    class Bootstrap {

        @Test
        void replaysTheWholeJournalWhenThereIsNoSnapshot(@TempDir Path root) {
            DataLayout layout = DataLayout.of(root);
            Journal journal = FileJournal.open(layout);
            Stores stores = journalled(journal);
            stores.attempts().append(List.of(BASE));

            Stores rebuilt = MemoryStores.create();
            BootstrapReport report = Snapshot.bootstrap(layout, journal, rebuilt);
            assertThat(report.fromSnapshot()).isEmpty();
            assertThat(report.applied()).isEqualTo(1);
            assertThat(rebuilt.attempts().listByLearner("lrn_one")).hasSize(1);
        }

        @Test
        void startsAfterTheSnapshotAndReplaysOnlyWhatFollowed(@TempDir Path root) {
            DataLayout layout = DataLayout.of(root);
            Journal journal = FileJournal.open(layout);
            Stores stores = journalled(journal);
            stores.attempts().append(List.of(BASE));
            Snapshot.write(layout, Snapshot.build(stores, List.of("lrn_one"), 1, AT));
            stores.ledgers().put(MasteryLedger.of("lrn_one", List.of(CellStrength.fresh("cell_b"))));

            Stores rebuilt = MemoryStores.create();
            BootstrapReport report = Snapshot.bootstrap(layout, journal, rebuilt);
            assertThat(report.fromSnapshot()).contains(1L);
            assertThat(report.applied()).isEqualTo(1);
            assertThat(rebuilt.attempts().listByLearner("lrn_one")).hasSize(1);
            assertThat(rebuilt.ledgers().get("lrn_one").map(ledger -> ledger.byCell()
                            .size()))
                    .contains(1);
        }

        @Test
        void givesTheSameStateAsAReplayFromTheBeginning(@TempDir Path root, @TempDir Path elsewhere) {
            DataLayout layout = DataLayout.of(root);
            Journal journal = FileJournal.open(layout);
            Stores stores = journalled(journal);
            stores.attempts().append(List.of(BASE));
            stores.ledgers().put(MasteryLedger.of("lrn_one", List.of(CellStrength.fresh("cell_b"))));
            Snapshot.write(layout, Snapshot.build(stores, List.of("lrn_one"), 2, AT));

            Stores withSnapshot = MemoryStores.create();
            Snapshot.bootstrap(layout, journal, withSnapshot);

            Stores withoutSnapshot = MemoryStores.create();
            Snapshot.bootstrap(DataLayout.of(elsewhere), journal, withoutSnapshot);

            assertThat(withSnapshot.ledgers().get("lrn_one").map(ledger -> ledger.byCell()
                            .size()))
                    .isEqualTo(withoutSnapshot.ledgers().get("lrn_one").map(ledger -> ledger.byCell()
                            .size()));
        }
    }
}
