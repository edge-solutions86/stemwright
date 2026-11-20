package io.github.rkaushikdev.stemwright.domain.drill;

import static io.github.rkaushikdev.stemwright.support.Problems.problemMessages;
import static io.github.rkaushikdev.stemwright.support.Profiles.LATIN_NOUN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.IdKind;
import io.github.rkaushikdev.stemwright.domain.Identifiers;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DeckTest {

    private static final Deck FIRST_YEAR = build(DeckInput.builder("first-year")
            .label("First year nouns")
            .scope(scope().classKeys("first-declension", "second-declension")
                    .tags("core")
                    .cells("number=sg")
                    .build())
            .toleranceLevel("standard")
            .note("the nouns from the first six chapters")
            .build());

    private static Deck build(DeckInput input) {
        return Deck.of(LATIN_NOUN, input);
    }

    private static Deck build(String key, DeckScopeInput scope) {
        return build(DeckInput.builder(key).scope(scope).build());
    }

    private static DeckScopeInput.Builder scope() {
        return DeckScopeInput.builder();
    }

    @Nested
    class Of {

        @Test
        void keepsTheKeyLabelAndLanguage() {
            assertThat(FIRST_YEAR.key()).isEqualTo("first-year");
            assertThat(FIRST_YEAR.label()).isEqualTo("First year nouns");
            assertThat(FIRST_YEAR.language()).isEqualTo("latin");
            assertThat(Identifiers.isId(IdKind.DECK, FIRST_YEAR.id())).isTrue();
        }

        @Test
        void derivesAStableIdFromTheLanguageAndTheKey() {
            assertThat(FIRST_YEAR.id()).isEqualTo(Deck.idFor("latin", "first-year"));
        }

        @Test
        void defaultsTheLabelToTheKey() {
            assertThat(build(DeckInput.of("plain")).label()).isEqualTo("plain");
        }

        @Test
        void parsesTheCellSelector() {
            assertThat(FIRST_YEAR.scope().cells().format()).isEqualTo("number=sg");
        }

        @Test
        void leavesTheToleranceLevelAbsentWhenNoneIsAskedFor() {
            assertThat(build(DeckInput.of("plain")).toleranceLevel()).isEmpty();
        }

        @Test
        void refusesARepeatedClassTagOrLexeme() {
            assertThat(problemMessages(
                            () -> build("x", scope().classKeys("a", "a").build())))
                    .containsExactly("scope.classKeys names the same class twice");
            assertThat(problemMessages(() -> build("x", scope().tags("a", "a").build())))
                    .containsExactly("scope.tags names the same tag twice");
            assertThat(problemMessages(
                            () -> build("x", scope().lexemeIds("lex_a", "lex_a").build())))
                    .containsExactly("scope.lexemeIds names the same lexeme twice");
        }

        @Test
        void refusesACellSelectorTheSchemaRejects() {
            assertThat(problemMessages(
                            () -> build("x", scope().cells("tense=pres").build())))
                    .containsExactly("scope.cells tense is not a dimension of latin");
        }

        @Test
        void refusesAKeyThatIsNotASlug() {
            assertThatThrownBy(() -> build(DeckInput.of("First Year"))).hasMessageContaining("not a slug");
        }
    }

    @Nested
    class IsEmpty {

        @Test
        void isTrueForTheEmptyScope() {
            assertThat(DeckScope.EMPTY.isEmpty()).isTrue();
            assertThat(build(DeckInput.of("plain")).isWholeLanguage()).isTrue();
        }

        @Test
        void isFalseAsSoonAsAnyPartIsSet() {
            assertThat(FIRST_YEAR.scope().isEmpty()).isFalse();
            assertThat(build("x", scope().cells("number=pl").build()).isWholeLanguage())
                    .isFalse();
            assertThat(build("y", scope().tags("core").build()).isWholeLanguage())
                    .isFalse();
        }
    }

    @Nested
    class Describe {

        @Test
        void saysSoWhenNothingIsConstrained() {
            assertThat(DeckScope.EMPTY.describe()).isEqualTo("everything in the language");
        }

        @Test
        void readsEveryPartOfTheScopeOut() {
            assertThat(FIRST_YEAR.scope().describe())
                    .isEqualTo("classes first-declension or second-declension, tagged core, cells where number=sg");
        }

        @Test
        void countsNamedLexemesRatherThanListingThem() {
            Deck named =
                    build("x", scope().lexemeIds("lex_aaaaaa", "lex_bbbbbb").build());
            assertThat(named.scope().describe()).isEqualTo("2 named lexemes");
        }
    }

    @Nested
    class WithScope {

        @Test
        void replacesOnlyThePartsItIsGiven() {
            Deck widened =
                    FIRST_YEAR.withScope(LATIN_NOUN, scope().cells("number=pl").build());
            assertThat(widened.scope().cells().format()).isEqualTo("number=pl");
            assertThat(widened.scope().classKeys()).containsExactly("first-declension", "second-declension");
            assertThat(widened.scope().tags()).containsExactly("core");
        }

        @Test
        void keepsTheToleranceLevelAndTheNote() {
            Deck widened =
                    FIRST_YEAR.withScope(LATIN_NOUN, scope().tags("advanced").build());
            assertThat(widened.toleranceLevel()).contains("standard");
            assertThat(widened.note()).contains("the nouns from the first six chapters");
        }

        @Test
        void leavesTheOriginalDeckAlone() {
            FIRST_YEAR.withScope(LATIN_NOUN, scope().tags().build());
            assertThat(FIRST_YEAR.scope().tags()).containsExactly("core");
        }

        @Test
        void keepsTheSameIdBecauseTheKeyDidNotChange() {
            assertThat(FIRST_YEAR.withScope(LATIN_NOUN, scope().tags().build()).id())
                    .isEqualTo(FIRST_YEAR.id());
        }
    }
}
