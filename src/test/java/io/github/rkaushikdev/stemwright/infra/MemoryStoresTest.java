package io.github.rkaushikdev.stemwright.infra;

import static io.github.rkaushikdev.stemwright.support.Course.MICHAELMAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.app.Clock;
import io.github.rkaushikdev.stemwright.app.SteppableClock;
import io.github.rkaushikdev.stemwright.app.Stores;
import io.github.rkaushikdev.stemwright.domain.course.Assignment;
import io.github.rkaushikdev.stemwright.domain.course.AssignmentInput;
import io.github.rkaushikdev.stemwright.domain.course.Course;
import io.github.rkaushikdev.stemwright.domain.course.Enrolment;
import io.github.rkaushikdev.stemwright.domain.course.EnrolmentInput;
import io.github.rkaushikdev.stemwright.domain.course.EnrolmentRole;
import io.github.rkaushikdev.stemwright.domain.drill.Deck;
import io.github.rkaushikdev.stemwright.domain.drill.DeckInput;
import io.github.rkaushikdev.stemwright.domain.drill.Session;
import io.github.rkaushikdev.stemwright.domain.lexicon.Lexeme;
import io.github.rkaushikdev.stemwright.domain.mastery.Attempt;
import io.github.rkaushikdev.stemwright.domain.mastery.AttemptInput;
import io.github.rkaushikdev.stemwright.domain.mastery.MasteryLedger;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import io.github.rkaushikdev.stemwright.domain.time.DayRange;
import io.github.rkaushikdev.stemwright.domain.time.Instants;
import io.github.rkaushikdev.stemwright.support.Book;
import io.github.rkaushikdev.stemwright.support.Drill;
import io.github.rkaushikdev.stemwright.support.Profiles;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MemoryStoresTest {

    private static LocalDate d(String value) {
        return CivilDates.parseCivilDate(value);
    }

    @Nested
    class Clocks {

        @Test
        void aFixedClockNeverMoves() {
            Clock clock = Clock.fixed(Instant.parse("2025-03-03T09:00:00Z"), d("2025-03-03"));
            assertThat(clock.today()).isEqualTo(d("2025-03-03"));
            assertThat(Instants.toIsoString(clock.now())).isEqualTo("2025-03-03T09:00:00.000Z");
        }

        @Test
        void aFixedClockHandsOutACopyOfItsInstant() {
            Clock clock = Clock.fixed(Instant.parse("2025-03-03T09:00:00Z"), d("2025-03-03"));
            ZonedDateTime moved = clock.now().atZone(ZoneOffset.UTC).withYear(1999);
            assertThat(moved.getYear()).isEqualTo(1999);
            assertThat(clock.now().atZone(ZoneOffset.UTC).getYear()).isEqualTo(2025);
        }

        @Test
        void aSteppableClockMovesOnlyWhenTold() {
            SteppableClock clock = Clock.steppable(Instant.parse("2025-03-03T09:00:00Z"), d("2025-03-03"));
            assertThat(clock.today()).isEqualTo(d("2025-03-03"));
            clock.set(Instant.parse("2025-03-04T09:00:00Z"), d("2025-03-04"));
            assertThat(clock.today()).isEqualTo(d("2025-03-04"));
            assertThat(clock.now()).isEqualTo(Instant.parse("2025-03-04T09:00:00Z"));
        }

        @Test
        void theSystemClockReportsAnIsoDay() {
            assertThat(Clock.system().today().toString()).matches("\\d{4}-\\d{2}-\\d{2}");
        }
    }

    @Nested
    class LexemeBookDeckAndLedgerStores {

        @Test
        void roundTripsALexemeAndListsByLanguage() {
            Stores stores = MemoryStores.create();
            stores.lexemes().put(Drill.PUELLA);
            stores.lexemes().put(Drill.BELLUM);
            assertThat(stores.lexemes().get(Drill.PUELLA.id()).map(Lexeme::lemma))
                    .contains("puella");
            assertThat(stores.lexemes().listByLanguage("latin"))
                    .extracting(Lexeme::lemma)
                    .containsExactly("bellum", "puella");
            assertThat(stores.lexemes().listByLanguage("greek")).isEmpty();
        }

        @Test
        void removesALexeme() {
            Stores stores = MemoryStores.create();
            stores.lexemes().put(Drill.PUELLA);
            stores.lexemes().remove(Drill.PUELLA.id());
            assertThat(stores.lexemes().get(Drill.PUELLA.id())).isEmpty();
        }

        @Test
        void roundTripsAProfileBookByLanguage() {
            Stores stores = MemoryStores.create();
            stores.books().put(Book.LATIN_BOOK);
            assertThat(stores.books().get("latin").map(book -> book.histories().size()))
                    .contains(2);
            assertThat(stores.books().get("greek")).isEmpty();
        }

        @Test
        void keysDecksByLanguageAndKey() {
            Stores stores = MemoryStores.create();
            stores.decks().put(Deck.of(Profiles.LATIN_NOUN, DeckInput.of("all")));
            assertThat(stores.decks().get("latin", "all").map(Deck::key)).contains("all");
            assertThat(stores.decks().get("greek", "all")).isEmpty();
            stores.decks().remove("latin", "all");
            assertThat(stores.decks().listByLanguage("latin")).isEmpty();
        }

        @Test
        void roundTripsALedgerByLearner() {
            Stores stores = MemoryStores.create();
            stores.ledgers().put(MasteryLedger.empty("lrn_one"));
            assertThat(stores.ledgers().get("lrn_one").map(MasteryLedger::learnerId))
                    .contains("lrn_one");
            assertThat(stores.ledgers().get("lrn_two")).isEmpty();
        }
    }

    @Nested
    class AttemptSessionAndCourseStores {

        private static Attempt makeAttempt(String cellId, String on, int sequence) {
            return makeAttempt(cellId, on, sequence, "sess_a");
        }

        private static Attempt makeAttempt(String cellId, String on, int sequence, String sessionId) {
            return Attempt.of(AttemptInput.builder()
                    .learnerId("lrn_one")
                    .sessionId(sessionId)
                    .cellId(cellId)
                    .deckKey("all")
                    .on(d(on))
                    .sequence(sequence)
                    .given("x")
                    .expected("x")
                    .verdict("exact")
                    .toleranceLevel("standard")
                    .revisionId("rev_a")
                    .build());
        }

        private static Assignment weekThree(int practiceDays) {
            return Assignment.of(
                    Profiles.LATIN_NOUN,
                    MICHAELMAS,
                    AssignmentInput.builder("week-3", "all", d("2024-10-21"), d("2024-10-27"))
                            .practiceDays(practiceDays)
                            .build());
        }

        @Test
        void appendsAttemptsAndReadsThemBackInOrder() {
            Stores stores = MemoryStores.create();
            stores.attempts()
                    .append(List.of(makeAttempt("cell_b", "2024-10-22", 2), makeAttempt("cell_a", "2024-10-21", 1)));
            List<Attempt> found = stores.attempts().listByLearner("lrn_one");
            assertThat(found).extracting(Attempt::cellId).containsExactly("cell_a", "cell_b");
        }

        @Test
        void filtersAttemptsByRangeAndBySession() {
            Stores stores = MemoryStores.create();
            stores.attempts()
                    .append(List.of(
                            makeAttempt("cell_a", "2024-10-21", 1), makeAttempt("cell_b", "2024-10-25", 1, "sess_b")));
            DayRange range = DayRange.of(d("2024-10-24"), d("2024-10-26"));
            assertThat(stores.attempts().listByLearner("lrn_one", range)).hasSize(1);
            assertThat(stores.attempts().listBySession("sess_b")).hasSize(1);
            assertThat(stores.attempts().listByLearner("lrn_two")).isEmpty();
        }

        @Test
        void listsOnlyTheOpenSessionsOfALearner() {
            Stores stores = MemoryStores.create();
            Session open = Session.open("lrn_one", "all", d("2024-10-21"));
            Session shut = Session.open("lrn_one", "core", d("2024-10-21")).close();
            stores.sessions().put(open);
            stores.sessions().put(shut);
            assertThat(stores.sessions().listOpen("lrn_one")).hasSize(1);
            assertThat(stores.sessions().listOpen("lrn_two")).isEmpty();
            assertThat(stores.sessions().get(open.id()).map(Session::deckKey)).contains("all");
        }

        @Test
        void replacesAnEnrolmentRatherThanAddingASecondOne() {
            Stores stores = MemoryStores.create();
            stores.courses().put(MICHAELMAS);
            stores.courses().putEnrolment(Enrolment.of(MICHAELMAS, EnrolmentInput.of("lrn_one")));
            stores.courses()
                    .putEnrolment(Enrolment.of(
                            MICHAELMAS,
                            EnrolmentInput.builder("lrn_one")
                                    .role(EnrolmentRole.AUDITOR)
                                    .build()));
            List<Enrolment> found = stores.courses().enrolments("latin-1a");
            assertThat(found).hasSize(1);
            assertThat(found.get(0).role()).isEqualTo(EnrolmentRole.AUDITOR);
        }

        @Test
        void replacesAnAssignmentUnderTheSameKey() {
            Stores stores = MemoryStores.create();
            stores.courses().putAssignment(weekThree(3));
            stores.courses().putAssignment(weekThree(5));
            List<Assignment> found = stores.courses().assignments("latin-1a");
            assertThat(found).hasSize(1);
            assertThat(found.get(0).target().practiceDays()).contains(5);
        }

        /**
         * The listing is a snapshot: it refuses to be changed, so the store cannot be changed
         * through it, and a later write to the store does not reach it.
         */
        @Test
        void handsOutACopyOfTheEnrolmentList() {
            Stores stores = MemoryStores.create();
            stores.courses().putEnrolment(Enrolment.of(MICHAELMAS, EnrolmentInput.of("lrn_one")));
            List<Enrolment> found = stores.courses().enrolments("latin-1a");
            assertThatThrownBy(found::removeLast).isInstanceOf(UnsupportedOperationException.class);
            assertThat(stores.courses().enrolments("latin-1a")).hasSize(1);
            stores.courses().putEnrolment(Enrolment.of(MICHAELMAS, EnrolmentInput.of("lrn_two")));
            assertThat(found).hasSize(1);
        }

        @Test
        void listsCoursesInKeyOrder() {
            Stores stores = MemoryStores.create();
            stores.courses().put(MICHAELMAS);
            assertThat(stores.courses().list()).extracting(Course::key).containsExactly("latin-1a");
            assertThat(stores.courses().get("nowhere")).isEmpty();
        }
    }
}
