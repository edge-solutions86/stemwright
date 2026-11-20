package io.github.rkaushikdev.stemwright.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.rkaushikdev.stemwright.app.DomainEvent.AssignmentMet;
import io.github.rkaushikdev.stemwright.app.DomainEvent.AttemptRecorded;
import io.github.rkaushikdev.stemwright.app.DomainEvent.CellSuspended;
import io.github.rkaushikdev.stemwright.app.DomainEvent.LexemeAdded;
import io.github.rkaushikdev.stemwright.app.DomainEvent.ProfilePublished;
import io.github.rkaushikdev.stemwright.app.DomainEvent.SessionClosed;
import io.github.rkaushikdev.stemwright.domain.mastery.Attempt;
import io.github.rkaushikdev.stemwright.domain.mastery.AttemptInput;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EventBusTest {

    private static final Instant AT = Instant.parse("2025-03-03T09:00:00Z");

    private static final LexemeAdded LEXEME_ADDED = new LexemeAdded(AT, "lrn_teacher", "lex_aaaaaa", "latin");

    private static final CellSuspended SUSPENDED = new CellSuspended(AT, "system", "lrn_one", "cell_aaaaaa", 6);

    private static final AttemptRecorded RECORDED = new AttemptRecorded(
            AT,
            "lrn_one",
            Attempt.of(AttemptInput.builder()
                    .learnerId("lrn_one")
                    .sessionId("sess_a")
                    .cellId("cell_aaaaaa")
                    .deckKey("all")
                    .on(d("2025-03-03"))
                    .sequence(1)
                    .given("puellae")
                    .expected("puellae")
                    .verdict("exact")
                    .toleranceLevel("standard")
                    .revisionId("rev_a")
                    .build()));

    private static LocalDate d(String value) {
        return CivilDates.parseCivilDate(value);
    }

    /**
     * Throws a checked exception past the compiler, as a handler written in another JVM language
     * can.
     */
    @SuppressWarnings("unchecked") // erased to Throwable, so nothing checks the cast: that is the point
    private static <T extends Throwable> void sneakyThrow(Throwable failure) throws T {
        throw (T) failure;
    }

    @Nested
    class Create {

        @Test
        void deliversToAHandlerOfTheRightKind() {
            EventBus bus = EventBus.create();
            List<LexemeAdded> calls = new ArrayList<>();
            bus.on(LexemeAdded.class, calls::add);
            bus.publish(LEXEME_ADDED);
            assertThat(calls).containsExactly(LEXEME_ADDED);
        }

        @Test
        void doesNotDeliverToAHandlerOfAnotherKind() {
            EventBus bus = EventBus.create();
            List<CellSuspended> calls = new ArrayList<>();
            bus.on(CellSuspended.class, calls::add);
            bus.publish(LEXEME_ADDED);
            assertThat(calls).isEmpty();
        }

        @Test
        void deliversEverythingToAnOnAnyHandler() {
            EventBus bus = EventBus.create();
            List<DomainEvent> calls = new ArrayList<>();
            bus.onAny(calls::add);
            bus.publish(LEXEME_ADDED);
            bus.publish(SUSPENDED);
            assertThat(calls).hasSize(2);
        }

        @Test
        void deliversToSeveralHandlersOfOneKind() {
            EventBus bus = EventBus.create();
            List<DomainEvent> first = new ArrayList<>();
            List<DomainEvent> second = new ArrayList<>();
            bus.on(LexemeAdded.class, first::add);
            bus.on(LexemeAdded.class, second::add);
            bus.publish(LEXEME_ADDED);
            assertThat(first).hasSize(1);
            assertThat(second).hasSize(1);
        }

        @Test
        void stopsDeliveringOnceUnsubscribed() {
            EventBus bus = EventBus.create();
            List<DomainEvent> calls = new ArrayList<>();
            Subscription subscription = bus.on(LexemeAdded.class, calls::add);
            subscription.unsubscribe();
            bus.publish(LEXEME_ADDED);
            assertThat(calls).isEmpty();
        }

        @Test
        void toleratesUnsubscribingTwice() {
            EventBus bus = EventBus.create();
            Subscription subscription = bus.onAny(event -> {});
            subscription.unsubscribe();
            assertThatCode(subscription::unsubscribe).doesNotThrowAnyException();
        }

        @Test
        void publishesToNobodyWithoutComplaining() {
            assertThat(EventBus.create().publish(LEXEME_ADDED)).isEmpty();
        }
    }

    @Nested
    class AHandlerThatThrows {

        @Test
        void doesNotStopTheOtherHandlers() {
            EventBus bus = EventBus.create();
            List<DomainEvent> second = new ArrayList<>();
            bus.on(LexemeAdded.class, event -> {
                throw new IllegalStateException("subscriber is broken");
            });
            bus.on(LexemeAdded.class, second::add);
            bus.publish(LEXEME_ADDED);
            assertThat(second).hasSize(1);
        }

        @Test
        void comesBackAsACollectedFailureRatherThanAThrow() {
            EventBus bus = EventBus.create();
            bus.on(LexemeAdded.class, event -> {
                throw new IllegalStateException("subscriber is broken");
            });
            List<RuntimeException> failures = bus.publish(LEXEME_ADDED);
            assertThat(failures).hasSize(1);
            assertThat(failures.get(0)).hasMessage("subscriber is broken");
        }

        @Test
        void wrapsAThrownNonError() {
            EventBus bus = EventBus.create();
            Exception checked = new Exception("a string");
            bus.onAny(event -> sneakyThrow(checked));
            List<RuntimeException> failures = bus.publish(LEXEME_ADDED);
            assertThat(failures.get(0)).hasMessage("a string").hasCause(checked);
        }
    }

    @Nested
    class TheRecordingBus {

        @Test
        void keepsEverythingItPublished() {
            RecordingBus bus = RecordingBus.create();
            bus.publish(LEXEME_ADDED);
            bus.publish(SUSPENDED);
            assertThat(bus.events()).hasSize(2);
        }

        @Test
        void filtersByKind() {
            RecordingBus bus = RecordingBus.create();
            bus.publish(LEXEME_ADDED);
            bus.publish(SUSPENDED);
            assertThat(bus.ofKind(CellSuspended.class)).hasSize(1);
            assertThat(bus.ofKind(CellSuspended.class).get(0).cellId()).isEqualTo("cell_aaaaaa");
            assertThat(bus.ofKind(SessionClosed.class)).isEmpty();
        }

        @Test
        void stillDeliversToSubscribers() {
            RecordingBus bus = RecordingBus.create();
            List<DomainEvent> calls = new ArrayList<>();
            bus.on(LexemeAdded.class, calls::add);
            bus.publish(LEXEME_ADDED);
            assertThat(calls).hasSize(1);
        }

        @Test
        void clearsWhatItRecorded() {
            RecordingBus bus = RecordingBus.create();
            bus.publish(LEXEME_ADDED);
            bus.clear();
            assertThat(bus.events()).isEmpty();
        }
    }

    @Nested
    class Describe {

        @Test
        void readsEachKindOut() {
            assertThat(LEXEME_ADDED.describe()).isEqualTo("lrn_teacher added lex_aaaaaa to latin");
            assertThat(SUSPENDED.describe()).isEqualTo("cell_aaaaaa was suspended for lrn_one after 6 lapses");
            assertThat(RECORDED.describe()).isEqualTo("lrn_one answered cell_aaaaaa exact");
        }

        @Test
        void readsTheRemainingKindsOut() {
            assertThat(new ProfilePublished(AT, "lrn_teacher", "first-declension", "rev_a", d("2025-01-15")).describe())
                    .isEqualTo("lrn_teacher published first-declension from 2025-01-15");
            assertThat(new SessionClosed(AT, "lrn_one", "sess_a", "lrn_one", 12).describe())
                    .isEqualTo("lrn_one closed a session of 12 answers");
            assertThat(new AssignmentMet(AT, "lrn_one", "week-3", "lrn_one", d("2025-03-03")).describe())
                    .isEqualTo("lrn_one met week-3 on 2025-03-03");
        }
    }
}
