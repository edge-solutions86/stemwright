package io.github.rkaushikdev.stemwright.domain.features;

import io.github.rkaushikdev.stemwright.domain.Identifiers;
import io.github.rkaushikdev.stemwright.domain.errors.ProblemCollector;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.text.Normalise;
import io.github.rkaushikdev.stemwright.domain.text.Text;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The grammar of one language as this desk models it. A schema fixes which dimensions exist, which
 * values each admits and in what order they are conventionally printed, because a paradigm table
 * read out of order is a different table to a learner even though it holds the same cells.
 *
 * <p>Schemas are per language and never global. Latin has five cases and no dual, Greek has five
 * cases and a dual, and no code outside a schema is allowed to assume either.
 *
 * @param language the language key
 * @param dimensions the dimensions in declaration order
 */
public record FeatureSchema(String language, List<FeatureDimension> dimensions) {

    private static final int LABEL_LIMIT = 64;
    private static final int ABBREVIATION_LIMIT = 8;

    public FeatureSchema {
        dimensions = List.copyOf(dimensions);
    }

    /** Checks a schema as written and fills in what it left out, reporting every problem at once. */
    public static FeatureSchema of(FeatureSchemaInput input) {
        String language = Identifiers.asSlug("language", input.language());
        ProblemCollector problems = new ProblemCollector();
        if (input.dimensions().isEmpty()) {
            problems.add("dimensions", "must name at least one dimension");
        }

        List<FeatureDimension> dimensions = new ArrayList<>();
        Set<String> seenDimensions = new HashSet<>();
        for (FeatureSchemaInput.Dimension raw : input.dimensions()) {
            String key = Identifiers.asSlug("dimension", raw.key());
            if (!seenDimensions.add(key)) {
                problems.add("dimensions." + key, "is declared twice");
                continue;
            }

            List<FeatureValue> values =
                    raw.values().stream().map(value -> buildValue(key, value)).toList();
            if (values.isEmpty()) {
                problems.add("dimensions." + key + ".values", "must admit at least one value");
            }
            Set<String> seenValues = new HashSet<>();
            for (FeatureValue value : values) {
                if (!seenValues.add(value.key())) {
                    problems.add("dimensions." + key + ".values." + value.key(), "is declared twice");
                }
            }

            String label = raw.label()
                    .map(text -> Normalise.requireText(key + ".label", text, LABEL_LIMIT))
                    .orElseGet(() -> titleCase(key));
            dimensions.add(new FeatureDimension(key, label, values, raw.optional()));
        }

        problems.throwIfAny("feature schema for " + language + " is not usable");
        return new FeatureSchema(language, dimensions);
    }

    private static FeatureValue buildValue(String dimensionKey, FeatureSchemaInput.Value raw) {
        String key = Identifiers.asSlug(dimensionKey + ".value", raw.key());
        String label = raw.label()
                .map(text -> Normalise.requireText(key + ".label", text, LABEL_LIMIT))
                .orElseGet(() -> titleCase(key));
        String abbreviation = raw.abbreviation()
                .map(text -> Normalise.requireText(key + ".abbreviation", text, ABBREVIATION_LIMIT))
                .orElseGet(() -> key.substring(0, Math.min(3, key.length())));
        return new FeatureValue(key, label, abbreviation);
    }

    private static String titleCase(String key) {
        return Text.split(key.replace('.', '-'), "-").stream()
                .map(part -> part.isEmpty() ? part : Text.upper(part.substring(0, 1)) + part.substring(1))
                .collect(Collectors.joining(" "));
    }

    public List<String> dimensionKeys() {
        return dimensions.stream().map(FeatureDimension::key).toList();
    }

    public Optional<FeatureDimension> dimensionOf(String key) {
        return dimensions.stream()
                .filter(dimension -> dimension.key().equals(key))
                .findFirst();
    }

    public FeatureDimension requireDimension(String key) {
        return dimensionOf(key)
                .orElseThrow(
                        () -> ValidationException.field("dimension", "is not in the " + language + " schema: " + key));
    }

    public boolean hasValue(String dimensionKey, String valueKey) {
        return dimensionOf(dimensionKey)
                .flatMap(dimension -> dimension.value(valueKey))
                .isPresent();
    }

    /**
     * Where a value sits in its dimension, which is how a table orders its rows; -1 for a value the
     * dimension does not admit.
     *
     * @throws ValidationException when the schema has no such dimension
     */
    public int valueIndex(String dimensionKey, String valueKey) {
        List<FeatureValue> values = requireDimension(dimensionKey).values();
        for (int index = 0; index < values.size(); index += 1) {
            if (values.get(index).key().equals(valueKey)) {
                return index;
            }
        }
        return -1;
    }

    /** Where a dimension sits in the schema; -1 for one the schema does not have. */
    public int dimensionIndex(String dimensionKey) {
        for (int index = 0; index < dimensions.size(); index += 1) {
            if (dimensions.get(index).key().equals(dimensionKey)) {
                return index;
            }
        }
        return -1;
    }

    /** How many bundles the schema can express if every dimension is filled in. */
    public int cardinality() {
        int total = 1;
        for (FeatureDimension dimension : dimensions) {
            total *= dimension.values().size();
        }
        return total;
    }

    public List<FeatureDimension> requiredDimensions() {
        return dimensions.stream().filter(dimension -> !dimension.optional()).toList();
    }
}
