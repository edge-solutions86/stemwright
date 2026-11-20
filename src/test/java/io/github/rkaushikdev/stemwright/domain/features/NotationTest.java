package io.github.rkaushikdev.stemwright.domain.features;

import static io.github.rkaushikdev.stemwright.domain.features.Notation.ambiguousTokens;
import static io.github.rkaushikdev.stemwright.domain.features.Notation.describeBundle;
import static io.github.rkaushikdev.stemwright.domain.features.Notation.formatBundle;
import static io.github.rkaushikdev.stemwright.domain.features.Notation.formatUnambiguous;
import static io.github.rkaushikdev.stemwright.domain.features.Notation.isAmbiguousToken;
import static io.github.rkaushikdev.stemwright.domain.features.Notation.parseBundleNotation;
import static io.github.rkaushikdev.stemwright.support.Schemas.GREEK_VERB;
import static io.github.rkaushikdev.stemwright.support.Schemas.LATIN_ADJECTIVE;
import static io.github.rkaushikdev.stemwright.support.Schemas.LATIN_NOUN;
import static io.github.rkaushikdev.stemwright.support.TestMaps.ordered;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.features.FeatureSchemaInput.Dimension;
import io.github.rkaushikdev.stemwright.domain.features.Notation.BundleStyle;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NotationTest {

    private static final FeatureSchema SHARED = FeatureSchema.of(FeatureSchemaInput.of(
            "shared", Dimension.of("tense", "pres", "perf"), Dimension.of("aspect", "perf", "imperf")));

    @Nested
    class FormatBundle {

        private final FeatureBundle bundle =
                FeatureBundle.of(LATIN_ADJECTIVE, ordered("case", "gen", "number", "pl", "gender", "neut"));

        @Test
        void printsAbbreviationsJoinedByDotsByDefault() {
            assertThat(formatBundle(LATIN_ADJECTIVE, bundle)).isEqualTo("gen.pl.neu");
        }

        @Test
        void printsFullKeysOnRequest() {
            assertThat(formatBundle(LATIN_ADJECTIVE, bundle, BundleStyle.KEY)).isEqualTo("gen.pl.neut");
        }

        @Test
        void printsLabelsSeparatedBySpaces() {
            assertThat(formatBundle(LATIN_ADJECTIVE, bundle, BundleStyle.LABEL)).isEqualTo("Gen Pl Neut");
        }

        @Test
        void printsAnEmptyBundleAsAnEmptyString() {
            assertThat(formatBundle(LATIN_NOUN, FeatureBundle.of(LATIN_NOUN, Map.of())))
                    .isEmpty();
        }
    }

    @Nested
    class ParseBundleNotation {

        @Test
        void readsADottedNotationIntoABundle() {
            FeatureBundle bundle = parseBundleNotation(LATIN_NOUN, "gen.pl");
            assertThat(bundle.key()).isEqualTo("case=gen;number=pl");
        }

        @Test
        void acceptsFullKeysAsWellAsAbbreviations() {
            assertThat(parseBundleNotation(GREEK_VERB, "aor.subj.mid.second").key())
                    .isEqualTo(parseBundleNotation(GREEK_VERB, "aor.subj.mid.2").key());
        }

        @Test
        void isCaseInsensitiveAndToleratesSurroundingSpace() {
            assertThat(parseBundleNotation(LATIN_NOUN, "  GEN.PL ").key()).isEqualTo("case=gen;number=pl");
        }

        @Test
        void readsAnEmptyNotationAsAnEmptyBundle() {
            assertThat(parseBundleNotation(LATIN_NOUN, "").key()).isEmpty();
        }

        @Test
        void refusesAnEmptyToken() {
            assertThatThrownBy(() -> parseBundleNotation(LATIN_NOUN, "gen..pl")).hasMessageContaining("empty token");
        }

        @Test
        void refusesATokenNoDimensionAdmits() {
            assertThatThrownBy(() -> parseBundleNotation(LATIN_NOUN, "gen.inst"))
                    .hasMessageContaining("is not a feature value in latin");
        }

        @Test
        void refusesToFixTheSameDimensionTwice() {
            assertThatThrownBy(() -> parseBundleNotation(LATIN_NOUN, "gen.nom"))
                    .hasMessageContaining("fixes case twice");
        }

        @Test
        void roundTripsWhateverItPrinted() {
            FeatureBundle bundle =
                    FeatureBundle.of(LATIN_ADJECTIVE, ordered("case", "abl", "number", "sg", "gender", "fem"));
            String printed = formatBundle(LATIN_ADJECTIVE, bundle, BundleStyle.KEY);
            assertThat(parseBundleNotation(LATIN_ADJECTIVE, printed).key()).isEqualTo(bundle.key());
        }
    }

    @Nested
    class Ambiguity {

        @Test
        void listsTheTokensMoreThanOneDimensionAdmits() {
            assertThat(ambiguousTokens(SHARED)).containsExactly("per", "perf");
            assertThat(ambiguousTokens(LATIN_NOUN)).isEmpty();
        }

        @Test
        void answersForASingleToken() {
            assertThat(isAmbiguousToken(SHARED, "perf")).isTrue();
            assertThat(isAmbiguousToken(SHARED, "pres")).isFalse();
        }

        @Test
        void refusesABareAmbiguousTokenAndNamesBothDimensions() {
            assertThatThrownBy(() -> parseBundleNotation(SHARED, "perf"))
                    .hasMessageContaining("ambiguous between tense, aspect");
        }

        @Test
        void acceptsTheSameTokenOnceItIsQualified() {
            assertThat(parseBundleNotation(SHARED, "aspect:perf").key()).isEqualTo("aspect=perf");
            assertThat(parseBundleNotation(SHARED, "tense:perf").key()).isEqualTo("tense=perf");
        }

        @Test
        void refusesAQualifierNamingADimensionThatIsNotThere() {
            assertThatThrownBy(() -> parseBundleNotation(SHARED, "mood:perf"))
                    .hasMessageContaining("does not have: mood");
        }

        @Test
        void refusesAQualifiedValueTheDimensionDoesNotAdmit() {
            assertThatThrownBy(() -> parseBundleNotation(SHARED, "tense:imperf"))
                    .hasMessageContaining("tense does not admit imperf");
        }
    }

    @Nested
    class FormatUnambiguous {

        @Test
        void qualifiesOnlyTheTokensThatNeedIt() {
            FeatureBundle bundle = FeatureBundle.of(SHARED, ordered("tense", "pres", "aspect", "perf"));
            assertThat(formatUnambiguous(SHARED, bundle)).isEqualTo("pre.aspect:per");
        }

        @Test
        void printsAPlainNotationWhenNothingIsAmbiguous() {
            FeatureBundle bundle = FeatureBundle.of(LATIN_NOUN, ordered("case", "dat", "number", "pl"));
            assertThat(formatUnambiguous(LATIN_NOUN, bundle)).isEqualTo("dat.pl");
        }

        @Test
        void alwaysParsesBackToTheBundleItWasGiven() {
            for (String tense : List.of("pres", "perf")) {
                for (String aspect : List.of("perf", "imperf")) {
                    FeatureBundle bundle = FeatureBundle.of(SHARED, ordered("tense", tense, "aspect", aspect));
                    String printed = formatUnambiguous(SHARED, bundle);
                    assertThat(parseBundleNotation(SHARED, printed).key()).isEqualTo(bundle.key());
                }
            }
        }
    }

    @Nested
    class DescribeBundle {

        @Test
        void readsABundleOutInWordsInSchemaOrder() {
            FeatureBundle bundle = FeatureBundle.of(
                    GREEK_VERB, ordered("voice", "mid", "tense", "aor", "mood", "subj", "person", "second"));
            assertThat(describeBundle(GREEK_VERB, bundle)).isEqualTo("tense aor, mood subj, voice mid, person second");
        }

        @Test
        void saysSoWhenNothingIsFixed() {
            assertThat(describeBundle(LATIN_NOUN, FeatureBundle.of(LATIN_NOUN, Map.of())))
                    .isEqualTo("no features fixed");
        }
    }
}
