package io.github.rkaushikdev.stemwright.domain.paradigm;

import static io.github.rkaushikdev.stemwright.support.Profiles.FIRST_DECLENSION;
import static io.github.rkaushikdev.stemwright.support.Profiles.LATIN_NOUN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.errors.NotFoundException;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import io.github.rkaushikdev.stemwright.domain.time.DayRange;
import io.github.rkaushikdev.stemwright.support.Problems;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RevisionHistoryTest {

    private static final ParadigmProfile REVISED =
            FIRST_DECLENSION.withSlot(LATIN_NOUN, ParadigmSlotInput.of("gen.sg", "ai"));

    private static final RevisionHistory HISTORY = RevisionHistory.of(
            "first-declension",
            List.of(
                    ProfileRevisionInput.of(d("2024-09-01"), FIRST_DECLENSION).withReason("first published"),
                    ProfileRevisionInput.of(d("2025-01-15"), REVISED)
                            .withSyncretism(SyncretismMap.of(
                                    LATIN_NOUN, List.of(SyncretismGroupInput.of("is", "dat.pl", "abl.pl"))))
                            .withReason("the archaic genitive was added for the epic reading list")));

    private static LocalDate d(String value) {
        return CivilDates.parseCivilDate(value);
    }

    @Nested
    class Of {

        @Test
        void numbersRevisionsFromOneInDateOrder() {
            assertThat(HISTORY.revisions()).extracting(ProfileRevision::ordinal).containsExactly(1, 2);
        }

        @Test
        void sortsRevisionsGivenOutOfOrder() {
            RevisionHistory reversed = RevisionHistory.of(
                    "first-declension",
                    List.of(
                            ProfileRevisionInput.of(d("2025-01-15"), REVISED),
                            ProfileRevisionInput.of(d("2024-09-01"), FIRST_DECLENSION)));
            assertThat(reversed.revisions())
                    .first()
                    .extracting(ProfileRevision::effectiveFrom)
                    .isEqualTo(d("2024-09-01"));
        }

        @Test
        void derivesAStableIdFromTheProfileKeyAndTheOrdinal() {
            assertThat(HISTORY.revisions())
                    .first()
                    .extracting(ProfileRevision::id)
                    .isEqualTo(ProfileRevision.idFor("first-declension", 1));
        }

        @Test
        void defaultsTheSyncretismMapToNothingDeclared() {
            assertThat(HISTORY.revisions().get(0).syncretism().groups()).isEmpty();
        }

        @Test
        void refusesAnEmptyHistory() {
            assertThat(Problems.problemMessages(() -> RevisionHistory.of("x", List.of())))
                    .containsExactly("revisions must hold at least one revision");
        }

        @Test
        void refusesTwoRevisionsStartingOnTheSameDay() {
            List<ProfileRevisionInput> inputs = List.of(
                    ProfileRevisionInput.of(d("2024-09-01"), FIRST_DECLENSION),
                    ProfileRevisionInput.of(d("2024-09-01"), REVISED));
            assertThat(Problems.problemMessages(() -> RevisionHistory.of("first-declension", inputs)))
                    .containsExactly("revisions.1 starts on 2024-09-01 and so does the one before it");
        }

        @Test
        void refusesARevisionCarryingAnotherProfile() {
            ParadigmProfile other = ParadigmProfile.of(
                    LATIN_NOUN, ParadigmProfileInput.of("second-neuter", "x", ParadigmSlotInput.of("nom.sg", "um")));
            List<ProfileRevisionInput> inputs = List.of(ProfileRevisionInput.of(d("2024-09-01"), other));
            assertThat(Problems.problemMessages(() -> RevisionHistory.of("first-declension", inputs)))
                    .containsExactly("revisions.0 carries profile second-neuter in the history of first-declension");
        }
    }

    @Nested
    class RevisionInForce {

        @Test
        void givesNothingBeforeTheFirstRevisionStarts() {
            assertThat(HISTORY.revisionInForce(d("2024-08-31"))).isEmpty();
            assertThatThrownBy(() -> HISTORY.requireRevisionInForce(d("2024-08-31")))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void takesEffectOnTheStartDayItself() {
            assertThat(HISTORY.revisionInForce(d("2024-09-01")).map(ProfileRevision::ordinal))
                    .contains(1);
        }

        @Test
        void staysInForceUntilTheDayBeforeTheNextOne() {
            assertThat(HISTORY.revisionInForce(d("2025-01-14")).map(ProfileRevision::ordinal))
                    .contains(1);
            assertThat(HISTORY.revisionInForce(d("2025-01-15")).map(ProfileRevision::ordinal))
                    .contains(2);
        }

        @Test
        void keepsTheNewestOneInForceIndefinitely() {
            assertThat(HISTORY.revisionInForce(d("2030-06-01")).map(ProfileRevision::ordinal))
                    .contains(2);
        }

        @Test
        void resolvesTheSyncretismMapOfTheDayNotTheNewestOne() {
            assertThat(HISTORY.revisionInForce(d("2024-12-01")))
                    .hasValueSatisfying(revision ->
                            assertThat(revision.syncretism().groups()).isEmpty());
            assertThat(HISTORY.revisionInForce(d("2025-02-01")))
                    .hasValueSatisfying(revision ->
                            assertThat(revision.syncretism().groups()).hasSize(1));
        }
    }

    @Nested
    class Lookups {

        @Test
        void findsTheFirstAndTheLatest() {
            assertThat(HISTORY.firstRevision().ordinal()).isEqualTo(1);
            assertThat(HISTORY.latestRevision().ordinal()).isEqualTo(2);
        }

        @Test
        void findsByOrdinalAndById() {
            assertThat(HISTORY.revisionByOrdinal(2).map(ProfileRevision::effectiveFrom))
                    .contains(d("2025-01-15"));
            assertThat(HISTORY.revisionByOrdinal(3)).isEmpty();
            assertThat(HISTORY.revisionById(ProfileRevision.idFor("first-declension", 1))
                            .map(ProfileRevision::ordinal))
                    .contains(1);
            assertThat(HISTORY.revisionById("rev_zzzzzz")).isEmpty();
        }

        @Test
        void reportsWhenARevisionWasRetired() {
            assertThat(HISTORY.retiredOn(1)).contains(d("2025-01-15"));
            assertThat(HISTORY.retiredOn(2)).isEmpty();
        }
    }

    @Nested
    class RevisionsAcross {

        @Test
        void namesOneRevisionForARangeInsideIt() {
            DayRange range = DayRange.of(d("2024-10-01"), d("2024-10-31"));
            assertThat(HISTORY.revisionsAcross(range))
                    .extracting(ProfileRevision::ordinal)
                    .containsExactly(1);
            assertThat(HISTORY.spansARevisionChange(range)).isFalse();
        }

        @Test
        void namesBothWhenTheRangeCrossesTheChange() {
            DayRange range = DayRange.of(d("2025-01-10"), d("2025-01-20"));
            assertThat(HISTORY.revisionsAcross(range))
                    .extracting(ProfileRevision::ordinal)
                    .containsExactly(1, 2);
            assertThat(HISTORY.spansARevisionChange(range)).isTrue();
        }

        @Test
        void namesNothingForARangeBeforePublication() {
            DayRange range = DayRange.of(d("2024-01-01"), d("2024-08-31"));
            assertThat(HISTORY.revisionsAcross(range)).isEmpty();
            assertThat(HISTORY.spansARevisionChange(range)).isFalse();
        }
    }

    @Nested
    class WithRevision {

        @Test
        void appendsAndRenumbers() {
            RevisionHistory grown = HISTORY.withRevision(ProfileRevisionInput.of(d("2025-09-01"), FIRST_DECLENSION)
                    .withReason("reverted the archaic genitive"));
            assertThat(grown.revisions()).hasSize(3);
            assertThat(grown.latestRevision().ordinal()).isEqualTo(3);
        }

        @Test
        void leavesTheOriginalHistoryAlone() {
            HISTORY.withRevision(ProfileRevisionInput.of(d("2025-09-01"), FIRST_DECLENSION));
            assertThat(HISTORY.revisions()).hasSize(2);
        }

        @Test
        void insertsInTheMiddleAndRenumbersWhatFollows() {
            RevisionHistory grown = HISTORY.withRevision(ProfileRevisionInput.of(d("2024-11-01"), FIRST_DECLENSION));
            assertThat(grown.revisions())
                    .extracting(ProfileRevision::effectiveFrom)
                    .containsExactly(d("2024-09-01"), d("2024-11-01"), d("2025-01-15"));
            assertThat(grown.revisionInForce(d("2024-12-01")).map(ProfileRevision::ordinal))
                    .contains(2);
        }

        @Test
        void refusesAStartDayThatIsAlreadyTaken() {
            assertThat(Problems.problemMessages(
                            () -> HISTORY.withRevision(ProfileRevisionInput.of(d("2025-01-15"), REVISED))))
                    .hasSize(1);
        }
    }
}
