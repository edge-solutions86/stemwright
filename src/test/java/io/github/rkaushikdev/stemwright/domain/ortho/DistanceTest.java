package io.github.rkaushikdev.stemwright.domain.ortho;

import static io.github.rkaushikdev.stemwright.domain.ortho.Distance.clusterDistance;
import static io.github.rkaushikdev.stemwright.domain.ortho.Distance.firstDivergence;
import static io.github.rkaushikdev.stemwright.domain.ortho.Distance.isTransposition;
import static io.github.rkaushikdev.stemwright.domain.ortho.Distance.pointDistance;
import static io.github.rkaushikdev.stemwright.domain.ortho.Distance.sharedPrefixLength;
import static io.github.rkaushikdev.stemwright.domain.ortho.Distance.sharedSuffixLength;
import static io.github.rkaushikdev.stemwright.domain.ortho.Distance.similarity;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DistanceTest {

    private static final String ACUTE = Character.toString(0x0301);

    @Nested
    class ClusterDistance {

        @Test
        void isZeroForIdenticalStrings() {
            assertThat(clusterDistance("puella", "puella")).isZero();
        }

        @Test
        void countsOneSubstitution() {
            assertThat(clusterDistance("puella", "puellu")).isEqualTo(1);
        }

        @Test
        void countsOneInsertionAndOneDeletion() {
            assertThat(clusterDistance("puella", "puellae")).isEqualTo(1);
            assertThat(clusterDistance("puellae", "puella")).isEqualTo(1);
        }

        @Test
        void countsAnAccentAsOneChangeNotAsSeveralCodePoints() {
            assertThat(clusterDistance("anthropos", "a" + ACUTE + "nthropos")).isEqualTo(1);
            assertThat(pointDistance("anthropos", "a" + ACUTE + "nthropos")).isEqualTo(1);
        }

        @Test
        void countsAComposedAccentTheSameAsADecomposedOne() {
            assertThat(clusterDistance("ανθρωπος", "άνθρωπος")).isEqualTo(1);
        }

        @Test
        void isTheLengthOfTheOtherStringWhenOneIsEmpty() {
            assertThat(clusterDistance("", "puella")).isEqualTo(6);
            assertThat(clusterDistance("puella", "")).isEqualTo(6);
            assertThat(clusterDistance("", "")).isZero();
        }

        @Test
        void isSymmetric() {
            assertThat(clusterDistance("bellum", "bellorum")).isEqualTo(clusterDistance("bellorum", "bellum"));
        }
    }

    @Nested
    class IsTransposition {

        @Test
        void isTrueForOneAdjacentSwap() {
            assertThat(isTransposition("puella", "peulla")).isTrue();
        }

        @Test
        void isFalseForASwapThatIsNotAdjacent() {
            assertThat(isTransposition("puella", "auellp")).isFalse();
        }

        @Test
        void isFalseWhenTheLengthsDiffer() {
            assertThat(isTransposition("puella", "puellae")).isFalse();
        }

        @Test
        void isFalseForIdenticalStrings() {
            assertThat(isTransposition("puella", "puella")).isFalse();
        }

        @Test
        void isFalseForASingleLetterString() {
            assertThat(isTransposition("a", "a")).isFalse();
        }

        @Test
        void isFalseWhenThreePositionsDiffer() {
            assertThat(isTransposition("abcd", "badc")).isFalse();
        }
    }

    @Nested
    class FirstDivergence {

        @Test
        void givesNothingWhenTheFormsAgree() {
            assertThat(firstDivergence("puella", "puella")).isEmpty();
        }

        @Test
        void namesThePositionAndBothClusters() {
            assertThat(firstDivergence("puella", "puellu"))
                    .contains(new Divergence(5, Optional.of("a"), Optional.of("u")));
        }

        @Test
        void reportsAMissingClusterAtTheEnd() {
            assertThat(firstDivergence("puellae", "puella"))
                    .contains(new Divergence(6, Optional.of("e"), Optional.empty()));
        }

        @Test
        void reportsAnExtraClusterAtTheEnd() {
            assertThat(firstDivergence("puella", "puellae"))
                    .contains(new Divergence(6, Optional.empty(), Optional.of("e")));
        }

        @Test
        void countsAnAccentedVowelAsOnePosition() {
            assertThat(firstDivergence("ανθρωπος", "άνθρωπος"))
                    .map(Divergence::index)
                    .contains(0);
        }
    }

    @Nested
    class SharedPrefixAndSuffix {

        @Test
        void countsTheSharedOpening() {
            assertThat(sharedPrefixLength("puella", "puellae")).isEqualTo(6);
            assertThat(sharedPrefixLength("bellum", "puella")).isZero();
        }

        @Test
        void countsTheSharedEnding() {
            assertThat(sharedSuffixLength("puellarum", "bellorum")).isEqualTo(3);
            assertThat(sharedSuffixLength("puella", "bellum")).isZero();
        }

        @Test
        void neverCountsPastTheShorterString() {
            assertThat(sharedPrefixLength("pu", "puella")).isEqualTo(2);
            assertThat(sharedSuffixLength("um", "bellum")).isEqualTo(2);
        }
    }

    @Nested
    class Similarity {

        @Test
        void isOneForIdenticalStringsAndForTwoEmptyOnes() {
            assertThat(similarity("puella", "puella")).isEqualTo(1.0);
            assertThat(similarity("", "")).isEqualTo(1.0);
        }

        @Test
        void isZeroWhenNothingIsShared() {
            assertThat(similarity("abc", "xyz")).isZero();
        }

        @Test
        void fallsAsTheFormsDiverge() {
            double close = similarity("puella", "puellae");
            double far = similarity("puella", "bellorum");
            assertThat(close).isGreaterThan(far);
            assertThat(close).isLessThan(1.0);
        }

        @Test
        void isNeverNegative() {
            assertThat(similarity("a", "abcdefgh")).isGreaterThanOrEqualTo(0.0);
        }
    }
}
