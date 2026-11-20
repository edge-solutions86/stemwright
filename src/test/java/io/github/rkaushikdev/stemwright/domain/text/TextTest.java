package io.github.rkaushikdev.stemwright.domain.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TextTest {

    @Nested
    class Trim {

        @Test
        void removesEverySpacingCodePointFromBothEnds() {
            String padded = " 　 \t kos  \n";
            assertThat(Text.trim(padded)).isEqualTo("kos");
        }

        @Test
        void leavesInnerSpacingAlone() {
            assertThat(Text.trim("  oi  kos ")).isEqualTo("oi  kos");
        }

        @Test
        void doesNotTreatTheNextLineControlAsSpacing() {
            assertThat(Text.trim("\u0085a")).isEqualTo("\u0085a");
        }

        @Test
        void trimsOnlyTheEndWhenAskedTo() {
            assertThat(Text.trimEnd("  oi kos 　\t\n")).isEqualTo("  oi kos");
            assertThat(Text.trimEnd(" \t ")).isEmpty();
            assertThat(Text.trimEnd("a\u0085")).isEqualTo("a\u0085");
        }
    }

    @Nested
    class Case {

        @Test
        void ignoresTheMachineLocale() {
            Locale previous = Locale.getDefault();
            try {
                Locale.setDefault(Locale.forLanguageTag("tr"));
                assertThat(Text.upper("i")).isEqualTo("I");
                assertThat(Text.lower("I")).isEqualTo("i");
            } finally {
                Locale.setDefault(previous);
            }
        }

        @Test
        void writesAFinalSigmaAtTheEndOfAWord() {
            assertThat(Text.lower("ΛΟΓΟΣ")).isEqualTo("λογος");
        }
    }

    @Nested
    class Split {

        @Test
        void keepsEmptyPartsIncludingTrailingOnes() {
            assertThat(Text.split("a;;b;", ";")).containsExactly("a", "", "b", "");
        }

        @Test
        void splitsAnEmptyStringIntoOneEmptyPart() {
            assertThat(Text.split("", ";")).containsExactly("");
        }

        @Test
        void takesTheSeparatorLiterallyRatherThanAsAPattern() {
            assertThat(Text.split("a.b|c", ".")).containsExactly("a", "b|c");
            assertThat(Text.split("a|b", "|")).containsExactly("a", "b");
        }

        @Test
        void refusesAnEmptySeparator() {
            assertThatThrownBy(() -> Text.split("abc", "")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Padding {

        @Test
        void padsTheStartWithTheFillerUpToTheLength() {
            assertThat(Text.padStart("7", 3, '0')).isEqualTo("007");
            assertThat(Text.padStart("7", 3)).isEqualTo("  7");
        }

        @Test
        void padsTheEndWithSpaces() {
            assertThat(Text.padEnd("ab", 4)).isEqualTo("ab  ");
        }

        @Test
        void leavesAValueThatIsLongEnoughAlone() {
            assertThat(Text.padStart("12345", 3, '0')).isEqualTo("12345");
            assertThat(Text.padEnd("abcd", 2)).isEqualTo("abcd");
        }
    }
}
