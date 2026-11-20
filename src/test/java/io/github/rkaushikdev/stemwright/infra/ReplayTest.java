package io.github.rkaushikdev.stemwright.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.app.Clock;
import io.github.rkaushikdev.stemwright.app.Stores;
import io.github.rkaushikdev.stemwright.domain.drill.Session;
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
import io.github.rkaushikdev.stemwright.infra.json.JsonObject;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReplayTest {

    private static final LocalDate DAY = CivilDates.parseCivilDate("2025-03-03");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2025-03-03T09:00:00Z"), DAY);
    private static final Ladder FLAT =
            Ladder.of(LadderInput.builder("flat", 1, 3).spread(0).build());
    private static final String STAMP = "2025-03-03T09:00:00.000Z";

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

    /** The base input as plain json, written straight into a payload. */
    private static JsonObject baseRow() {
        return Json.object()
                .put("learnerId", "lrn_one")
                .put("sessionId", "sess_aaaaaa")
                .put("cellId", "cell_aaaaaa")
                .put("deckKey", "first-year")
                .put("on", "2025-03-03")
                .put("sequence", 1)
                .put("given", "puellae")
                .put("expected", "puellae")
                .put("verdict", "exact")
                .put("toleranceLevel", "standard")
                .put("revisionId", "rev_aaaaaa")
                .build();
    }

    private static Attempt attempt(AttemptInput.Builder input) {
        return Attempt.of(input.build());
    }

    private record Built(Journal journal, Stores stores) {}

    private static Built build() {
        Journal journal = Journal.inMemory();
        return new Built(
                journal,
                JournalledStores.of(MemoryStores.create(), journal, CLOCK).stores());
    }

    @Nested
    class Journalled {

        @Test
        void writesAnAttemptToTheJournalAndToTheStore() {
            Built built = build();
            built.stores().attempts().append(List.of(attempt(base())));
            assertThat(built.journal().size()).isEqualTo(1);
            assertThat(built.stores().attempts().listByLearner("lrn_one")).hasSize(1);
        }

        @Test
        void writesOneJournalEntryPerAttempt() {
            Built built = build();
            built.stores().attempts().append(List.of(attempt(base()), attempt(base().sequence(2))));
            assertThat(built.journal().size()).isEqualTo(2);
        }

        @Test
        void writesNothingForAnEmptyAppend() {
            Built built = build();
            built.stores().attempts().append(List.of());
            assertThat(built.journal().size()).isZero();
        }

        @Test
        void writesALedgerToTheJournal() {
            Built built = build();
            built.stores().ledgers().put(MasteryLedger.of("lrn_one", List.of(CellStrength.fresh("cell_a"))));
            assertThat(built.journal().size()).isEqualTo(1);
            assertThat(built.journal().read().get(0).kind()).isEqualTo(JournalKind.LEDGER_PUT);
        }

        @Test
        void leavesTheOtherStoresUnwrapped() {
            Built built = build();
            Session session = Session.open("lrn_one", "first-year", DAY);
            built.stores().sessions().put(session);
            assertThat(built.journal().size()).isZero();
            assertThat(built.stores().sessions().get(session.id())).isPresent();
        }
    }

    @Nested
    class ReplayEntries {

        @Test
        void rebuildsAttemptsAndLedgersIntoFreshStores() {
            Built built = build();
            built.stores().attempts().append(List.of(attempt(base())));
            built.stores()
                    .ledgers()
                    .put(MasteryLedger.of(
                            "lrn_one", List.of(CellStrength.fresh("cell_aaaaaa").recordCorrect(FLAT, DAY))));

            Stores rebuilt = MemoryStores.create();
            ReplayReport report = Replay.apply(built.journal(), rebuilt);

            assertThat(report.applied()).isEqualTo(2);
            assertThat(report.skipped()).isZero();
            assertThat(report.lastSeq()).isEqualTo(2);
            assertThat(rebuilt.attempts().listByLearner("lrn_one")).hasSize(1);
            assertThat(rebuilt.ledgers()
                            .get("lrn_one")
                            .orElseThrow()
                            .strengthFor("cell_aaaaaa")
                            .seen())
                    .isEqualTo(1);
        }

        @Test
        void appliesEntriesInSequenceOrderHoweverTheyArrive() {
            List<JournalEntry> entries = List.of(
                    new JournalEntry(
                            2,
                            STAMP,
                            JournalKind.LEDGER_PUT,
                            Json.object()
                                    .put("learnerId", "lrn_one")
                                    .put("cells", JsonArray.EMPTY)
                                    .build()),
                    new JournalEntry(1, STAMP, JournalKind.ATTEMPT_APPENDED, baseRow()));
            Stores rebuilt = MemoryStores.create();
            assertThat(Replay.apply(entries, rebuilt).applied()).isEqualTo(2);
        }

        @Test
        void skipsAKindThisBuildDoesNotHandle() {
            List<JournalEntry> entries = List.of(new JournalEntry(1, STAMP, JournalKind.DECK_PUT, JsonObject.EMPTY));
            ReplayReport report = Replay.apply(entries, MemoryStores.create());
            assertThat(report.applied()).isZero();
            assertThat(report.skipped()).isEqualTo(1);
        }

        @Test
        void stopsOnAMalformedEntryRatherThanSkippingIt() {
            List<JournalEntry> entries = List.of(new JournalEntry(
                    1,
                    STAMP,
                    JournalKind.ATTEMPT_APPENDED,
                    Json.object().put("learnerId", "lrn_one").build()));
            assertThatThrownBy(() -> Replay.apply(entries, MemoryStores.create()))
                    .hasMessageContaining("journal.1");
        }

        @Test
        void reportsNothingForAnEmptyJournal() {
            assertThat(Replay.apply(List.of(), MemoryStores.create())).isEqualTo(new ReplayReport(0, 0, 0));
        }
    }

    @Nested
    class Inspect {

        @Test
        void readsAnAttemptEntryOut() {
            Built built = build();
            built.stores().attempts().append(List.of(attempt(base())));
            assertThat(Replay.inspect(built.journal().read()).get(0))
                    .isEqualTo("1 2025-03-03T09:00:00.000Z attempt cell_aaaaaa by lrn_one");
        }

        @Test
        void readsALedgerEntryOut() {
            Built built = build();
            built.stores().ledgers().put(MasteryLedger.of("lrn_one", List.of()));
            assertThat(Replay.inspect(built.journal().read()).get(0))
                    .isEqualTo("1 2025-03-03T09:00:00.000Z ledger for lrn_one");
        }

        @Test
        void namesTheKindOfAnythingElse() {
            List<JournalEntry> entries = List.of(new JournalEntry(1, STAMP, JournalKind.DECK_PUT, JsonObject.EMPTY));
            assertThat(Replay.inspect(entries).get(0)).isEqualTo("1 2025-03-03T09:00:00.000Z deck.put");
        }
    }

    @Nested
    class AssertContiguous {

        @Test
        void passesOnAJournalThatStartsAtOneAndDoesNotSkip() {
            Built built = build();
            built.stores().attempts().append(List.of(attempt(base()), attempt(base().sequence(2))));
            List<JournalEntry> entries = built.journal().read();
            assertThatCode(() -> Replay.assertContiguous(entries)).doesNotThrowAnyException();
        }

        @Test
        void namesThePositionOfTheGap() {
            List<JournalEntry> entries = List.of(
                    new JournalEntry(1, "x", JournalKind.LEDGER_PUT, JsonObject.EMPTY),
                    new JournalEntry(3, "x", JournalKind.LEDGER_PUT, JsonObject.EMPTY));
            assertThatThrownBy(() -> Replay.assertContiguous(entries)).isInstanceOf(IntegrityException.class);
            assertThatThrownBy(() -> Replay.assertContiguous(entries))
                    .hasMessageContaining("position 2, found sequence 3");
        }

        @Test
        void passesOnAnEmptyJournal() {
            assertThatCode(() -> Replay.assertContiguous(List.of())).doesNotThrowAnyException();
        }
    }
}
