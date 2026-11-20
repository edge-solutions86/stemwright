package io.github.rkaushikdev.stemwright.domain.features;

import static io.github.rkaushikdev.stemwright.support.Schemas.GREEK_VERB;
import static io.github.rkaushikdev.stemwright.support.Schemas.LATIN_ADJECTIVE;
import static io.github.rkaushikdev.stemwright.support.Schemas.LATIN_NOUN;
import static io.github.rkaushikdev.stemwright.support.TestMaps.ordered;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.support.Problems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FeatureBundleTest {

    @Nested
    class Of {

        @Test
        void putsThePairsIntoSchemaOrderWhateverOrderTheyArrivedIn() {
            FeatureBundle bundle =
                    FeatureBundle.of(LATIN_ADJECTIVE, ordered("gender", "fem", "number", "pl", "case", "dat"));
            assertThat(bundle.dimensions()).containsExactly("case", "number", "gender");
        }

        @Test
        void refusesADimensionTheSchemaDoesNotHave() {
            assertThat(Problems.problemMessages(() -> FeatureBundle.of(LATIN_NOUN, ordered("tense", "pres"))))
                    .containsExactly("tense is not a dimension of latin");
        }

        @Test
        void refusesAValueTheDimensionDoesNotAdmit() {
            assertThat(Problems.problemMessages(() -> FeatureBundle.of(LATIN_NOUN, ordered("case", "inst"))))
                    .containsExactly("case does not admit the value inst");
        }

        @Test
        void reportsEveryBadPairAtOnce() {
            assertThat(Problems.detailsOf(() -> FeatureBundle.of(LATIN_NOUN, ordered("tense", "pres", "case", "inst"))))
                    .hasSize(2);
        }

        @Test
        void acceptsAnEmptyBundle() {
            assertThat(FeatureBundle.of(LATIN_NOUN, Map.of()).size()).isZero();
        }
    }

    @Nested
    class ReadingValues {

        private final FeatureBundle bundle = FeatureBundle.of(LATIN_NOUN, ordered("case", "gen", "number", "sg"));

        @Test
        void readsAValueThatIsThere() {
            assertThat(bundle.valueOf("case")).contains("gen");
            assertThat(bundle.requireValue("number")).isEqualTo("sg");
        }

        @Test
        void givesNothingForADimensionTheBundleDoesNotFix() {
            assertThat(bundle.valueOf("gender")).isEmpty();
            assertThatThrownBy(() -> bundle.requireValue("gender")).hasMessageContaining("has no value for gender");
        }
    }

    @Nested
    class Key {

        @Test
        void writesDimensionsInSchemaOrderRegardlessOfInputOrder() {
            FeatureBundle a =
                    FeatureBundle.of(LATIN_ADJECTIVE, ordered("number", "sg", "case", "nom", "gender", "masc"));
            FeatureBundle b =
                    FeatureBundle.of(LATIN_ADJECTIVE, ordered("gender", "masc", "case", "nom", "number", "sg"));
            assertThat(a.key()).isEqualTo("case=nom;number=sg;gender=masc");
            assertThat(a.key()).isEqualTo(b.key());
        }

        @Test
        void writesAnEmptyBundleAsAnEmptyString() {
            assertThat(FeatureBundle.of(LATIN_NOUN, Map.of()).key()).isEmpty();
        }

        @Test
        void roundTripsThroughTheParser() {
            FeatureBundle bundle = FeatureBundle.of(
                    GREEK_VERB,
                    ordered("tense", "aor", "mood", "subj", "voice", "mid", "person", "second", "number", "pl"));
            assertThat(FeatureBundle.parseKey(GREEK_VERB, bundle.key()).key()).isEqualTo(bundle.key());
        }
    }

    @Nested
    class ParseKey {

        @Test
        void readsAnEmptyKeyAsAnEmptyBundle() {
            assertThat(FeatureBundle.parseKey(LATIN_NOUN, "").size()).isZero();
        }

        @Test
        void refusesAPartWithNoEqualsSign() {
            assertThatThrownBy(() -> FeatureBundle.parseKey(LATIN_NOUN, "casenom"))
                    .hasMessageContaining("dimension value pair");
            assertThatThrownBy(() -> FeatureBundle.parseKey(LATIN_NOUN, "=nom"))
                    .hasMessageContaining("dimension value pair");
        }

        @Test
        void refusesARepeatedDimension() {
            assertThatThrownBy(() -> FeatureBundle.parseKey(LATIN_NOUN, "case=nom;case=gen"))
                    .hasMessageContaining("names case twice");
        }

        @Test
        void stillValidatesAgainstTheSchema() {
            assertThatThrownBy(() -> FeatureBundle.parseKey(LATIN_NOUN, "case=inst"))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class EqualityAndSubsumption {

        private final FeatureBundle full =
                FeatureBundle.of(LATIN_ADJECTIVE, ordered("case", "nom", "number", "sg", "gender", "masc"));
        private final FeatureBundle partial = FeatureBundle.of(LATIN_ADJECTIVE, ordered("case", "nom", "number", "sg"));

        @Test
        void isEqualOnlyWhenThePairsMatchExactly() {
            assertThat(full).isEqualTo(full);
            assertThat(full).isNotEqualTo(partial);
        }

        @Test
        void subsumesWhenEveryPairOfTheBroaderBundleIsPresent() {
            assertThat(partial.subsumes(full)).isTrue();
            assertThat(full.subsumes(partial)).isFalse();
        }

        @Test
        void isSubsumedByTheEmptyBundle() {
            FeatureBundle empty = FeatureBundle.of(LATIN_ADJECTIVE, Map.of());
            assertThat(empty.subsumes(full)).isTrue();
        }
    }

    @Nested
    class WithAndWithout {

        private final FeatureBundle bundle = FeatureBundle.of(LATIN_ADJECTIVE, ordered("case", "nom", "number", "sg"));

        @Test
        void addsAPairAndKeepsSchemaOrder() {
            FeatureBundle grown = bundle.with(LATIN_ADJECTIVE, "gender", "neut");
            assertThat(grown.key()).isEqualTo("case=nom;number=sg;gender=neut");
        }

        @Test
        void replacesAPairThatIsAlreadyThere() {
            FeatureBundle changed = bundle.with(LATIN_ADJECTIVE, "case", "acc");
            assertThat(changed.key()).isEqualTo("case=acc;number=sg");
        }

        @Test
        void leavesTheOriginalAlone() {
            bundle.with(LATIN_ADJECTIVE, "gender", "neut");
            assertThat(bundle.key()).isEqualTo("case=nom;number=sg");
        }

        @Test
        void dropsAPairAndToleratesOneThatWasNotThere() {
            assertThat(bundle.without("number").key()).isEqualTo("case=nom");
            assertThat(bundle.without("gender").key()).isEqualTo("case=nom;number=sg");
        }

        @Test
        void refusesToAddAValueTheSchemaRejects() {
            assertThatThrownBy(() -> bundle.with(LATIN_ADJECTIVE, "gender", "common"))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class Completeness {

        @Test
        void needsEveryRequiredDimensionAndIgnoresOptionalOnes() {
            FeatureBundle bundle = FeatureBundle.of(
                    GREEK_VERB, ordered("tense", "pres", "mood", "ind", "voice", "act", "person", "first"));
            assertThat(bundle.isComplete(GREEK_VERB)).isTrue();
            assertThat(bundle.missingDimensions(GREEK_VERB)).isEmpty();
        }

        @Test
        void namesWhatIsMissingInSchemaOrder() {
            FeatureBundle bundle = FeatureBundle.of(GREEK_VERB, ordered("voice", "act"));
            assertThat(bundle.missingDimensions(GREEK_VERB)).containsExactly("tense", "mood", "person");
            assertThat(bundle.isComplete(GREEK_VERB)).isFalse();
        }
    }

    @Nested
    class Compare {

        @Test
        void sortsByTheFirstDimensionThenTheNext() {
            List<FeatureBundle> sorted = new ArrayList<>(List.of(
                    FeatureBundle.of(LATIN_NOUN, ordered("case", "gen", "number", "sg")),
                    FeatureBundle.of(LATIN_NOUN, ordered("case", "nom", "number", "pl")),
                    FeatureBundle.of(LATIN_NOUN, ordered("case", "nom", "number", "sg"))));
            sorted.sort(FeatureBundle.tableOrder(LATIN_NOUN));
            assertThat(sorted)
                    .extracting(FeatureBundle::key)
                    .containsExactly("case=nom;number=sg", "case=nom;number=pl", "case=gen;number=sg");
        }

        @Test
        void sortsAMissingValueLast() {
            FeatureBundle withNumber = FeatureBundle.of(LATIN_NOUN, ordered("case", "nom", "number", "sg"));
            FeatureBundle withoutNumber = FeatureBundle.of(LATIN_NOUN, ordered("case", "nom"));
            assertThat(FeatureBundle.compare(LATIN_NOUN, withNumber, withoutNumber))
                    .isNegative();
            assertThat(FeatureBundle.compare(LATIN_NOUN, withoutNumber, withNumber))
                    .isPositive();
        }

        @Test
        void callsIdenticalBundlesEqual() {
            FeatureBundle bundle = FeatureBundle.of(LATIN_NOUN, ordered("case", "nom", "number", "sg"));
            assertThat(FeatureBundle.compare(LATIN_NOUN, bundle, bundle)).isZero();
        }
    }

    @Nested
    class Enumerate {

        @Test
        void producesTheFullCrossProduct() {
            assertThat(FeatureBundle.enumerate(LATIN_NOUN)).hasSize(12);
            assertThat(FeatureBundle.enumerate(LATIN_ADJECTIVE)).hasSize(36);
        }

        @Test
        void startsAtTheFirstValueOfEveryDimension() {
            assertThat(FeatureBundle.enumerate(LATIN_NOUN).get(0).key()).isEqualTo("case=nom;number=sg");
        }

        @Test
        void producesDistinctKeys() {
            List<String> keys = FeatureBundle.enumerate(LATIN_ADJECTIVE).stream()
                    .map(FeatureBundle::key)
                    .toList();
            assertThat(Set.copyOf(keys)).hasSameSizeAs(keys);
        }
    }

    @Nested
    class Unsafe {

        @Test
        void copiesThePairsItIsHanded() {
            List<FeaturePair> pairs = new ArrayList<>(List.of(new FeaturePair("case", "nom")));
            FeatureBundle bundle = FeatureBundle.unsafe(pairs);
            pairs.add(new FeaturePair("number", "sg"));
            assertThat(bundle.size()).isEqualTo(1);
        }
    }
}
