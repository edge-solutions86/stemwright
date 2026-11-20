package io.github.rkaushikdev.stemwright.domain.lexicon;

import static io.github.rkaushikdev.stemwright.support.Lexemes.AGER;
import static io.github.rkaushikdev.stemwright.support.Lexemes.BELLUM;
import static io.github.rkaushikdev.stemwright.support.Lexemes.LATIN_FULL_SCHEMA;
import static io.github.rkaushikdev.stemwright.support.Lexemes.PUELLA;
import static io.github.rkaushikdev.stemwright.support.Lexemes.makeLexeme;
import static io.github.rkaushikdev.stemwright.support.TestMaps.ordered;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.IdKind;
import io.github.rkaushikdev.stemwright.domain.Identifiers;
import io.github.rkaushikdev.stemwright.domain.features.FeatureBundle;
import io.github.rkaushikdev.stemwright.support.Problems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LexemeTest {

    private static StemSet baseStem(String form) {
        return StemSet.of(StemInput.of("base", form));
    }

    @Nested
    class Of {

        @Test
        void keepsTheLemmaClassAndGlosses() {
            assertThat(PUELLA.lemma()).isEqualTo("puella");
            assertThat(PUELLA.classKey()).isEqualTo("first-declension");
            assertThat(PUELLA.glosses()).containsExactly("girl");
            assertThat(Identifiers.isId(IdKind.LEXEME, PUELLA.id())).isTrue();
        }

        @Test
        void defaultsTheHomographIndexToOne() {
            assertThat(PUELLA.homograph()).isEqualTo(1);
        }

        @Test
        void takesTheLanguageFromTheClassRegistry() {
            assertThat(PUELLA.language()).isEqualTo("latin");
        }

        @Test
        void refusesAClassTheRegistryDoesNotHave() {
            LexemeInput input = LexemeInput.of("nauta", "fourth-declension", baseStem("naut"));
            assertThat(Problems.problemMessages(() -> makeLexeme(input)))
                    .containsExactly("classKey is not a class of latin: fourth-declension");
        }

        @Test
        void refusesAnInherentFeatureTheSchemaDoesNotKnow() {
            LexemeInput input = LexemeInput.builder("nauta", "first-declension", baseStem("naut"))
                    .inherent(Map.of("tense", "pres"))
                    .build();
            assertThat(Problems.problemMessages(() -> makeLexeme(input)))
                    .containsExactly("inherent.tense is not a dimension of latin");
        }

        @Test
        void refusesAHomographIndexBelowOne() {
            LexemeInput input = LexemeInput.builder("malus", "second-declension", baseStem("mal"))
                    .homograph(0)
                    .build();
            assertThat(Problems.problemMessages(() -> makeLexeme(input)))
                    .containsExactly("homograph must be a positive whole number: 0");
        }

        @Test
        void refusesARepeatedTag() {
            LexemeInput input = LexemeInput.builder("nauta", "first-declension", baseStem("naut"))
                    .tags("core", "core")
                    .build();
            assertThat(Problems.problemMessages(() -> makeLexeme(input)))
                    .containsExactly("tags holds the same tag twice");
        }

        @Test
        void refusesABlankLemma() {
            assertThatThrownBy(() -> makeLexeme(LexemeInput.of("   ", "first-declension", baseStem("x"))))
                    .hasMessageContaining("lemma must not be blank");
        }
    }

    @Nested
    class IdFor {

        @Test
        void isStableForTheSameLemmaAndHomograph() {
            assertThat(Lexeme.idFor("latin", "puella", 1)).isEqualTo(Lexeme.idFor("latin", "puella", 1));
            assertThat(Lexeme.idFor("latin", "puella", 1)).isEqualTo(PUELLA.id());
        }

        @Test
        void ignoresCaseAndSurroundingSpaceInTheLemma() {
            assertThat(Lexeme.idFor("latin", "  PUELLA ", 1)).isEqualTo(Lexeme.idFor("latin", "puella", 1));
        }

        @Test
        void separatesHomographsAndLanguages() {
            assertThat(Lexeme.idFor("latin", "malus", 1)).isNotEqualTo(Lexeme.idFor("latin", "malus", 2));
            assertThat(Lexeme.idFor("latin", "malus", 1)).isNotEqualTo(Lexeme.idFor("greek", "malus", 1));
        }
    }

    @Nested
    class CitationForm {

        @Test
        void isTheBareLemmaForTheFirstHomograph() {
            assertThat(PUELLA.citationForm()).isEqualTo("puella");
        }

        @Test
        void marksALaterHomograph() {
            Lexeme second = makeLexeme(LexemeInput.builder("malus", "second-declension", baseStem("mal"))
                    .homograph(2)
                    .glosses("apple tree")
                    .build());
            assertThat(second.citationForm()).isEqualTo("malus (2)");
        }
    }

    @Nested
    class GlossesAndTags {

        @Test
        void readsTheFirstGlossAsThePrimaryOne() {
            assertThat(BELLUM.primaryGloss()).contains("war");
            assertThat(AGER.primaryGloss()).contains("field");
        }

        @Test
        void givesNothingWhenThereAreNoGlosses() {
            Lexeme bare = makeLexeme(LexemeInput.of("res", "third-declension", baseStem("r")));
            assertThat(bare.primaryGloss()).isEmpty();
        }

        @Test
        void answersWhetherATagIsPresent() {
            assertThat(PUELLA.hasTag("core")).isTrue();
            assertThat(PUELLA.hasTag("rare")).isFalse();
        }
    }

    @Nested
    class ContradictsInherent {

        @Test
        void isFalseWhenTheBundleSaysNothingAboutTheInherentDimension() {
            FeatureBundle bundle = FeatureBundle.of(LATIN_FULL_SCHEMA, ordered("case", "gen", "number", "sg"));
            assertThat(PUELLA.contradictsInherent(bundle)).isFalse();
        }

        @Test
        void isFalseWhenTheBundleAgrees() {
            FeatureBundle bundle = FeatureBundle.of(LATIN_FULL_SCHEMA, ordered("case", "gen", "gender", "fem"));
            assertThat(PUELLA.contradictsInherent(bundle)).isFalse();
        }

        @Test
        void isTrueWhenTheBundleAsksForAnotherValue() {
            FeatureBundle bundle = FeatureBundle.of(LATIN_FULL_SCHEMA, ordered("case", "gen", "gender", "masc"));
            assertThat(PUELLA.contradictsInherent(bundle)).isTrue();
        }

        @Test
        void isNeverTrueForALexemeWithNoInherentFeatures() {
            Lexeme bare = makeLexeme(LexemeInput.of("res", "third-declension", baseStem("r")));
            FeatureBundle bundle = FeatureBundle.of(LATIN_FULL_SCHEMA, ordered("gender", "neut"));
            assertThat(bare.contradictsInherent(bundle)).isFalse();
        }
    }

    @Nested
    class ComparisonHelpers {

        @Test
        void readsTheInherentKeyBack() {
            assertThat(PUELLA.inherentKey()).isEqualTo("gender=fem");
        }

        @Test
        void matchesLemmasWithoutRegardToCase() {
            Lexeme shouted = makeLexeme(LexemeInput.of("PUELLA", "first-declension", baseStem("puell")));
            assertThat(PUELLA.sameLemma(shouted)).isTrue();
            assertThat(PUELLA.sameLemma(BELLUM)).isFalse();
        }

        @Test
        void sortsByLemmaThenHomograph() {
            List<Lexeme> entries = new ArrayList<>(List.of(BELLUM, PUELLA, AGER));
            entries.sort(Lexeme::compare);
            assertThat(entries.stream().map(Lexeme::lemma)).containsExactly("ager", "bellum", "puella");
        }
    }
}
