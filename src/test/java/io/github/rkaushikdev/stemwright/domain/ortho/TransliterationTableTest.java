package io.github.rkaushikdev.stemwright.domain.ortho;

import static io.github.rkaushikdev.stemwright.domain.ortho.TransliterationTable.GREEK_BETA_CODE;
import static io.github.rkaushikdev.stemwright.domain.ortho.TransliterationTable.NONE;
import static io.github.rkaushikdev.stemwright.support.Problems.problemMessages;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.text.Normalise;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TransliterationTableTest {

    @Nested
    class Of {

        @Test
        void sortsTheRulesLongestFirst() {
            TransliterationTable table = TransliterationTable.of(
                    "x", List.of(new TransliterationRule("t", "τ"), new TransliterationRule("th", "θ")));
            assertThat(table.rules().get(0).from()).isEqualTo("th");
        }

        @Test
        void lowerCasesAndTrimsTheSourceSide() {
            TransliterationTable table = TransliterationTable.of("x", List.of(new TransliterationRule("  TH  ", "θ")));
            assertThat(table.rules().get(0).from()).isEqualTo("th");
        }

        @Test
        void refusesABlankSource() {
            assertThat(problemMessages(
                            () -> TransliterationTable.of("x", List.of(new TransliterationRule("   ", "θ")))))
                    .containsExactly("rules.0.from must not be blank");
        }

        @Test
        void refusesTheSameSourceMappedTwice() {
            assertThat(problemMessages(() -> TransliterationTable.of(
                            "x", List.of(new TransliterationRule("th", "θ"), new TransliterationRule("TH", "τ")))))
                    .containsExactly("rules.1.from is mapped twice: th");
        }

        @Test
        void buildsAnEmptyTable() {
            assertThat(TransliterationTable.of("x", List.of()).rules()).isEmpty();
        }
    }

    @Nested
    class Transliterate {

        @Test
        void mapsASingleLetter() {
            assertThat(GREEK_BETA_CODE.transliterate("a")).isEqualTo("α");
        }

        @Test
        void prefersALongerMatch() {
            assertThat(GREEK_BETA_CODE.transliterate("th")).isEqualTo("θ");
            assertThat(GREEK_BETA_CODE.transliterate("ht")).isEqualTo("ητ");
        }

        @Test
        void mapsAWholeWord() {
            assertThat(GREEK_BETA_CODE.transliterate("anthrwpos")).isEqualTo("ανθρωποσ");
        }

        /**
         * The table has one sigma and it is the medial one. Which shape a sigma takes at the end of a
         * word is a spelling question, and the fold that already answers it is the right place for it
         * rather than a second rule here that would have to know where the word ends.
         */
        @Test
        void leavesTheFinalSigmaToTheFoldingStep() {
            assertThat(GREEK_BETA_CODE.transliterate("logos")).endsWith("σ");
        }

        @Test
        void leavesACharacterItHasNoRuleForAlone() {
            assertThat(GREEK_BETA_CODE.transliterate("a1")).isEqualTo("α1");
        }

        @Test
        void ignoresCaseAndSurroundingSpace() {
            assertThat(GREEK_BETA_CODE.transliterate("  LOGOS ")).isEqualTo("λογοσ");
        }

        @Test
        void changesNothingUnderAnEmptyTable() {
            assertThat(NONE.transliterate("anthrwpos")).isEqualTo("anthrwpos");
        }

        @Test
        void mapsAnEmptyStringToAnEmptyString() {
            assertThat(GREEK_BETA_CODE.transliterate("")).isEmpty();
        }
    }

    @Nested
    class MatchesTransliterated {

        @Test
        void acceptsATransliteratedAnswer() {
            assertThat(GREEK_BETA_CODE.matchesTransliterated("λογος", "logos")).isFalse();
            assertThat(GREEK_BETA_CODE.matchesTransliterated("λογοσ", "logos")).isTrue();
        }

        @Test
        void ignoresAccentsOnTheExpectedForm() {
            assertThat(GREEK_BETA_CODE.matchesTransliterated("λόγοσ", "logos")).isTrue();
        }

        @Test
        void refusesAnAnswerThatTransliteratesToSomethingElse() {
            assertThat(GREEK_BETA_CODE.matchesTransliterated("λογοσ", "nomos")).isFalse();
        }

        @Test
        void isAlwaysFalseUnderAnEmptyTable() {
            assertThat(NONE.matchesTransliterated("logos", "logos")).isFalse();
        }
    }

    @Nested
    class IsTransliterable {

        @Test
        void isTrueWhenEveryLetterHasARule() {
            assertThat(GREEK_BETA_CODE.isTransliterable("logos")).isTrue();
        }

        @Test
        void isFalseWhenSomethingHasNone() {
            assertThat(GREEK_BETA_CODE.isTransliterable("log0s")).isFalse();
        }

        @Test
        void isFalseForAnEmptyString() {
            assertThat(GREEK_BETA_CODE.isTransliterable("")).isFalse();
        }
    }

    @Nested
    class RequireRule {

        @Test
        void findsARuleAndRefusesOneThatIsNotThere() {
            assertThat(GREEK_BETA_CODE.requireRule("th").to()).isEqualTo("θ");
            assertThatThrownBy(() -> GREEK_BETA_CODE.requireRule("q"))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("not in the greek table");
        }
    }

    @Nested
    class TheGreekTable {

        @Test
        void coversEveryLetterOfTheAlphabetAFirstYearNeeds() {
            for (String letter : Normalise.codePoints("abgdezhiklmnoprstuw")) {
                assertThat(GREEK_BETA_CODE.isTransliterable(letter)).isTrue();
            }
        }

        @Test
        void mapsTheDigraphsABetaCodeReaderExpects() {
            assertThat(GREEK_BETA_CODE.transliterate("ph")).isEqualTo("φ");
            assertThat(GREEK_BETA_CODE.transliterate("ch")).isEqualTo("χ");
            assertThat(GREEK_BETA_CODE.transliterate("ps")).isEqualTo("ψ");
            assertThat(GREEK_BETA_CODE.transliterate("ks")).isEqualTo("ξ");
        }
    }
}
