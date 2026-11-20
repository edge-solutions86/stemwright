package io.github.rkaushikdev.stemwright.domain.features;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.errors.ErrorDetail;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.features.FeatureSchemaInput.Dimension;
import io.github.rkaushikdev.stemwright.domain.features.FeatureSchemaInput.Value;
import io.github.rkaushikdev.stemwright.support.Problems;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FeatureSchemaTest {

    private static final FeatureSchema LATIN = FeatureSchema.of(FeatureSchemaInput.of(
            "latin",
            Dimension.of("case", "nom", "gen", "dat", "acc", "abl", "voc"),
            Dimension.of("number", "sg", "pl"),
            Dimension.optional("gender", "masc", "fem", "neut")));

    @Nested
    class Of {

        @Test
        void keepsTheDimensionsInTheOrderTheyWereDeclared() {
            assertThat(LATIN.dimensionKeys()).containsExactly("case", "number", "gender");
        }

        @Test
        void lowerCasesTheLanguageKey() {
            FeatureSchemaInput latin = FeatureSchemaInput.from(LATIN);
            FeatureSchema renamed = FeatureSchema.of(new FeatureSchemaInput("Ancient-Greek", latin.dimensions()));
            assertThat(renamed.language()).isEqualTo("ancient-greek");
        }

        @Test
        void defaultsALabelFromTheKeyAndAnAbbreviationFromTheFirstLetters() {
            FeatureDimension dimension = LATIN.requireDimension("case");
            assertThat(dimension.label()).isEqualTo("Case");
            assertThat(dimension.values().get(0)).isEqualTo(new FeatureValue("nom", "Nom", "nom"));
        }

        @Test
        void takesExplicitLabelsAndAbbreviationsWhenGiven() {
            FeatureSchema schema = FeatureSchema.of(FeatureSchemaInput.of(
                    "greek",
                    new Dimension(
                            "voice",
                            Optional.of("Diathesis"),
                            List.of(Value.of("mid-pass", "Middle passive", "mp")),
                            false)));
            assertThat(schema.dimensions().get(0).label()).isEqualTo("Diathesis");
            assertThat(schema.dimensions().get(0).values().get(0).abbreviation())
                    .isEqualTo("mp");
        }

        @Test
        void marksOptionalDimensionsAndLeavesTheRestRequired() {
            assertThat(LATIN.requiredDimensions())
                    .extracting(FeatureDimension::key)
                    .containsExactly("case", "number");
            assertThat(LATIN.requireDimension("gender").optional()).isTrue();
        }

        @Test
        void refusesASchemaWithNoDimensions() {
            assertThatThrownBy(() -> FeatureSchema.of(FeatureSchemaInput.of("latin")))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void refusesADimensionWithNoValues() {
            ValidationException problem = Problems.validationFailureOf(
                    () -> FeatureSchema.of(FeatureSchemaInput.of("latin", Dimension.of("case"))));
            assertThat(problem.getMessage()).isEqualTo("feature schema for latin is not usable");
            assertThat(problem.details())
                    .containsExactly(new ErrorDetail("dimensions.case.values", "must admit at least one value"));
        }

        @Test
        void collectsEveryProblemInOneThrow() {
            List<String> paths = Problems.problemPaths(() -> FeatureSchema.of(
                    FeatureSchemaInput.of("latin", Dimension.of("case", "nom", "nom"), Dimension.of("case", "gen"))));
            assertThat(paths).containsExactly("dimensions.case.values.nom", "dimensions.case");
        }

        @Test
        void refusesAKeyThatIsNotASlug() {
            assertThatThrownBy(
                            () -> FeatureSchema.of(FeatureSchemaInput.of("latin", Dimension.of("Case Marking", "nom"))))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class Lookups {

        @Test
        void findsADimensionAndReportsAMissingOne() {
            assertThat(LATIN.dimensionOf("number")).hasValueSatisfying(dimension -> assertThat(dimension.values())
                    .hasSize(2));
            assertThat(LATIN.dimensionOf("tense")).isEmpty();
            assertThatThrownBy(() -> LATIN.requireDimension("tense")).hasMessageContaining("not in the latin schema");
        }

        @Test
        void answersWhetherAValueBelongsToADimension() {
            assertThat(LATIN.hasValue("case", "abl")).isTrue();
            assertThat(LATIN.hasValue("case", "inst")).isFalse();
            assertThat(LATIN.hasValue("tense", "pres")).isFalse();
        }

        @Test
        void reportsTheDeclaredPositionOfAValue() {
            assertThat(LATIN.valueIndex("case", "nom")).isZero();
            assertThat(LATIN.valueIndex("case", "voc")).isEqualTo(5);
            assertThat(LATIN.valueIndex("case", "inst")).isEqualTo(-1);
        }

        @Test
        void reportsTheDeclaredPositionOfADimension() {
            assertThat(LATIN.dimensionIndex("case")).isZero();
            assertThat(LATIN.dimensionIndex("gender")).isEqualTo(2);
            assertThat(LATIN.dimensionIndex("mood")).isEqualTo(-1);
        }
    }

    @Nested
    class Cardinality {

        @Test
        void multipliesOutEveryDimension() {
            assertThat(LATIN.cardinality()).isEqualTo(6 * 2 * 3);
        }

        @Test
        void isTheValueCountForASingleDimensionSchema() {
            FeatureSchema schema = FeatureSchema.of(FeatureSchemaInput.of("x", Dimension.of("number", "sg", "pl")));
            assertThat(schema.cardinality()).isEqualTo(2);
        }
    }
}
