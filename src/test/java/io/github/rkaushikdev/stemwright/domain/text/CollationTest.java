package io.github.rkaushikdev.stemwright.domain.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CollationTest {

    private static List<String> sorted(String... values) {
        List<String> out = new ArrayList<>(List.of(values));
        out.sort(Collation.ORDER);
        return out;
    }

    @Nested
    class Letters {

        @Test
        void putsALowerCaseLetterBeforeItsCapitalAndBothBeforeTheNextLetter() {
            assertThat(sorted("B", "b", "A", "a")).containsExactly("a", "A", "b", "B");
        }

        @Test
        void letsALetterDifferenceAnywhereOutweighACaseDifferenceEarlier() {
            assertThat(sorted("ab", "Aa")).containsExactly("Aa", "ab");
        }

        @Test
        void putsAnUnmarkedLetterBeforeAMarkedOneAndBothBeforeTheNextLetter() {
            assertThat(sorted("f", "é", "e")).containsExactly("e", "é", "f");
        }

        @Test
        void ordersGreekAlphabeticallyAfterLatin() {
            assertThat(sorted("ω", "β", "z", "α")).containsExactly("z", "α", "β", "ω");
        }

        @Test
        void treatsAFinalSigmaAsASigma() {
            assertThat(Collation.compare("λογος", "λογοσ")).isPositive();
            assertThat(Collation.compare("λογος", "λογοτ")).isNegative();
        }

        @Test
        void putsGreekLowerCaseBeforeItsCapital() {
            assertThat(sorted("Ω", "ω")).containsExactly("ω", "Ω");
        }

        @Test
        void callsTheComposedAndDecomposedFormsEqual() {
            String composed = "ά";
            String decomposed = Normalizer.normalize(composed, Normalizer.Form.NFD);
            assertThat(Collation.compare(composed, decomposed)).isZero();
        }
    }

    @Nested
    class PunctuationAndDigits {

        @Test
        void putsSpacingBeforePunctuationBeforeDigitsBeforeLetters() {
            assertThat(sorted("a", "1", "-", " ")).containsExactly(" ", "-", "1", "a");
        }

        @Test
        void doesNotIgnoreAHyphenOrASpace() {
            assertThat(sorted("ab", "a-b", "a b")).containsExactly("a b", "a-b", "ab");
        }

        @Test
        void ordersTheAsciiPunctuationByTheRootTable() {
            assertThat(sorted("a.b", "a-b", "a_b")).containsExactly("a_b", "a-b", "a.b");
        }

        @Test
        void agreesWithCodePointOrderOnIdsAndDates() {
            List<String> ids = List.of("cell_0a1b2c", "cell_0a1b2d", "cell_9zzzzz", "cell_a00000");
            assertThat(sorted(ids.get(3), ids.get(1), ids.get(2), ids.get(0))).containsExactlyElementsOf(ids);
            assertThat(sorted("2025-03-10", "2025-03-01", "2024-12-31"))
                    .containsExactly("2024-12-31", "2025-03-01", "2025-03-10");
        }

        @Test
        void putsAPrefixBeforeWhatExtendsIt() {
            assertThat(sorted("second-declension.neuter", "second-declension"))
                    .containsExactly("second-declension", "second-declension.neuter");
        }
    }

    @Nested
    class Equality {

        @Test
        void callsIdenticalStringsEqual() {
            assertThat(Collation.compare("puella", "puella")).isZero();
            assertThat(Collation.compare("", "")).isZero();
        }

        @Test
        void ignoresAControlCharacterEntirely() {
            assertThat(Collation.compare("pu\u0007ella", "puella")).isZero();
        }
    }
}
