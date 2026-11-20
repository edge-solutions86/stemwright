package io.github.rkaushikdev.stemwright.domain.features;

import static io.github.rkaushikdev.stemwright.support.Schemas.LATIN_ADJECTIVE;
import static io.github.rkaushikdev.stemwright.support.Schemas.LATIN_NOUN;
import static io.github.rkaushikdev.stemwright.support.TestMaps.ordered;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.support.Problems;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SelectorTest {

    private static Selector parse(String text) {
        return Selector.parse(LATIN_NOUN, text);
    }

    @Nested
    class Parse {

        @Test
        void readsASingleClause() {
            assertThat(parse("number=pl").format()).isEqualTo("number=pl");
        }

        @Test
        void readsSeveralValuesSeparatedByPipes() {
            assertThat(parse("case=nom|gen").format()).isEqualTo("case=gen|nom");
        }

        @Test
        void readsANegatedClause() {
            assertThat(parse("case!=voc").format()).isEqualTo("case!=voc");
        }

        @Test
        void putsClausesIntoSchemaOrder() {
            assertThat(parse("number=pl,case=dat").format()).isEqualTo("case=dat,number=pl");
        }

        @Test
        void readsAnEmptyStringAsTheSelectorThatMatchesEverything() {
            assertThat(parse("")).isEqualTo(Selector.EMPTY);
        }

        @Test
        void refusesAnEmptyClauseAndAClauseWithNoEqualsSign() {
            assertThatThrownBy(() -> parse("case=nom,")).hasMessageContaining("empty clause");
            assertThatThrownBy(() -> parse("casenom")).hasMessageContaining("dimension and values");
            assertThatThrownBy(() -> parse("=nom")).hasMessageContaining("dimension and values");
        }

        @Test
        void refusesAnUnknownDimensionOrValue() {
            assertThat(Problems.problemMessages(() -> parse("tense=pres")))
                    .containsExactly("tense is not a dimension of latin");
            assertThat(Problems.problemMessages(() -> parse("case=inst")))
                    .containsExactly("case does not admit the value inst");
        }

        @Test
        void refusesTheSameDimensionConstrainedTwiceTheSameWay() {
            assertThat(Problems.problemMessages(() -> parse("case=nom,case=gen")))
                    .containsExactly("case is constrained twice in the same direction");
        }

        @Test
        void allowsAPositiveAndANegativeClauseOnOneDimension() {
            assertThat(parse("case=nom|gen|dat,case!=dat").format()).isEqualTo("case=dat|gen|nom,case!=dat");
        }

        @Test
        void refusesAClauseWithNoValuesAtAll() {
            assertThat(Problems.problemMessages(() -> parse("case=")))
                    .containsExactly("case must name at least one value");
        }
    }

    @Nested
    class Matches {

        private final FeatureBundle nomSg = FeatureBundle.of(LATIN_NOUN, ordered("case", "nom", "number", "sg"));
        private final FeatureBundle genPl = FeatureBundle.of(LATIN_NOUN, ordered("case", "gen", "number", "pl"));

        @Test
        void matchesEverythingWhenItHasNoClauses() {
            assertThat(Selector.EMPTY.matches(nomSg)).isTrue();
            assertThat(Selector.EMPTY.matches(FeatureBundle.of(LATIN_NOUN, Map.of())))
                    .isTrue();
        }

        @Test
        void matchesOnAListedValue() {
            assertThat(parse("case=nom|gen").matches(nomSg)).isTrue();
            assertThat(parse("case=dat").matches(nomSg)).isFalse();
        }

        @Test
        void invertsANegatedClause() {
            assertThat(parse("case!=nom").matches(nomSg)).isFalse();
            assertThat(parse("case!=nom").matches(genPl)).isTrue();
        }

        @Test
        void needsEveryClauseToHold() {
            assertThat(parse("case=gen,number=pl").matches(genPl)).isTrue();
            assertThat(parse("case=gen,number=sg").matches(genPl)).isFalse();
        }

        @Test
        void doesNotMatchABundleThatLeavesTheDimensionOpen() {
            FeatureBundle caseOnly = FeatureBundle.of(LATIN_NOUN, ordered("case", "gen"));
            assertThat(parse("number=pl").matches(caseOnly)).isFalse();
            assertThat(parse("number!=pl").matches(caseOnly)).isFalse();
        }
    }

    @Nested
    class SelectAndExpand {

        @Test
        void filtersAListDownToWhatMatches() {
            List<FeatureBundle> bundles = List.of(
                    FeatureBundle.of(LATIN_NOUN, ordered("case", "nom", "number", "sg")),
                    FeatureBundle.of(LATIN_NOUN, ordered("case", "nom", "number", "pl")));
            assertThat(parse("number=pl").select(bundles))
                    .extracting(FeatureBundle::key)
                    .containsExactly("case=nom;number=pl");
        }

        @Test
        void expandsAgainstTheWholeSchemaInTableOrder() {
            List<FeatureBundle> expanded = parse("number=pl").expand(LATIN_NOUN);
            assertThat(expanded).hasSize(6);
            assertThat(expanded.get(0).key()).isEqualTo("case=nom;number=pl");
        }
    }

    @Nested
    class Cardinality {

        @Test
        void countsTheWholeSpaceWhenUnconstrained() {
            assertThat(Selector.EMPTY.cardinality(LATIN_NOUN)).isEqualTo(12);
        }

        @Test
        void multipliesTheAllowedValuesOfEachDimension() {
            assertThat(parse("case=nom|gen").cardinality(LATIN_NOUN)).isEqualTo(4);
            assertThat(parse("case=nom|gen,number=sg").cardinality(LATIN_NOUN)).isEqualTo(2);
        }

        @Test
        void countsANegationAsEverythingElse() {
            assertThat(parse("case!=voc").cardinality(LATIN_NOUN)).isEqualTo(10);
        }

        @Test
        void agreesWithWhatExpansionProduces() {
            for (String text : List.of("", "case=nom", "case!=voc,number=pl", "case=nom|gen|dat")) {
                Selector selection = parse(text);
                assertThat(selection.expand(LATIN_NOUN)).as(text).hasSize(selection.cardinality(LATIN_NOUN));
            }
        }

        @Test
        void reportsAnEmptySelectionWhenANegationExcludesEverything() {
            Selector all = parse("case!=nom|gen|dat|acc|abl|voc");
            assertThat(all.isEmptySelection(LATIN_NOUN)).isTrue();
            assertThat(Selector.EMPTY.isEmptySelection(LATIN_NOUN)).isFalse();
        }
    }

    @Nested
    class Intersect {

        private String narrow(String a, String b) {
            return parse(a).intersect(LATIN_NOUN, parse(b)).format();
        }

        @Test
        void keepsClausesThatOnlyOneSideNames() {
            assertThat(narrow("case=nom", "number=pl")).isEqualTo("case=nom,number=pl");
        }

        @Test
        void takesTheSharedValuesOfARepeatedDimension() {
            assertThat(narrow("case=nom|gen|dat", "case=gen|dat|acc")).isEqualTo("case=dat|gen");
        }

        @Test
        void unionsTheExcludedValuesOfARepeatedNegation() {
            assertThat(narrow("case!=nom", "case!=gen")).isEqualTo("case!=gen|nom");
        }

        @Test
        void producesAnEmptySelectionWhenTheTwoDoNotOverlap() {
            Selector result = parse("case=nom").intersect(LATIN_NOUN, parse("case=gen"));
            assertThat(result.isEmptySelection(LATIN_NOUN)).isTrue();
        }

        @Test
        void isUnchangedByIntersectingWithTheEverythingSelector() {
            assertThat(narrow("case=nom|gen", "")).isEqualTo("case=gen|nom");
        }
    }

    @Nested
    class Describe {

        @Test
        void saysSoWhenNothingIsConstrained() {
            assertThat(Selector.EMPTY.describe(LATIN_NOUN)).isEqualTo("every cell in latin");
        }

        @Test
        void readsClausesOutInWords() {
            Selector selection = Selector.parse(LATIN_ADJECTIVE, "case=nom|gen,gender!=neut");
            assertThat(selection.describe(LATIN_ADJECTIVE)).isEqualTo("case of gen or nom, gender other than neut");
        }
    }
}
