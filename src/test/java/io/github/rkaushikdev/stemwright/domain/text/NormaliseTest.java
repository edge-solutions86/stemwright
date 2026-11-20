package io.github.rkaushikdev.stemwright.domain.text;

import static io.github.rkaushikdev.stemwright.domain.text.Normalise.baseOf;
import static io.github.rkaushikdev.stemwright.domain.text.Normalise.codePointLength;
import static io.github.rkaushikdev.stemwright.domain.text.Normalise.codePoints;
import static io.github.rkaushikdev.stemwright.domain.text.Normalise.collapseSpaces;
import static io.github.rkaushikdev.stemwright.domain.text.Normalise.hasMarks;
import static io.github.rkaushikdev.stemwright.domain.text.Normalise.isBlank;
import static io.github.rkaushikdev.stemwright.domain.text.Normalise.markClusters;
import static io.github.rkaushikdev.stemwright.domain.text.Normalise.marksOf;
import static io.github.rkaushikdev.stemwright.domain.text.Normalise.normaliseInput;
import static io.github.rkaushikdev.stemwright.domain.text.Normalise.requireText;
import static io.github.rkaushikdev.stemwright.domain.text.Normalise.stripInvisible;
import static io.github.rkaushikdev.stemwright.domain.text.Normalise.stripMarks;
import static io.github.rkaushikdev.stemwright.domain.text.Normalise.toForm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import java.text.Normalizer.Form;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NormaliseTest {

    private static String cp(int point) {
        return Character.toString(point);
    }

    private static final String ZWSP = cp(0x200b);
    private static final String ZWJ = cp(0x200d);
    private static final String BOM = cp(0xfeff);
    private static final String NBSP = cp(0x00a0);
    private static final String IDEOGRAPHIC_SPACE = cp(0x3000);
    private static final String COMBINING_ACUTE = cp(0x0301);
    private static final String COMBINING_PSILI = cp(0x0313);

    private static final String ALPHA = "α";
    private static final String ALPHA_TONOS = "ά";
    private static final String ALPHA_THEN_TONOS = ALPHA + COMBINING_ACUTE;
    private static final String ALPHA_PSILI = "ἀ";
    private static final String ALPHA_PSILI_OXIA = "ἄ";

    @Nested
    class ToForm {

        @Test
        void composesAndDecomposesTheSameLetter() {
            assertThat(toForm(ALPHA_THEN_TONOS, Form.NFC)).isEqualTo(ALPHA_TONOS);
            assertThat(toForm(ALPHA_TONOS, Form.NFD)).isEqualTo(ALPHA_THEN_TONOS);
        }
    }

    @Nested
    class StripInvisible {

        @Test
        void dropsZeroWidthMarks() {
            assertThat(stripInvisible("a" + ZWSP + "b" + ZWJ + "c" + BOM)).isEqualTo("abc");
        }

        @Test
        void dropsControlCharacters() {
            assertThat(stripInvisible("a" + cp(0x0007) + "b" + cp(0x007f))).isEqualTo("ab");
        }

        @Test
        void keepsTabNewlineAndCarriageReturnForTheSpacePassToHandle() {
            assertThat(stripInvisible("a\tb\nc\r")).isEqualTo("a\tb\nc\r");
        }
    }

    @Nested
    class CollapseSpaces {

        @Test
        void turnsAnyRunOfSpacingIntoOnePlainSpace() {
            assertThat(collapseSpaces("  a \t\n b c  ")).isEqualTo("a b c");
        }

        @Test
        void emptiesAStringOfNothingButSpacing() {
            assertThat(collapseSpaces(" " + IDEOGRAPHIC_SPACE + "\t ")).isEmpty();
        }

        @Test
        void treatsANoBreakSpaceAsSpacing() {
            assertThat(collapseSpaces("a" + NBSP + "b")).isEqualTo("a b");
        }
    }

    @Nested
    class NormaliseInput {

        @Test
        void runsInvisibleStrippingBeforeSpaceCollapsing() {
            assertThat(normaliseInput("a " + ZWSP + " b")).isEqualTo("a b");
        }

        @Test
        void composesByDefaultAndDecomposesOnRequest() {
            assertThat(normaliseInput(ALPHA_THEN_TONOS)).isEqualTo(ALPHA_TONOS);
            assertThat(normaliseInput(ALPHA_TONOS, Form.NFD)).isEqualTo(ALPHA_THEN_TONOS);
        }

        @Test
        void makesTwoSpellingsOfTheSameWordCompareEqual() {
            assertThat(normaliseInput("  " + ALPHA_THEN_TONOS + ZWSP + "  ")).isEqualTo(normaliseInput(ALPHA_TONOS));
        }
    }

    @Nested
    class IsBlank {

        @Test
        void isTrueForSpacingAndInvisibleMarksOnly() {
            assertThat(isBlank("   ")).isTrue();
            assertThat(isBlank(ZWSP + BOM)).isTrue();
            assertThat(isBlank("")).isTrue();
        }

        @Test
        void isFalseAsSoonAsAnythingVisibleIsThere() {
            assertThat(isBlank(" a ")).isFalse();
        }
    }

    @Nested
    class RequireText {

        @Test
        void returnsTheCleanedValue() {
            assertThat(requireText("lemma", "  oi  kos  ")).isEqualTo("oi kos");
        }

        @Test
        void refusesBlanksAndNamesThePath() {
            assertThatThrownBy(() -> requireText("lemma", " " + ZWSP + " ")).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> requireText("lemma", "")).hasMessageContaining("lemma must not be blank");
        }

        @Test
        void refusesTextOverTheCapItWasGiven() {
            assertThatThrownBy(() -> requireText("gloss", "a".repeat(9), 8))
                    .hasMessageContaining("at most 8 characters");
            assertThat(requireText("gloss", "a".repeat(8), 8)).hasSize(8);
        }
    }

    @Nested
    class CodePoints {

        @Test
        void countsASurrogatePairOnce() {
            String withAstral = "a" + cp(0x1d7d9) + "b";
            assertThat(codePointLength(withAstral)).isEqualTo(3);
            assertThat(withAstral.length()).isEqualTo(4);
        }

        @Test
        void splitsIntoWholeCodePoints() {
            assertThat(codePoints(cp(0x1d7d9))).containsExactly(cp(0x1d7d9));
        }
    }

    @Nested
    class MarkClusters {

        @Test
        void bindsTrailingMarksToTheLetterBeforeThem() {
            List<String> clusters = markClusters(ALPHA_PSILI + "ν");
            assertThat(clusters).hasSize(2);
            assertThat(baseOf(clusters.get(0))).isEqualTo(ALPHA);
            assertThat(marksOf(clusters.get(0))).containsExactly(COMBINING_PSILI);
        }

        @Test
        void holdsTwoMarksOnOneBase() {
            List<String> clusters = markClusters(ALPHA_PSILI_OXIA);
            assertThat(clusters).hasSize(1);
            assertThat(marksOf(clusters.get(0))).hasSize(2);
        }

        @Test
        void leavesAnUnmarkedWordOneClusterPerLetter() {
            assertThat(markClusters("logos")).hasSize(5);
        }

        @Test
        void doesNotLoseALeadingMark() {
            assertThat(markClusters(COMBINING_ACUTE + "a")).hasSize(2);
        }
    }

    @Nested
    class HasMarksAndStripMarks {

        @Test
        void seesAMarkWhetherTheInputWasComposedOrNot() {
            assertThat(hasMarks(ALPHA_TONOS)).isTrue();
            assertThat(hasMarks(ALPHA_THEN_TONOS)).isTrue();
            assertThat(hasMarks(ALPHA)).isFalse();
        }

        @Test
        void stripsBackToTheBareLetters() {
            assertThat(stripMarks(ALPHA_PSILI_OXIA + "ν")).isEqualTo(ALPHA + "ν");
            assertThat(stripMarks("resumé")).isEqualTo("resume");
        }

        @Test
        void leavesAnUnmarkedStringAlone() {
            assertThat(stripMarks("logos")).isEqualTo("logos");
        }
    }
}
