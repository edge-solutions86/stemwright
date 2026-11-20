package io.github.rkaushikdev.stemwright.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.app.DomainEvent.AttemptRecorded;
import io.github.rkaushikdev.stemwright.app.DomainEvent.LexemeAdded;
import io.github.rkaushikdev.stemwright.app.DomainEvent.ProfilePublished;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.mastery.Attempt;
import io.github.rkaushikdev.stemwright.domain.mastery.AttemptInput;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import io.github.rkaushikdev.stemwright.domain.time.DayRange;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AuditLogTest {

    private static final Instant AT = Instant.parse("2025-03-03T09:00:00Z");

    private static final LexemeAdded ADDED = new LexemeAdded(AT, "lrn_teacher", "lex_aaaaaa", "latin");

    private static final ProfilePublished PUBLISHED =
            new ProfilePublished(AT, "lrn_teacher", "first-declension", "rev_aaaaaa", d("2025-04-01"));

    private static final AttemptRecorded ANSWERED = new AttemptRecorded(
            AT,
            "lrn_one",
            Attempt.of(AttemptInput.builder()
                    .learnerId("lrn_one")
                    .sessionId("sess_a")
                    .cellId("cell_a")
                    .deckKey("all")
                    .on(d("2025-03-03"))
                    .sequence(1)
                    .given("x")
                    .expected("x")
                    .verdict("exact")
                    .toleranceLevel("standard")
                    .revisionId("rev_a")
                    .build()));

    private static LocalDate d(String value) {
        return CivilDates.parseCivilDate(value);
    }

    private static AuditEntry entryOf(DomainEvent event) {
        return AuditEntry.of(event).orElseThrow();
    }

    @Nested
    class IsAudited {

        @Test
        void recordsEditorialEventsAndNothingElse() {
            assertThat(AuditLog.isAudited(ADDED)).isTrue();
            assertThat(AuditLog.isAudited(PUBLISHED)).isTrue();
            assertThat(AuditLog.isAudited(ANSWERED)).isFalse();
        }
    }

    @Nested
    class ToAuditEntry {

        @Test
        void turnsALexemeAdditionIntoAnEntry() {
            assertThat(AuditEntry.of(ADDED))
                    .contains(new AuditEntry(
                            "2025-03-03T09:00:00.000Z",
                            d("2025-03-03"),
                            "lrn_teacher",
                            "lexeme.added",
                            "lex_aaaaaa",
                            "added to latin"));
        }

        @Test
        void namesTheRevisionAndTheDayItTakesEffect() {
            assertThat(AuditEntry.of(PUBLISHED).map(AuditEntry::detail))
                    .contains("revision rev_aaaaaa takes effect 2025-04-01");
        }

        @Test
        void givesNothingForAnEventItDoesNotAudit() {
            assertThat(AuditEntry.of(ANSWERED)).isEmpty();
        }
    }

    @Nested
    class TheLog {

        @Test
        void recordsWhatItShouldAndSkipsWhatItShouldNot() {
            AuditLog log = AuditLog.create();
            log.record(ADDED);
            log.record(ANSWERED);
            assertThat(log.entries()).hasSize(1);
        }

        @Test
        void handsBackTheEntryItRecorded() {
            assertThat(AuditLog.create().record(ADDED).map(AuditEntry::subject)).contains("lex_aaaaaa");
            assertThat(AuditLog.create().record(ANSWERED)).isEmpty();
        }

        @Test
        void filtersByActorAndBySubject() {
            AuditLog log = AuditLog.create();
            log.record(ADDED);
            log.record(PUBLISHED);
            assertThat(log.byActor("lrn_teacher")).hasSize(2);
            assertThat(log.byActor("lrn_one")).isEmpty();
            assertThat(log.bySubject("first-declension")).hasSize(1);
        }

        @Test
        void filtersByDayRange() {
            AuditLog log = AuditLog.create();
            log.record(ADDED);
            assertThat(log.inRange(DayRange.of(d("2025-03-01"), d("2025-03-05"))))
                    .hasSize(1);
            assertThat(log.inRange(DayRange.of(d("2025-04-01"), d("2025-04-05"))))
                    .isEmpty();
        }

        @Test
        void clearsWhatItHolds() {
            AuditLog log = AuditLog.create();
            log.record(ADDED);
            log.clear();
            assertThat(log.entries()).isEmpty();
        }

        @Test
        void startsFromTheEntriesItWasSeededWith() {
            assertThat(AuditLog.create(List.of(entryOf(ADDED))).entries()).hasSize(1);
        }
    }

    @Nested
    class AuditFrom {

        @Test
        void recordsEventsPublishedOnTheBus() {
            EventBus bus = EventBus.create();
            AuditLog log = AuditLog.create();
            AuditLog.auditFrom(bus, log);
            bus.publish(ADDED);
            bus.publish(ANSWERED);
            assertThat(log.entries()).hasSize(1);
        }

        @Test
        void stopsRecordingOnceDetached() {
            EventBus bus = EventBus.create();
            AuditLog log = AuditLog.create();
            AuditLog.auditFrom(bus, log).unsubscribe();
            bus.publish(ADDED);
            assertThat(log.entries()).isEmpty();
        }
    }

    @Nested
    class FormatAndRequireActor {

        @Test
        void readsAnEntryOut() {
            assertThat(entryOf(ADDED).format())
                    .isEqualTo("2025-03-03T09:00:00.000Z lrn_teacher lexeme.added lex_aaaaaa: added to latin");
        }

        @Test
        void refusesAnEntryWithNoActor() {
            AuditEntry added = entryOf(ADDED);
            AuditEntry entry =
                    new AuditEntry(added.at(), added.on(), "", added.action(), added.subject(), added.detail());
            assertThatThrownBy(entry::requireActor).isInstanceOf(ValidationException.class);
        }
    }
}
