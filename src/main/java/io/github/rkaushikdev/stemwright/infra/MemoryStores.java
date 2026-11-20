package io.github.rkaushikdev.stemwright.infra;

import io.github.rkaushikdev.stemwright.app.AttemptStore;
import io.github.rkaushikdev.stemwright.app.BookStore;
import io.github.rkaushikdev.stemwright.app.CourseStore;
import io.github.rkaushikdev.stemwright.app.DeckStore;
import io.github.rkaushikdev.stemwright.app.LanguageBundle;
import io.github.rkaushikdev.stemwright.app.LanguageStore;
import io.github.rkaushikdev.stemwright.app.LedgerStore;
import io.github.rkaushikdev.stemwright.app.LexemeStore;
import io.github.rkaushikdev.stemwright.app.SessionStore;
import io.github.rkaushikdev.stemwright.app.Stores;
import io.github.rkaushikdev.stemwright.domain.course.Assignment;
import io.github.rkaushikdev.stemwright.domain.course.Course;
import io.github.rkaushikdev.stemwright.domain.course.Enrolment;
import io.github.rkaushikdev.stemwright.domain.drill.Deck;
import io.github.rkaushikdev.stemwright.domain.drill.Session;
import io.github.rkaushikdev.stemwright.domain.lexicon.Lexeme;
import io.github.rkaushikdev.stemwright.domain.mastery.Attempt;
import io.github.rkaushikdev.stemwright.domain.mastery.MasteryLedger;
import io.github.rkaushikdev.stemwright.domain.paradigm.ProfileBook;
import io.github.rkaushikdev.stemwright.domain.text.Collation;
import io.github.rkaushikdev.stemwright.domain.time.DayRange;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Stores backed by plain maps. Every service test runs against these, and a single process
 * deployment can run against them too by loading a snapshot at start up and writing one at
 * shutdown.
 *
 * <p>Each store answers from memory and may be called from several threads: every call holds the
 * store's lock for its duration. Listings are unmodifiable snapshots, so a caller can neither change
 * a store through one nor see it change under them.
 */
public final class MemoryStores {

    private MemoryStores() {}

    /** One empty store of every kind. */
    public static Stores create() {
        return new Stores(languages(), lexemes(), books(), decks(), ledgers(), attempts(), sessions(), courses());
    }

    /** An empty language store. */
    public static LanguageStore languages() {
        return new MemoryLanguageStore();
    }

    /** An empty lexeme store. */
    public static LexemeStore lexemes() {
        return new MemoryLexemeStore();
    }

    /** An empty profile book store. */
    public static BookStore books() {
        return new MemoryBookStore();
    }

    /** An empty deck store. */
    public static DeckStore decks() {
        return new MemoryDeckStore();
    }

    /** An empty ledger store. */
    public static LedgerStore ledgers() {
        return new MemoryLedgerStore();
    }

    /** An empty attempt log. */
    public static AttemptStore attempts() {
        return new MemoryAttemptStore();
    }

    /** An empty session store. */
    public static SessionStore sessions() {
        return new MemorySessionStore();
    }

    /** An empty course store. */
    public static CourseStore courses() {
        return new MemoryCourseStore();
    }

    private static final class MemoryLanguageStore implements LanguageStore {

        private final Map<String, LanguageBundle> byLanguage = new HashMap<>();

        @Override
        public synchronized Optional<LanguageBundle> get(String language) {
            return Optional.ofNullable(byLanguage.get(language));
        }

        @Override
        public synchronized List<String> list() {
            return byLanguage.keySet().stream().sorted().toList();
        }

        @Override
        public synchronized void put(LanguageBundle bundle) {
            byLanguage.put(bundle.language(), bundle);
        }
    }

    private static final class MemoryLexemeStore implements LexemeStore {

        private final Map<String, Lexeme> byId = new HashMap<>();

        @Override
        public synchronized Optional<Lexeme> get(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public synchronized List<Lexeme> listByLanguage(String language) {
            return byId.values().stream()
                    .filter(entry -> entry.language().equals(language))
                    .sorted(Lexeme.ORDER)
                    .toList();
        }

        @Override
        public synchronized void put(Lexeme entry) {
            byId.put(entry.id(), entry);
        }

        @Override
        public synchronized void remove(String id) {
            byId.remove(id);
        }
    }

    private static final class MemoryBookStore implements BookStore {

        private final Map<String, ProfileBook> byLanguage = new HashMap<>();

        @Override
        public synchronized Optional<ProfileBook> get(String language) {
            return Optional.ofNullable(byLanguage.get(language));
        }

        @Override
        public synchronized void put(ProfileBook book) {
            byLanguage.put(book.language(), book);
        }
    }

    private static final class MemoryDeckStore implements DeckStore {

        private static final Comparator<Deck> BY_KEY = Comparator.comparing(Deck::key, Collation.ORDER);

        private final Map<Address, Deck> byAddress = new HashMap<>();

        @Override
        public synchronized Optional<Deck> get(String language, String key) {
            return Optional.ofNullable(byAddress.get(new Address(language, key)));
        }

        @Override
        public synchronized List<Deck> listByLanguage(String language) {
            return byAddress.values().stream()
                    .filter(entry -> entry.language().equals(language))
                    .sorted(BY_KEY)
                    .toList();
        }

        @Override
        public synchronized void put(Deck entry) {
            byAddress.put(new Address(entry.language(), entry.key()), entry);
        }

        @Override
        public synchronized void remove(String language, String key) {
            byAddress.remove(new Address(language, key));
        }

        /** A deck key is unique only within its language, so a deck is filed under both. */
        private record Address(String language, String key) {}
    }

    private static final class MemoryLedgerStore implements LedgerStore {

        private final Map<String, MasteryLedger> byLearner = new HashMap<>();

        @Override
        public synchronized Optional<MasteryLedger> get(String learnerId) {
            return Optional.ofNullable(byLearner.get(learnerId));
        }

        @Override
        public synchronized void put(MasteryLedger ledger) {
            byLearner.put(ledger.learnerId(), ledger);
        }
    }

    private static final class MemoryAttemptStore implements AttemptStore {

        private final List<Attempt> entries = new ArrayList<>();

        @Override
        public synchronized void append(List<Attempt> incoming) {
            entries.addAll(incoming);
        }

        @Override
        public synchronized List<Attempt> listByLearner(String learnerId) {
            return matching(entry -> entry.learnerId().equals(learnerId));
        }

        @Override
        public synchronized List<Attempt> listByLearner(String learnerId, DayRange range) {
            return matching(entry -> entry.learnerId().equals(learnerId) && range.contains(entry.on()));
        }

        @Override
        public synchronized List<Attempt> listBySession(String sessionId) {
            return matching(entry -> entry.sessionId().equals(sessionId));
        }

        private List<Attempt> matching(Predicate<Attempt> filter) {
            return entries.stream().filter(filter).sorted(Attempt.ORDER).toList();
        }
    }

    private static final class MemorySessionStore implements SessionStore {

        /** Insertion ordered, because the open sessions are listed in the order first stored. */
        private final Map<String, Session> byId = new LinkedHashMap<>();

        @Override
        public synchronized Optional<Session> get(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public synchronized void put(Session session) {
            byId.put(session.id(), session);
        }

        @Override
        public synchronized List<Session> listOpen(String learnerId) {
            return byId.values().stream()
                    .filter(session -> session.learnerId().equals(learnerId) && !session.closed())
                    .toList();
        }
    }

    private static final class MemoryCourseStore implements CourseStore {

        private static final Comparator<Course> BY_KEY = Comparator.comparing(Course::key, Collation.ORDER);

        private final Map<String, Course> byKey = new HashMap<>();
        private final Map<String, List<Enrolment>> enrolled = new HashMap<>();
        private final Map<String, List<Assignment>> assigned = new HashMap<>();

        @Override
        public synchronized Optional<Course> get(String key) {
            return Optional.ofNullable(byKey.get(key));
        }

        @Override
        public synchronized List<Course> list() {
            return byKey.values().stream().sorted(BY_KEY).toList();
        }

        @Override
        public synchronized void put(Course entry) {
            byKey.put(entry.key(), entry);
        }

        @Override
        public synchronized List<Enrolment> enrolments(String courseKey) {
            return enrolled.getOrDefault(courseKey, List.of());
        }

        @Override
        public synchronized void putEnrolment(Enrolment enrolment) {
            String courseKey = enrolment.courseKey();
            enrolled.put(courseKey, replacing(enrolments(courseKey), enrolment, Enrolment::learnerId));
        }

        @Override
        public synchronized List<Assignment> assignments(String courseKey) {
            return assigned.getOrDefault(courseKey, List.of());
        }

        @Override
        public synchronized void putAssignment(Assignment entry) {
            String courseKey = entry.courseKey();
            assigned.put(courseKey, replacing(assignments(courseKey), entry, Assignment::key));
        }

        /** The rows whose key is not the incoming row's, and the incoming row last. */
        private static <T> List<T> replacing(List<T> rows, T incoming, Function<T, String> keyOf) {
            String key = keyOf.apply(incoming);
            List<T> kept = new ArrayList<>();
            for (T row : rows) {
                if (!keyOf.apply(row).equals(key)) {
                    kept.add(row);
                }
            }
            kept.add(incoming);
            return List.copyOf(kept);
        }
    }
}
