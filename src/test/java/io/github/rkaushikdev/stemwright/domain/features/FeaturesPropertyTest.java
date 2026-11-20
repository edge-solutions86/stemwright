package io.github.rkaushikdev.stemwright.domain.features;

import static io.github.rkaushikdev.stemwright.support.Schemas.GREEK_VERB;
import static io.github.rkaushikdev.stemwright.support.Schemas.LATIN_ADJECTIVE;
import static io.github.rkaushikdev.stemwright.support.Schemas.LATIN_NOUN;
import static io.github.rkaushikdev.stemwright.support.SeededRandom.forEachDraw;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.domain.features.Notation.BundleStyle;
import io.github.rkaushikdev.stemwright.support.SeededRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FeaturesPropertyTest {

    private static final List<FeatureSchema> SCHEMAS = List.of(LATIN_NOUN, LATIN_ADJECTIVE, GREEK_VERB);

    private static Selector selectorFor(FeatureSchema schema, SeededRandom random) {
        String clauses = random.sample(schema.dimensions(), random.nextInt(1, 2)).stream()
                .map(dimension -> {
                    List<FeatureValue> values = random.sample(
                            dimension.values(),
                            random.nextInt(1, dimension.values().size()));
                    boolean negated = random.bool(0.3);
                    String listed = values.stream().map(FeatureValue::key).collect(Collectors.joining("|"));
                    return dimension.key() + (negated ? "!=" : "=") + listed;
                })
                .collect(Collectors.joining(","));
        return Selector.parse(schema, clauses);
    }

    @Nested
    class BundleProperties {

        @Test
        void roundTripsEveryCompleteBundleThroughItsCanonicalKey() {
            forEachDraw(60, 1301, (random, draw) -> {
                FeatureSchema schema = random.pick(SCHEMAS);
                FeatureBundle bundle = random.pick(FeatureBundle.enumerate(schema));
                String key = bundle.key();
                assertThat(FeatureBundle.parseKey(schema, key).key()).isEqualTo(key);
            });
        }

        /**
         * The plain printer is not round trip safe and is not meant to be. The greek schema
         * abbreviates the imperfect to imp, which is also the key of the imperative mood, so a bare
         * token is genuinely ambiguous there. That is why the qualifying printer exists and is what
         * the round trip is asserted over.
         */
        @Test
        void roundTripsEveryCompleteBundleThroughTheUnambiguousNotation() {
            forEachDraw(60, 4409, (random, draw) -> {
                FeatureSchema schema = random.pick(SCHEMAS);
                FeatureBundle bundle = random.pick(FeatureBundle.enumerate(schema));
                String printed = Notation.formatUnambiguous(schema, bundle);
                assertThat(Notation.parseBundleNotation(schema, printed).key()).isEqualTo(bundle.key());
            });
        }

        @Test
        void printsAPlainNotationThatReadsBackOnlyWhenNothingIsAmbiguous() {
            FeatureBundle bundle = FeatureBundle.enumerate(LATIN_NOUN).get(3);
            String printed = Notation.formatBundle(LATIN_NOUN, bundle, BundleStyle.KEY);
            assertThat(Notation.parseBundleNotation(LATIN_NOUN, printed).key()).isEqualTo(bundle.key());
            assertThat(Notation.ambiguousTokens(GREEK_VERB)).contains("imp");
        }

        @Test
        void putsPairsIntoSchemaOrderHoweverTheInputWasOrdered() {
            forEachDraw(60, 7717, (random, draw) -> {
                FeatureSchema schema = random.pick(SCHEMAS);
                FeatureBundle bundle = random.pick(FeatureBundle.enumerate(schema));
                Map<String, String> input = new LinkedHashMap<>();
                for (FeaturePair pair : random.shuffle(bundle.pairs())) {
                    input.put(pair.dimension(), pair.value());
                }
                assertThat(FeatureBundle.of(schema, input).key()).isEqualTo(bundle.key());
            });
        }

        @Test
        void isSubsumedByAnythingItWasBuiltFromByRemovingAPair() {
            forEachDraw(60, 2213, (random, draw) -> {
                FeatureSchema schema = random.pick(SCHEMAS);
                FeatureBundle bundle = random.pick(FeatureBundle.enumerate(schema));
                String dimension = random.pick(bundle.pairs()).dimension();
                assertThat(bundle.without(dimension).subsumes(bundle)).isTrue();
            });
        }

        @Test
        void carriesAPairThatWasJustAdded() {
            forEachDraw(60, 8831, (random, draw) -> {
                FeatureSchema schema = random.pick(SCHEMAS);
                FeatureBundle bundle = random.pick(FeatureBundle.enumerate(schema));
                FeatureDimension dimension = random.pick(schema.dimensions());
                String value = random.pick(dimension.values()).key();
                FeatureBundle grown = bundle.with(schema, dimension.key(), value);
                assertThat(grown.key()).contains(dimension.key() + "=" + value);
            });
        }

        @Test
        void ordersBundlesConsistentlyInBothDirections() {
            forEachDraw(40, 6203, (random, draw) -> {
                FeatureSchema schema = random.pick(SCHEMAS);
                List<FeatureBundle> bundles = FeatureBundle.enumerate(schema);
                FeatureBundle first = random.pick(bundles);
                FeatureBundle second = random.pick(bundles);
                int forward = FeatureBundle.compare(schema, first, second);
                int backward = FeatureBundle.compare(schema, second, first);
                assertThat(Integer.signum(forward)).isEqualTo(Integer.signum(-backward));
            });
        }

        @Test
        void enumeratesExactlyAsManyBundlesAsTheCardinalitySays() {
            for (FeatureSchema schema : SCHEMAS) {
                assertThat(FeatureBundle.enumerate(schema)).hasSize(schema.cardinality());
            }
        }
    }

    @Nested
    class SelectorProperties {

        @Test
        void expandsToExactlyAsManyBundlesAsItsCardinality() {
            forEachDraw(80, 3319, (random, draw) -> {
                FeatureSchema schema = random.pick(SCHEMAS);
                Selector selection = selectorFor(schema, random);
                assertThat(selection.expand(schema)).hasSize(selection.cardinality(schema));
            });
        }

        @Test
        void matchesEveryBundleItExpandedTo() {
            forEachDraw(60, 5501, (random, draw) -> {
                FeatureSchema schema = random.pick(SCHEMAS);
                Selector selection = selectorFor(schema, random);
                for (FeatureBundle bundle : selection.expand(schema)) {
                    assertThat(selection.matches(bundle)).isTrue();
                }
            });
        }

        @Test
        void roundTripsThroughItsWrittenForm() {
            forEachDraw(60, 9137, (random, draw) -> {
                FeatureSchema schema = random.pick(SCHEMAS);
                Selector selection = selectorFor(schema, random);
                String printed = selection.format();
                assertThat(Selector.parse(schema, printed).format()).isEqualTo(printed);
            });
        }
    }
}
