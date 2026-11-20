package io.github.rkaushikdev.stemwright.app.services;

import static io.github.rkaushikdev.stemwright.support.App.seededStores;
import static io.github.rkaushikdev.stemwright.support.App.testHarness;
import static io.github.rkaushikdev.stemwright.support.Profiles.LATIN_NOUN;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.app.AttemptStore;
import io.github.rkaushikdev.stemwright.app.LedgerStore;
import io.github.rkaushikdev.stemwright.app.SessionStore;
import io.github.rkaushikdev.stemwright.app.Stores;
import io.github.rkaushikdev.stemwright.domain.drill.Deck;
import io.github.rkaushikdev.stemwright.domain.drill.DeckInput;
import io.github.rkaushikdev.stemwright.domain.drill.Session;
import io.github.rkaushikdev.stemwright.domain.mastery.Attempt;
import io.github.rkaushikdev.stemwright.domain.mastery.MasteryLedger;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import io.github.rkaushikdev.stemwright.domain.time.DayRange;
import io.github.rkaushikdev.stemwright.support.App.Harness;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The order an answer is written in is the recovery story: the attempt log, then the open session,
 * then the ledger, and only then the announcement. The order cannot be seen from the results, so
 * this checks it through stores that note each write.
 */
class DrillServiceWriteOrderTest {

    private static final LocalDate DAY = CivilDates.parseCivilDate("2024-10-21");

    private final List<String> writes = new ArrayList<>();

    private DrillService serviceOver(Stores seeded) {
        Stores noted = new Stores(
                seeded.languages(),
                seeded.lexemes(),
                seeded.books(),
                seeded.decks(),
                new NotedLedgers(seeded.ledgers()),
                new NotedAttempts(seeded.attempts()),
                new NotedSessions(seeded.sessions()),
                seeded.courses());
        Harness harness = testHarness();
        harness.bus().onAny(event -> writes.add("publish " + event.kind().key()));
        return new DrillService(noted, harness.bus(), harness.clock());
    }

    private static Stores withDeck() {
        Stores stores = seededStores();
        stores.decks().put(Deck.of(LATIN_NOUN, DeckInput.of("all")));
        return stores;
    }

    private static AnswerRequest ask(String cellId, LocalDate on) {
        return AnswerRequest.of("lrn_one", "latin", "all", cellId, "x").withOn(on);
    }

    @Test
    void writesTheAttemptThenTheSessionThenTheLedgerAndAnnouncesLast() {
        DrillService service = serviceOver(withDeck());
        String cellId = service.queue("lrn_one", "latin", "all", DAY)
                .entries()
                .get(0)
                .cell()
                .id();
        writes.clear();

        service.answer(ask(cellId, DAY));

        assertThat(writes)
                .containsExactly("attempts.append", "sessions.put", "ledgers.put", "publish attempt.recorded");
    }

    @Test
    void closesAStaleSessionBeforeWritingTheAnswer() {
        DrillService service = serviceOver(withDeck());
        String cellId = service.queue("lrn_one", "latin", "all", DAY)
                .entries()
                .get(0)
                .cell()
                .id();
        service.answer(ask(cellId, DAY));
        writes.clear();

        service.answer(ask(cellId, DAY.plusDays(2)));

        assertThat(writes)
                .containsExactly(
                        "sessions.put closed",
                        "attempts.append",
                        "sessions.put",
                        "ledgers.put",
                        "publish attempt.recorded");
    }

    private final class NotedAttempts implements AttemptStore {

        private final AttemptStore inner;

        NotedAttempts(AttemptStore inner) {
            this.inner = inner;
        }

        @Override
        public void append(List<Attempt> entries) {
            writes.add("attempts.append");
            inner.append(entries);
        }

        @Override
        public List<Attempt> listByLearner(String learnerId) {
            return inner.listByLearner(learnerId);
        }

        @Override
        public List<Attempt> listByLearner(String learnerId, DayRange range) {
            return inner.listByLearner(learnerId, range);
        }

        @Override
        public List<Attempt> listBySession(String sessionId) {
            return inner.listBySession(sessionId);
        }
    }

    private final class NotedSessions implements SessionStore {

        private final SessionStore inner;

        NotedSessions(SessionStore inner) {
            this.inner = inner;
        }

        @Override
        public Optional<Session> get(String id) {
            return inner.get(id);
        }

        @Override
        public void put(Session session) {
            writes.add(session.closed() ? "sessions.put closed" : "sessions.put");
            inner.put(session);
        }

        @Override
        public List<Session> listOpen(String learnerId) {
            return inner.listOpen(learnerId);
        }
    }

    private final class NotedLedgers implements LedgerStore {

        private final LedgerStore inner;

        NotedLedgers(LedgerStore inner) {
            this.inner = inner;
        }

        @Override
        public Optional<MasteryLedger> get(String learnerId) {
            return inner.get(learnerId);
        }

        @Override
        public void put(MasteryLedger ledger) {
            writes.add("ledgers.put");
            inner.put(ledger);
        }
    }
}
