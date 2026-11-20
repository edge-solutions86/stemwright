package io.github.rkaushikdev.stemwright.infra;

import io.github.rkaushikdev.stemwright.app.AttemptStore;
import io.github.rkaushikdev.stemwright.app.Clock;
import io.github.rkaushikdev.stemwright.app.LedgerStore;
import io.github.rkaushikdev.stemwright.app.Stores;
import io.github.rkaushikdev.stemwright.domain.mastery.Attempt;
import io.github.rkaushikdev.stemwright.domain.mastery.MasteryLedger;
import io.github.rkaushikdev.stemwright.domain.time.DayRange;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A set of stores that writes every change down in a journal before it applies it, so that {@link
 * Replay} can walk the journal back into a fresh set of stores later.
 *
 * <p>Only the records with a codec are journalled: attempts and ledgers, which are the two things a
 * learner cannot afford to lose and the two that change on every answer. Lexemes, decks and courses
 * are editorial data that an import can rebuild, and a codec for each of them is a cost that has not
 * been earned yet. The other stores are handed through as they are, rather than silently dropping
 * their writes.
 */
public final class JournalledStores {

    private final Stores stores;
    private final Journal journal;

    private JournalledStores(Stores stores, Journal journal) {
        this.stores = stores;
        this.journal = journal;
    }

    /**
     * Wraps the stores so that an attempt append and a ledger put are written to the journal first,
     * stamped with the clock's present instant, and only then applied. An empty attempt append
     * writes nothing anywhere.
     */
    public static JournalledStores of(Stores stores, Journal journal, Clock clock) {
        Objects.requireNonNull(stores, "stores");
        Objects.requireNonNull(journal, "journal");
        Objects.requireNonNull(clock, "clock");
        Stores wrapped = new Stores(
                stores.languages(),
                stores.lexemes(),
                stores.books(),
                stores.decks(),
                new JournallingLedgers(stores.ledgers(), journal, clock),
                new JournallingAttempts(stores.attempts(), journal, clock),
                stores.sessions(),
                stores.courses());
        return new JournalledStores(wrapped, journal);
    }

    /** The stores, with the attempts and the ledgers writing to the journal before they change. */
    public Stores stores() {
        return stores;
    }

    /** The journal the stores write to. */
    public Journal journal() {
        return journal;
    }

    private static final class JournallingAttempts implements AttemptStore {

        private final AttemptStore inner;
        private final Journal journal;
        private final Clock clock;

        JournallingAttempts(AttemptStore inner, Journal journal, Clock clock) {
            this.inner = inner;
            this.journal = journal;
            this.clock = clock;
        }

        /** One journal entry per attempt, all in one append, and then the store. */
        @Override
        public void append(List<Attempt> entries) {
            if (entries.isEmpty()) {
                return;
            }
            journal.append(
                    entries.stream()
                            .map(entry -> new JournalAppend(JournalKind.ATTEMPT_APPENDED, Records.encodeAttempt(entry)))
                            .toList(),
                    clock.now());
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

    private static final class JournallingLedgers implements LedgerStore {

        private final LedgerStore inner;
        private final Journal journal;
        private final Clock clock;

        JournallingLedgers(LedgerStore inner, Journal journal, Clock clock) {
            this.inner = inner;
            this.journal = journal;
            this.clock = clock;
        }

        @Override
        public Optional<MasteryLedger> get(String learnerId) {
            return inner.get(learnerId);
        }

        /** The whole ledger as one journal entry, and then the store. */
        @Override
        public void put(MasteryLedger ledger) {
            journal.append(
                    List.of(new JournalAppend(JournalKind.LEDGER_PUT, Records.encodeLedger(ledger))), clock.now());
            inner.put(ledger);
        }
    }
}
