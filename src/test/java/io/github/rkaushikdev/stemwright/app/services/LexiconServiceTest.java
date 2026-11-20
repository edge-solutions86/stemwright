package io.github.rkaushikdev.stemwright.app.services;

import static io.github.rkaushikdev.stemwright.support.App.seededStores;
import static io.github.rkaushikdev.stemwright.support.App.testHarness;
import static io.github.rkaushikdev.stemwright.support.Drill.PUELLA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.app.DomainEvent.LexemeAdded;
import io.github.rkaushikdev.stemwright.app.RecordingBus;
import io.github.rkaushikdev.stemwright.app.Stores;
import io.github.rkaushikdev.stemwright.domain.course.AccountRole;
import io.github.rkaushikdev.stemwright.domain.course.Actor;
import io.github.rkaushikdev.stemwright.domain.errors.ForbiddenException;
import io.github.rkaushikdev.stemwright.domain.errors.NotFoundException;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.lexicon.Lexeme;
import io.github.rkaushikdev.stemwright.domain.lexicon.LexemeInput;
import io.github.rkaushikdev.stemwright.domain.lexicon.StemInput;
import io.github.rkaushikdev.stemwright.domain.lexicon.StemSet;
import io.github.rkaushikdev.stemwright.support.App.Harness;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LexiconServiceTest {

    private static final Actor TEACHER = new Actor("lrn_teacher", AccountRole.INSTRUCTOR);
    private static final Actor STUDENT = new Actor("lrn_one", AccountRole.LEARNER);

    /** A first declension noun the seeded lexicon does not hold. */
    private static final LexemeInput NAUTA = nauta("first-declension");

    private static LexemeInput nauta(String classKey) {
        return LexemeInput.builder("nauta", classKey, StemSet.of(StemInput.of("base", "naut")))
                .glosses("sailor")
                .tags("core")
                .build();
    }

    private record Built(Stores stores, RecordingBus bus, LexiconService service) {}

    private static Built build() {
        Stores stores = seededStores();
        Harness harness = testHarness();
        return new Built(stores, harness.bus(), new LexiconService(stores, harness.bus(), harness.clock()));
    }

    @Nested
    class Language {

        @Test
        void loadsTheBundleItWasSeededWith() {
            LexiconService service = build().service();
            assertThat(service.language("latin").schema().language()).isEqualTo("latin");
        }

        @Test
        void refusesALanguageNobodyHasConfigured() {
            LexiconService service = build().service();
            assertThatThrownBy(() -> service.language("greek")).isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class LoadAndGet {

        @Test
        void buildsALexiconFromWhatIsStored() {
            LexiconService service = build().service();
            assertThat(service.load("latin").size()).isEqualTo(4);
        }

        @Test
        void readsOneLexemeBack() {
            LexiconService service = build().service();
            assertThat(service.get(PUELLA.id()).lemma()).isEqualTo("puella");
        }

        @Test
        void refusesAnIdNobodyStored() {
            LexiconService service = build().service();
            assertThatThrownBy(() -> service.get("lex_zzzzzz")).isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class Add {

        @Test
        void storesTheLexemeAndAnnouncesIt() {
            Built built = build();
            LexiconService service = built.service();
            Lexeme added = service.add(TEACHER, "latin", NAUTA);
            assertThat(added.lemma()).isEqualTo("nauta");
            assertThat(service.load("latin").size()).isEqualTo(5);
            assertThat(built.bus().ofKind(LexemeAdded.class)).hasSize(1);
            assertThat(built.bus().ofKind(LexemeAdded.class).get(0).lexemeId()).isEqualTo(added.id());
        }

        @Test
        void refusesALearner() {
            Built built = build();
            assertThatThrownBy(() -> built.service().add(STUDENT, "latin", NAUTA))
                    .isInstanceOf(ForbiddenException.class);
            assertThat(built.bus().events()).isEmpty();
        }

        @Test
        void refusesALexemeTheDomainRejectsAndStoresNothing() {
            LexiconService service = build().service();
            assertThatThrownBy(() -> service.add(TEACHER, "latin", nauta("fourth-declension")))
                    .isInstanceOf(ValidationException.class);
            assertThat(service.load("latin").size()).isEqualTo(4);
        }

        @Test
        void refusesALanguageNobodyHasConfigured() {
            LexiconService service = build().service();
            assertThatThrownBy(() -> service.add(TEACHER, "greek", NAUTA)).isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class ReplaceAndRemove {

        @Test
        void replacesAnEntryThatIsThere() {
            LexiconService service = build().service();
            Lexeme changed = service.replace(
                    TEACHER,
                    "latin",
                    PUELLA.id(),
                    LexemeInput.builder("puella", "first-declension", StemSet.of(StemInput.of("base", "puell")))
                            .glosses("girl", "maiden")
                            .build());
            assertThat(changed.glosses()).containsExactly("girl", "maiden");
            assertThat(service.load("latin").size()).isEqualTo(4);
        }

        @Test
        void refusesToReplaceSomethingThatIsNotStored() {
            LexiconService service = build().service();
            assertThatThrownBy(() -> service.replace(TEACHER, "latin", "lex_zzzzzz", NAUTA))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void removesAnEntryAndRefusesOneThatIsNotThere() {
            LexiconService service = build().service();
            service.remove(TEACHER, PUELLA.id());
            assertThat(service.load("latin").size()).isEqualTo(3);
            assertThatThrownBy(() -> service.remove(TEACHER, PUELLA.id())).isInstanceOf(NotFoundException.class);
        }

        @Test
        void refusesALearnerOnBoth() {
            LexiconService service = build().service();
            assertThatThrownBy(() -> service.remove(STUDENT, PUELLA.id())).isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> service.replace(STUDENT, "latin", PUELLA.id(), NAUTA))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Nested
    class Search {

        @Test
        void matchesALemma() {
            LexiconService service = build().service();
            assertThat(service.search("latin", "puel"))
                    .extracting(Lexeme::lemma)
                    .containsExactly("puella");
        }

        @Test
        void matchesAGloss() {
            LexiconService service = build().service();
            assertThat(service.search("latin", "field"))
                    .extracting(Lexeme::lemma)
                    .containsExactly("ager");
        }

        @Test
        void ignoresCaseAndSurroundingSpace() {
            LexiconService service = build().service();
            assertThat(service.search("latin", "  PUELLA ")).hasSize(1);
        }

        @Test
        void givesNothingForABlankTerm() {
            LexiconService service = build().service();
            assertThat(service.search("latin", "   ")).isEmpty();
        }

        @Test
        void givesNothingWhenNothingMatches() {
            LexiconService service = build().service();
            assertThat(service.search("latin", "zzzz")).isEmpty();
        }
    }

    @Nested
    class ByClassAndByTag {

        @Test
        void takesTheDescendantsOfAClassWithIt() {
            LexiconService service = build().service();
            assertThat(service.byClass("latin", "second-declension"))
                    .extracting(Lexeme::lemma)
                    .containsExactly("ager", "bellum");
        }

        @Test
        void filtersByTag() {
            LexiconService service = build().service();
            assertThat(service.byTag("latin", "advanced"))
                    .extracting(Lexeme::lemma)
                    .containsExactly("ager");
            assertThat(service.byTag("latin", "nowhere")).isEmpty();
        }
    }
}
