package io.github.rkaushikdev.stemwright.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.rkaushikdev.stemwright.app.Clock;
import io.github.rkaushikdev.stemwright.app.Stores;
import io.github.rkaushikdev.stemwright.domain.mastery.Attempt;
import io.github.rkaushikdev.stemwright.domain.mastery.AttemptInput;
import io.github.rkaushikdev.stemwright.domain.mastery.CellStrength;
import io.github.rkaushikdev.stemwright.domain.mastery.Ladder;
import io.github.rkaushikdev.stemwright.domain.mastery.LadderInput;
import io.github.rkaushikdev.stemwright.domain.mastery.MasteryLedger;
import io.github.rkaushikdev.stemwright.domain.ortho.MatchVerdict;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import io.github.rkaushikdev.stemwright.infra.json.JsonObject;
import io.github.rkaushikdev.stemwright.support.SeededRandom;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The journal has one job and one property: whatever is replayed out of it is what was put into it.
 * Everything the store does above it rests on that, and a single ordering or encoding mistake would
 * be invisible until somebody restarted a process that had been up for a term.
 */
class JournalInvariantsTest {

    private static final LocalDate DAY = CivilDates.parseCivilDate("2025-03-03");
    private static final Instant AT = Instant.parse("2025-03-03T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(AT, DAY);
    private static final Ladder FLAT =
            Ladder.of(LadderInput.builder("flat", 1, 3, 7).spread(0).build());

    private static AttemptInput.Builder base() {
        return AttemptInput.builder()
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
                .revisionId("rev_aaaaaa");
    }

    private record Busy(DataLayout layout, Journal journal, Stores stores, CellStrength strength) {}

    /**
     * A store after a run of answers: one attempt and one ledger put per answer, the answer right or
     * wrong and the next day zero to three days on, all drawn from one seed.
     */
    private static Busy busyStore(Path root, int writes) {
        DataLayout layout = DataLayout.of(root);
        Journal journal = FileJournal.open(layout);
        Stores stores =
                JournalledStores.of(MemoryStores.create(), journal, CLOCK).stores();
        SeededRandom random = new SeededRandom(7331);

        CellStrength strength = CellStrength.fresh("cell_aaaaaa");
        LocalDate day = DAY;

        for (int index = 0; index < writes; index += 1) {
            stores.attempts()
                    .append(List.of(Attempt.of(base().sequence(index + 1)
                            .on(day)
                            .given(random.word())
                            .build())));
            strength = random.bool(0.6) ? strength.recordCorrect(FLAT, day) : strength.recordLapse(FLAT, day);
            stores.ledgers().put(MasteryLedger.of("lrn_one", List.of(strength)));
            day = CivilDates.addDays(day, random.nextInt(0, 3));
        }

        return new Busy(layout, journal, stores, strength);
    }

    @Nested
    class TheJournalAfterRealUse {

        @Test
        void numbersEveryEntryFromOneWithNoGaps(@TempDir Path root) {
            Busy busy = busyStore(root, 30);
            List<JournalEntry> entries = busy.journal().read();
            assertThat(entries).hasSize(60);
            assertThatCode(() -> Replay.assertContiguous(entries)).doesNotThrowAnyException();
        }

        @Test
        void roundTripsThroughItsOwnEncodingByteForByte(@TempDir Path root) {
            Busy busy = busyStore(root, 20);
            List<JournalEntry> entries = busy.journal().read();
            String text = Journal.encode(entries);
            assertThat(Journal.encode(Journal.decode(text))).isEqualTo(text);
        }

        @Test
        void replaysIntoAStateThatMatchesTheLiveOne(@TempDir Path root) {
            Busy busy = busyStore(root, 25);
            Stores rebuilt = MemoryStores.create();
            Replay.apply(busy.journal().read(), rebuilt);

            List<Attempt> live = busy.stores().attempts().listByLearner("lrn_one");
            List<Attempt> copied = rebuilt.attempts().listByLearner("lrn_one");
            assertThat(copied).hasSize(live.size());
            assertThat(rebuilt.ledgers().get("lrn_one").orElseThrow().strengthFor("cell_aaaaaa"))
                    .isEqualTo(busy.strength());
        }

        /**
         * The snapshot is taken at the journal position the store is actually at. Labelling a snapshot
         * with a sequence lower than the state it captured makes the replay apply those entries a
         * second time, which is why a compaction reads {@code lastSeq} itself rather than taking a
         * number from its caller.
         */
        @Test
        void givesTheSameStateWhetherOrNotASnapshotWasTaken(@TempDir Path root) {
            Busy busy = busyStore(root, 20);
            long at = busy.journal().lastSeq();
            Snapshot.write(busy.layout(), Snapshot.build(busy.stores(), List.of("lrn_one"), at, AT));

            Stores withSnapshot = MemoryStores.create();
            Snapshot.bootstrap(busy.layout(), busy.journal(), withSnapshot);

            Stores plain = MemoryStores.create();
            Replay.apply(busy.journal().read(), plain);

            List<Attempt> left = withSnapshot.attempts().listByLearner("lrn_one");
            List<Attempt> right = plain.attempts().listByLearner("lrn_one");
            assertThat(left.stream().map(Attempt::id).toList())
                    .isEqualTo(right.stream().map(Attempt::id).toList());
            assertThat(withSnapshot.ledgers().get("lrn_one"))
                    .isEqualTo(plain.ledgers().get("lrn_one"));
        }

        @Test
        void survivesBeingReopenedFromDisk(@TempDir Path root) {
            Busy busy = busyStore(root, 15);
            Journal reopened = FileJournal.open(busy.layout());
            assertThat(reopened.size()).isEqualTo(busy.journal().size());
            assertThat(reopened.read()).isEqualTo(busy.journal().read());
        }

        @Test
        void carriesOnNumberingAfterAReopen(@TempDir Path root) {
            Busy busy = busyStore(root, 10);
            long before = busy.journal().lastSeq();
            Journal reopened = FileJournal.open(busy.layout());
            List<JournalEntry> written =
                    reopened.append(List.of(new JournalAppend(JournalKind.LEDGER_PUT, JsonObject.EMPTY)), AT);
            assertThat(written.get(0).seq()).isEqualTo(before + 1);
        }

        @Test
        void replaysAReopenedJournalToTheSamePlace(@TempDir Path root) {
            Busy busy = busyStore(root, 18);
            Journal reopened = FileJournal.open(DataLayout.of(busy.layout().root()));
            Stores rebuilt = MemoryStores.create();
            Replay.apply(reopened.read(), rebuilt);
            assertThat(rebuilt.ledgers().get("lrn_one"))
                    .isEqualTo(busy.stores().ledgers().get("lrn_one"));
        }

        @Test
        void appliesEveryEntryItHoldsAndSkipsNothing(@TempDir Path root) {
            Busy busy = busyStore(root, 12);
            ReplayReport report = Replay.apply(busy.journal().read(), MemoryStores.create());
            assertThat(report.skipped()).isZero();
            assertThat(report.applied()).isEqualTo(busy.journal().size());
        }
    }
}
