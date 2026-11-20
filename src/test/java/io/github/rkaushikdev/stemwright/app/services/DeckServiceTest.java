package io.github.rkaushikdev.stemwright.app.services;

import static io.github.rkaushikdev.stemwright.support.App.seededStores;
import static io.github.rkaushikdev.stemwright.support.App.testHarness;
import static io.github.rkaushikdev.stemwright.support.Course.MICHAELMAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.app.Stores;
import io.github.rkaushikdev.stemwright.domain.course.AccountRole;
import io.github.rkaushikdev.stemwright.domain.course.Actor;
import io.github.rkaushikdev.stemwright.domain.course.CourseScope;
import io.github.rkaushikdev.stemwright.domain.course.Roster;
import io.github.rkaushikdev.stemwright.domain.drill.Deck;
import io.github.rkaushikdev.stemwright.domain.drill.DeckInput;
import io.github.rkaushikdev.stemwright.domain.drill.DeckScopeInput;
import io.github.rkaushikdev.stemwright.domain.errors.ConflictException;
import io.github.rkaushikdev.stemwright.domain.errors.ForbiddenException;
import io.github.rkaushikdev.stemwright.domain.errors.NotFoundException;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import io.github.rkaushikdev.stemwright.support.App.Harness;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DeckServiceTest {

    private static final Actor TEACHER = new Actor("lrn_teacher", AccountRole.INSTRUCTOR);
    private static final Actor STUDENT = new Actor("lrn_one", AccountRole.LEARNER);
    private static final CourseScope SCOPE = new CourseScope(MICHAELMAS, Roster.of(MICHAELMAS, List.of()));

    /** A selector no bundle satisfies: nominative and not nominative at once. */
    private static final String NOTHING = "case=nom,case!=nom";

    private static LocalDate d(String value) {
        return CivilDates.parseCivilDate(value);
    }

    private static DeckService build() {
        Stores stores = seededStores();
        Harness harness = testHarness();
        DrillService drill = new DrillService(stores, harness.bus(), harness.clock());
        return new DeckService(stores, harness.clock(), drill);
    }

    private static DeckInput selecting(String key, String cells) {
        return DeckInput.builder(key).scope(cellsOnly(cells)).build();
    }

    private static DeckScopeInput cellsOnly(String cells) {
        return DeckScopeInput.builder().cells(cells).build();
    }

    @Nested
    class Create {

        @Test
        void storesADeckThatSelectsSomething() {
            DeckService service = build();
            Deck created = service.create(TEACHER, SCOPE, "latin", selecting("singulars", "number=sg"));
            assertThat(created.key()).isEqualTo("singulars");
            assertThat(service.list("latin")).hasSize(1);
        }

        @Test
        void refusesADeckThatSelectsNothing() {
            DeckService service = build();
            assertThatThrownBy(() -> service.create(TEACHER, SCOPE, "latin", selecting("empty", NOTHING)))
                    .isInstanceOf(ConflictException.class);
            assertThat(service.list("latin")).isEmpty();
        }

        @Test
        void refusesAKeyThatIsAlreadyTaken() {
            DeckService service = build();
            service.create(TEACHER, SCOPE, "latin", DeckInput.of("all"));
            assertThatThrownBy(() -> service.create(TEACHER, SCOPE, "latin", DeckInput.of("all")))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void refusesALearner() {
            DeckService service = build();
            assertThatThrownBy(() -> service.create(STUDENT, SCOPE, "latin", DeckInput.of("all")))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void refusesALanguageNobodyConfigured() {
            DeckService service = build();
            assertThatThrownBy(() -> service.create(TEACHER, SCOPE, "greek", DeckInput.of("all")))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class GetAndList {

        @Test
        void readsADeckBackAndRefusesOneThatIsNotThere() {
            DeckService service = build();
            service.create(TEACHER, SCOPE, "latin", DeckInput.of("all"));
            assertThat(service.get("latin", "all").key()).isEqualTo("all");
            assertThatThrownBy(() -> service.get("latin", "nowhere")).isInstanceOf(NotFoundException.class);
        }

        @Test
        void listsByLanguage() {
            DeckService service = build();
            service.create(TEACHER, SCOPE, "latin", DeckInput.of("all"));
            assertThat(service.list("greek")).isEmpty();
        }
    }

    @Nested
    class Rescope {

        @Test
        void narrowsADeckAndKeepsItSaved() {
            DeckService service = build();
            service.create(TEACHER, SCOPE, "latin", DeckInput.of("all"));
            Deck narrowed = service.rescope(TEACHER, SCOPE, "latin", "all", cellsOnly("number=pl"));
            assertThat(narrowed.scope().cells().format()).isEqualTo("number=pl");
            assertThat(service.get("latin", "all").scope().cells().format()).isEqualTo("number=pl");
        }

        @Test
        void refusesAChangeThatEmptiesTheDeck() {
            DeckService service = build();
            service.create(TEACHER, SCOPE, "latin", DeckInput.of("all"));
            assertThatThrownBy(() -> service.rescope(TEACHER, SCOPE, "latin", "all", cellsOnly(NOTHING)))
                    .isInstanceOf(ConflictException.class);
            assertThat(service.get("latin", "all").scope().cells().format()).isEqualTo("");
        }

        @Test
        void refusesADeckThatIsNotThere() {
            DeckService service = build();
            assertThatThrownBy(() -> service.rescope(TEACHER, SCOPE, "latin", "nowhere", cellsOnly("number=pl")))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void refusesALearner() {
            DeckService service = build();
            service.create(TEACHER, SCOPE, "latin", DeckInput.of("all"));
            assertThatThrownBy(() -> service.rescope(STUDENT, SCOPE, "latin", "all", cellsOnly("number=pl")))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Nested
    class Remove {

        @Test
        void removesADeckAndRefusesOneThatIsNotThere() {
            DeckService service = build();
            service.create(TEACHER, SCOPE, "latin", DeckInput.of("all"));
            service.remove(TEACHER, SCOPE, "latin", "all");
            assertThat(service.list("latin")).isEmpty();
            assertThatThrownBy(() -> service.remove(TEACHER, SCOPE, "latin", "all"))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void refusesALearner() {
            DeckService service = build();
            service.create(TEACHER, SCOPE, "latin", DeckInput.of("all"));
            assertThatThrownBy(() -> service.remove(STUDENT, SCOPE, "latin", "all"))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Nested
    class Preview {

        @Test
        void reportsWhatADeckWouldSelectWithoutSavingIt() {
            DeckService service = build();
            DeckPreview preview = service.preview("latin", DeckInput.of("draft"), d("2024-10-21"));
            assertThat(preview.membership().cells()).hasSize(30);
            assertThat(service.list("latin")).isEmpty();
        }

        @Test
        void carriesTheWarningsTheMembershipProduced() {
            DeckService service = build();
            DeckPreview preview = service.preview("latin", DeckInput.of("draft"), d("2024-10-21"));
            assertThat(preview.warnings()).contains("1 lexemes have no paradigm bound to their class");
        }

        @Test
        void reportsAnEmptySelectionAsAWarningRatherThanAFailure() {
            DeckService service = build();
            DeckPreview preview = service.preview("latin", selecting("draft", NOTHING), d("2024-10-21"));
            assertThat(preview.membership().cells()).isEmpty();
            assertThat(preview.warnings()).contains("the deck selects nothing at all");
        }

        @Test
        void takesTheDayOffTheClockWhenNoneIsGiven() {
            DeckService service = build();
            assertThat(service.preview("latin", DeckInput.of("draft"))
                            .membership()
                            .on())
                    .isEqualTo(d("2024-10-21"));
        }
    }
}
