package io.github.rkaushikdev.stemwright.app.services;

import static io.github.rkaushikdev.stemwright.support.App.seededStores;
import static io.github.rkaushikdev.stemwright.support.App.testHarness;
import static io.github.rkaushikdev.stemwright.support.Profiles.FIRST_DECLENSION;
import static io.github.rkaushikdev.stemwright.support.Profiles.LATIN_NOUN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.app.DomainEvent.ProfilePublished;
import io.github.rkaushikdev.stemwright.app.RecordingBus;
import io.github.rkaushikdev.stemwright.app.Stores;
import io.github.rkaushikdev.stemwright.domain.course.AccountRole;
import io.github.rkaushikdev.stemwright.domain.course.Actor;
import io.github.rkaushikdev.stemwright.domain.errors.ConflictException;
import io.github.rkaushikdev.stemwright.domain.errors.ForbiddenException;
import io.github.rkaushikdev.stemwright.domain.errors.NotFoundException;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.paradigm.ParadigmProfile;
import io.github.rkaushikdev.stemwright.domain.paradigm.ParadigmProfileInput;
import io.github.rkaushikdev.stemwright.domain.paradigm.ParadigmSlotInput;
import io.github.rkaushikdev.stemwright.domain.paradigm.ProfileRevision;
import io.github.rkaushikdev.stemwright.domain.paradigm.ProfileRevisionInput;
import io.github.rkaushikdev.stemwright.domain.paradigm.ResolvedProfile;
import io.github.rkaushikdev.stemwright.domain.paradigm.SyncretismGroupInput;
import io.github.rkaushikdev.stemwright.domain.paradigm.SyncretismMap;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import io.github.rkaushikdev.stemwright.domain.time.DayRange;
import io.github.rkaushikdev.stemwright.support.App.Harness;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProfileServiceTest {

    private static final Actor TEACHER = new Actor("lrn_teacher", AccountRole.INSTRUCTOR);
    private static final Actor STUDENT = new Actor("lrn_one", AccountRole.LEARNER);

    /** The first declension with the archaic genitive singular in ai. */
    private static final ParadigmProfile REVISED =
            FIRST_DECLENSION.withSlot(LATIN_NOUN, ParadigmSlotInput.of("gen.sg", "ai"));

    private static LocalDate d(String value) {
        return CivilDates.parseCivilDate(value);
    }

    private static ProfileRevisionInput revisedFrom(String day) {
        return ProfileRevisionInput.of(d(day), REVISED);
    }

    private record Built(Stores stores, RecordingBus bus, ProfileService service) {}

    private static Built build() {
        Stores stores = seededStores();
        Harness harness = testHarness();
        return new Built(stores, harness.bus(), new ProfileService(stores, harness.bus(), harness.clock()));
    }

    @Nested
    class BookAndHistory {

        @Test
        void loadsTheSeededBook() {
            ProfileService service = build().service();
            assertThat(service.book("latin").histories()).hasSize(2);
        }

        @Test
        void refusesALanguageWithNoBook() {
            ProfileService service = build().service();
            assertThatThrownBy(() -> service.book("greek")).isInstanceOf(NotFoundException.class);
        }

        @Test
        void loadsOneHistoryAndRefusesAnUnknownKey() {
            ProfileService service = build().service();
            assertThat(service.history("latin", "first-declension").revisions()).hasSize(1);
            assertThatThrownBy(() -> service.history("latin", "nowhere")).isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class Resolve {

        @Test
        void findsTheRevisionInForceThroughTheClassChain() {
            ProfileService service = build().service();
            ResolvedProfile resolved = service.resolve("latin", "second-declension.neuter", d("2024-10-21"));
            assertThat(resolved.revision().ordinal()).isEqualTo(1);
            assertThat(resolved.boundThrough()).isEqualTo("second-declension");
        }

        @Test
        void refusesAClassWithNoBinding() {
            ProfileService service = build().service();
            assertThatThrownBy(() -> service.resolve("latin", "third-declension", d("2024-10-21")))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class Publish {

        @Test
        void addsARevisionAndAnnouncesIt() {
            Built built = build();
            ProfileService service = built.service();
            ProfileRevision published = service.publish(
                    TEACHER, "latin", revisedFrom("2025-01-15").withReason("the archaic genitive was added"));
            assertThat(published.ordinal()).isEqualTo(2);
            assertThat(service.history("latin", "first-declension").revisions()).hasSize(2);
            assertThat(built.bus().ofKind(ProfilePublished.class).get(0).effectiveFrom())
                    .isEqualTo(d("2025-01-15"));
        }

        @Test
        void publishesAProfileTheBookDidNotHoldAtAll() {
            ProfileService service = build().service();
            ParadigmProfile fresh = ParadigmProfile.of(
                    LATIN_NOUN,
                    ParadigmProfileInput.of(
                            "third-declension", "third-declension", ParadigmSlotInput.of("nom.sg", "")));
            service.publish(TEACHER, "latin", ProfileRevisionInput.of(d("2025-01-15"), fresh));
            assertThat(service.book("latin").histories()).hasSize(3);
        }

        @Test
        void refusesARevisionThatWouldTakeEffectInThePast() {
            ProfileService service = build().service();
            assertThatThrownBy(() -> service.publish(TEACHER, "latin", revisedFrom("2024-10-01")))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void acceptsARevisionTakingEffectToday() {
            ProfileService service = build().service();
            assertThat(service.publish(TEACHER, "latin", revisedFrom("2024-10-21")))
                    .isNotNull();
        }

        @Test
        void refusesALearner() {
            Built built = build();
            assertThatThrownBy(() -> built.service().publish(STUDENT, "latin", revisedFrom("2025-01-15")))
                    .isInstanceOf(ForbiddenException.class);
            assertThat(built.bus().events()).isEmpty();
        }

        @Test
        void refusesAStartDayAnotherRevisionAlreadyHolds() {
            ProfileService service = build().service();
            service.publish(TEACHER, "latin", revisedFrom("2025-01-15"));
            assertThatThrownBy(() -> service.publish(TEACHER, "latin", revisedFrom("2025-01-15")))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void carriesASyncretismMapOntoTheRevision() {
            ProfileService service = build().service();
            SyncretismMap map =
                    SyncretismMap.of(LATIN_NOUN, List.of(SyncretismGroupInput.of("is", "dat.pl", "abl.pl")));
            ProfileRevision published =
                    service.publish(TEACHER, "latin", revisedFrom("2025-01-15").withSyncretism(map));
            assertThat(published.syncretism().groups()).hasSize(1);
        }
    }

    @Nested
    class Bind {

        @Test
        void bindsAClassAndClosesTheGap() {
            ProfileService service = build().service();
            assertThat(service.gaps("latin")).contains("third-declension");
            service.bind(TEACHER, "latin", "third-declension", "first-declension");
            assertThat(service.gaps("latin")).doesNotContain("third-declension");
        }

        @Test
        void refusesALearner() {
            ProfileService service = build().service();
            assertThatThrownBy(() -> service.bind(STUDENT, "latin", "third-declension", "first-declension"))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Nested
    class Disagreements {

        @Test
        void reportsAPairTheEndingsProduceThatNobodyDeclared() {
            ProfileService service = build().service();
            List<String> found = service.disagreements("latin", "second-neuter");
            assertThat(found).isNotEmpty();
            assertThat(found.get(0)).contains("produced but not declared");
        }

        @Test
        void reportsAPairTheDeclarationMissedEvenWhereMostOfItIsRight() {
            ProfileService service = build().service();
            assertThat(service.disagreements("latin", "first-declension"))
                    .containsExactly("case=nom;number=sg and case=abl;number=sg are produced but not declared");
        }
    }

    @Nested
    class RevisionsIn {

        @Test
        void namesOneRevisionForARangeInsideIt() {
            ProfileService service = build().service();
            DayRange range = DayRange.of(d("2024-10-01"), d("2024-10-31"));
            assertThat(service.revisionsIn("latin", "second-neuter", range)).hasSize(1);
        }

        @Test
        void namesBothWhenTheRangeCrossesTheChange() {
            ProfileService service = build().service();
            DayRange range = DayRange.of(d("2025-01-25"), d("2025-02-05"));
            assertThat(service.revisionsIn("latin", "second-neuter", range)).hasSize(2);
        }
    }
}
