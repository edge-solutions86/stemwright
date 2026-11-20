package io.github.rkaushikdev.stemwright.domain.features;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * A feature schema as a caller writes it, before {@link FeatureSchema#of} checks it and fills in
 * the labels and abbreviations it left out.
 *
 * @param language the language key, which becomes a slug
 * @param dimensions the dimensions in declaration order
 */
public record FeatureSchemaInput(String language, List<Dimension> dimensions) {

    public FeatureSchemaInput {
        dimensions = List.copyOf(dimensions);
    }

    public static FeatureSchemaInput of(String language, Dimension... dimensions) {
        return new FeatureSchemaInput(language, List.of(dimensions));
    }

    /** The input that would rebuild an existing schema, for a caller that wants to vary one part. */
    public static FeatureSchemaInput from(FeatureSchema schema) {
        return new FeatureSchemaInput(
                schema.language(),
                schema.dimensions().stream().map(Dimension::from).toList());
    }

    /**
     * One dimension as written.
     *
     * @param key the dimension key
     * @param label the printed name, defaulted from the key when absent
     * @param values the admitted values in table order
     * @param optional whether a lexeme may leave the dimension out
     */
    public record Dimension(String key, Optional<String> label, List<Value> values, boolean optional) {

        public Dimension {
            values = List.copyOf(values);
        }

        /** A required dimension whose values are given by key alone. */
        public static Dimension of(String key, String... values) {
            return new Dimension(key, Optional.empty(), keys(values), false);
        }

        /** An optional dimension whose values are given by key alone. */
        public static Dimension optional(String key, String... values) {
            return new Dimension(key, Optional.empty(), keys(values), true);
        }

        /** A required dimension with its values written out in full. */
        public static Dimension of(String key, List<Value> values) {
            return new Dimension(key, Optional.empty(), values, false);
        }

        public static Dimension from(FeatureDimension dimension) {
            return new Dimension(
                    dimension.key(),
                    Optional.of(dimension.label()),
                    dimension.values().stream().map(Value::from).toList(),
                    dimension.optional());
        }

        private static List<Value> keys(String... values) {
            return Arrays.stream(values).map(Value::of).toList();
        }
    }

    /**
     * One value as written.
     *
     * @param key the value key
     * @param label the printed name, defaulted from the key when absent
     * @param abbreviation the table header form, defaulted to the first three letters when absent
     */
    public record Value(String key, Optional<String> label, Optional<String> abbreviation) {

        public static Value of(String key) {
            return new Value(key, Optional.empty(), Optional.empty());
        }

        public static Value of(String key, String abbreviation) {
            return new Value(key, Optional.empty(), Optional.of(abbreviation));
        }

        public static Value of(String key, String label, String abbreviation) {
            return new Value(key, Optional.of(label), Optional.of(abbreviation));
        }

        public static Value from(FeatureValue value) {
            return of(value.key(), value.label(), value.abbreviation());
        }
    }
}
