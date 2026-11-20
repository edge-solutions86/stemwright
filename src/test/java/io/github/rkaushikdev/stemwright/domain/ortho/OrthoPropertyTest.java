package io.github.rkaushikdev.stemwright.domain.ortho;

import static io.github.rkaushikdev.stemwright.domain.ortho.Distance.clusterDistance;
import static io.github.rkaushikdev.stemwright.domain.ortho.Folding.applyFolds;
import static io.github.rkaushikdev.stemwright.domain.ortho.Folding.foldedEqual;
import static io.github.rkaushikdev.stemwright.support.Ortho.GREEK;
import static io.github.rkaushikdev.stemwright.support.SeededRandom.forEachDraw;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.domain.text.Normalise;
import io.github.rkaushikdev.stemwright.support.SeededRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrthoPropertyTest {

    private static final List<String> GREEK_LETTERS = Normalise.codePoints("αβγδεζηθικλμνξοπρστυφχψω");
    private static final List<String> MARKS =
            Stream.of(0x0301, 0x0313, 0x0314, 0x0342).map(Character::toString).toList();

    private static String greekWord(SeededRandom random) {
        int length = random.nextInt(3, 9);
        StringBuilder word = new StringBuilder();
        for (int index = 0; index < length; index += 1) {
            word.append(random.pick(GREEK_LETTERS));
            if (random.bool(0.25)) {
                word.append(random.pick(MARKS));
            }
        }
        return random.bool(0.2) ? word + "ς" : word.toString();
    }

    private static List<FoldStep> foldsFor(SeededRandom random) {
        List<FoldStep> folds = new ArrayList<>();
        for (FoldStep step : Folding.ORDER) {
            if (random.bool(0.4)) {
                folds.add(step);
            }
        }
        return folds;
    }

    @Nested
    class FoldingProperties {

        @Test
        void isIdempotentWhateverTheSteps() {
            forEachDraw(120, 1861, (random, draw) -> {
                String word = greekWord(random);
                List<FoldStep> folds = foldsFor(random);
                String once = applyFolds(word, folds);
                assertThat(applyFolds(once, folds)).isEqualTo(once);
            });
        }

        @Test
        void doesNotDependOnTheOrderTheStepsWereListed() {
            forEachDraw(120, 2903, (random, draw) -> {
                String word = greekWord(random);
                List<FoldStep> folds = foldsFor(random);
                assertThat(applyFolds(word, random.shuffle(folds))).isEqualTo(applyFolds(word, folds));
            });
        }

        @Test
        void leavesAStringEqualToItselfUnderAnySetOfSteps() {
            forEachDraw(80, 3907, (random, draw) -> {
                String word = greekWord(random);
                assertThat(foldedEqual(word, word, foldsFor(random))).isTrue();
            });
        }

        @Test
        void neverSeparatesTwoStringsThatASubsetOfTheStepsAlreadyJoined() {
            forEachDraw(120, 4931, (random, draw) -> {
                String first = greekWord(random);
                String second = greekWord(random);
                List<FoldStep> folds = foldsFor(random);
                if (!foldedEqual(first, second, folds)) {
                    return;
                }
                List<FoldStep> wider = new ArrayList<>();
                for (FoldStep step : Folding.ORDER) {
                    if (folds.contains(step) || random.bool(0.5)) {
                        wider.add(step);
                    }
                }
                assertThat(foldedEqual(first, second, wider)).isTrue();
            });
        }
    }

    @Nested
    class DistanceProperties {

        @Test
        void isZeroExactlyWhenTheFormsAreTheSame() {
            forEachDraw(120, 5939, (random, draw) -> {
                String word = greekWord(random);
                assertThat(clusterDistance(word, word)).isZero();
                String other = greekWord(random);
                assertThat(clusterDistance(word, other) == 0).isEqualTo(word.equals(other));
            });
        }

        @Test
        void isSymmetric() {
            forEachDraw(120, 6947, (random, draw) -> {
                String first = greekWord(random);
                String second = greekWord(random);
                assertThat(clusterDistance(first, second)).isEqualTo(clusterDistance(second, first));
            });
        }

        @Test
        void isNeverMoreThanTheLongerOfTheTwoLengths() {
            forEachDraw(120, 7951, (random, draw) -> {
                String first = greekWord(random);
                String second = greekWord(random);
                int longest = Math.max(
                        Normalise.markClusters(first).size(),
                        Normalise.markClusters(second).size());
                assertThat(clusterDistance(first, second)).isLessThanOrEqualTo(longest);
            });
        }

        @Test
        void obeysTheTriangleInequality() {
            forEachDraw(80, 8963, (random, draw) -> {
                String a = greekWord(random);
                String b = greekWord(random);
                String c = greekWord(random);
                assertThat(clusterDistance(a, c)).isLessThanOrEqualTo(clusterDistance(a, b) + clusterDistance(b, c));
            });
        }

        @Test
        void keepsSimilarityBetweenNoughtAndOne() {
            forEachDraw(120, 9973, (random, draw) -> {
                double value = Distance.similarity(greekWord(random), greekWord(random));
                assertThat(value).isGreaterThanOrEqualTo(0.0);
                assertThat(value).isLessThanOrEqualTo(1.0);
            });
        }

        @Test
        void neverSharesAPrefixLongerThanTheShorterString() {
            forEachDraw(80, 1289, (random, draw) -> {
                String first = greekWord(random);
                String second = greekWord(random);
                int shortest = Math.min(
                        Normalise.markClusters(first).size(),
                        Normalise.markClusters(second).size());
                assertThat(Distance.sharedPrefixLength(first, second)).isLessThanOrEqualTo(shortest);
            });
        }

        @Test
        void neverCallsAStringATranspositionOfItself() {
            forEachDraw(80, 2297, (random, draw) -> {
                String word = greekWord(random);
                assertThat(Distance.isTransposition(word, word)).isFalse();
            });
        }
    }

    @Nested
    class MarkingProperties {

        @Test
        void marksAnAnswerEqualToTheExpectedFormExactAtEveryLevel() {
            forEachDraw(80, 3301, (random, draw) -> {
                String word = greekWord(random);
                ToleranceLevel level = GREEK.requireLevel(random.pick(List.of("exact", "standard", "lenient")));
                assertThat(MatchResult.compareForm(word, word, level).verdict()).isEqualTo(MatchVerdict.EXACT);
            });
        }

        @Test
        void neverMarksAnAnswerWrongAtALevelThatAlreadyAcceptedItAtAStricterOne() {
            forEachDraw(120, 4327, (random, draw) -> {
                String expected = greekWord(random);
                String given = greekWord(random);
                ToleranceLevel strict = GREEK.requireLevel("standard");
                ToleranceLevel loose = GREEK.requireLevel("lenient");
                assertThat(strict.isStricterThan(loose)).isTrue();
                if (MatchResult.compareForm(expected, given, strict).isCorrect()) {
                    assertThat(MatchResult.compareForm(expected, given, loose).isCorrect())
                            .isTrue();
                }
            });
        }

        @Test
        void normalisesTheAnswerBeforeComparingIt() {
            forEachDraw(80, 5333, (random, draw) -> {
                String word = greekWord(random);
                ToleranceLevel level = GREEK.requireLevel("exact");
                String padded = "  " + word + "  ";
                assertThat(MatchResult.compareForm(word, padded, level).given())
                        .isEqualTo(Normalise.normaliseInput(word));
            });
        }
    }
}
