package io.github.rkaushikdev.stemwright.app;

import static io.github.rkaushikdev.stemwright.support.App.seededStores;
import static io.github.rkaushikdev.stemwright.support.Course.MICHAELMAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.course.CourseScope;
import io.github.rkaushikdev.stemwright.domain.course.Enrolment;
import io.github.rkaushikdev.stemwright.domain.course.EnrolmentInput;
import io.github.rkaushikdev.stemwright.domain.drill.Deck;
import io.github.rkaushikdev.stemwright.domain.drill.DeckInput;
import io.github.rkaushikdev.stemwright.domain.drill.DrillContext;
import io.github.rkaushikdev.stemwright.domain.errors.NotFoundException;
import io.github.rkaushikdev.stemwright.infra.MemoryStores;
import io.github.rkaushikdev.stemwright.support.Profiles;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LoadersTest {

    @Nested
    class RequireLanguage {

        @Test
        void loadsABundleThatIsThere() {
            Stores stores = seededStores();
            assertThat(Loaders.requireLanguage(stores, "latin").language()).isEqualTo("latin");
        }

        @Test
        void namesTheLanguageItCouldNotFind() {
            Stores stores = seededStores();
            assertThatThrownBy(() -> Loaders.requireLanguage(stores, "greek"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("language greek was not found");
        }
    }

    @Nested
    class RequireBook {

        @Test
        void loadsABookThatIsThere() {
            Stores stores = seededStores();
            assertThat(Loaders.requireBook(stores, "latin").language()).isEqualTo("latin");
        }

        @Test
        void namesTheBookItCouldNotFind() {
            Stores stores = MemoryStores.create();
            assertThatThrownBy(() -> Loaders.requireBook(stores, "latin"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("profile book latin was not found");
        }
    }

    @Nested
    class RequireDeck {

        @Test
        void loadsADeckThatIsThere() {
            Stores stores = seededStores();
            stores.decks().put(Deck.of(Profiles.LATIN_NOUN, DeckInput.of("all")));
            assertThat(Loaders.requireDeck(stores, "latin", "all").key()).isEqualTo("all");
        }

        @Test
        void namesTheLanguageAndTheKeyItCouldNotFind() {
            Stores stores = seededStores();
            assertThatThrownBy(() -> Loaders.requireDeck(stores, "latin", "nowhere"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("deck latin/nowhere was not found");
        }
    }

    @Nested
    class RequireCourseAndRequireScope {

        @Test
        void loadsACourseAndBuildsAScopeWithItsRoster() {
            Stores stores = seededStores();
            stores.courses().put(MICHAELMAS);
            stores.courses().putEnrolment(Enrolment.of(MICHAELMAS, EnrolmentInput.of("lrn_one")));
            assertThat(Loaders.requireCourse(stores, "latin-1a").key()).isEqualTo("latin-1a");
            CourseScope scope = Loaders.requireScope(stores, "latin-1a");
            assertThat(scope.roster().enrolments()).hasSize(1);
        }

        @Test
        void namesTheCourseItCouldNotFind() {
            Stores stores = seededStores();
            assertThatThrownBy(() -> Loaders.requireCourse(stores, "nowhere")).isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> Loaders.requireScope(stores, "nowhere")).isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class LoadContext {

        @Test
        void assemblesTheSchemaClassesBookAndLexicon() {
            Stores stores = seededStores();
            DrillContext context = Loaders.loadContext(stores, "latin");
            assertThat(context.schema().language()).isEqualTo("latin");
            assertThat(context.classes().language()).isEqualTo("latin");
            assertThat(context.book().histories()).hasSize(2);
            assertThat(context.lexicon().byId()).hasSize(4);
        }

        @Test
        void failsOnTheLanguageBeforeItReachesTheBook() {
            Stores stores = MemoryStores.create();
            assertThatThrownBy(() -> Loaders.loadContext(stores, "latin"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("language latin was not found");
        }

        @Test
        void failsOnTheBookWhenTheLanguageIsConfiguredWithoutOne() {
            Stores stores = seededStores();
            Stores bare = MemoryStores.create();
            bare.languages().put(Loaders.requireLanguage(stores, "latin"));
            assertThatThrownBy(() -> Loaders.loadContext(bare, "latin"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("profile book");
        }
    }
}
