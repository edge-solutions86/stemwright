package io.github.rkaushikdev.stemwright.domain.features;

import io.github.rkaushikdev.stemwright.domain.errors.ProblemCollector;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.text.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * One point in the space a schema describes: nominative, singular, masculine.
 *
 * <p>A bundle is an ordered list of pairs rather than a map, so the order is the schema order
 * rather than whatever order the caller happened to type, which is what makes the canonical {@link
 * #key()} stable. Two bundles are equal exactly when their keys are.
 *
 * @param pairs the fixed features, in schema order
 */
public record FeatureBundle(List<FeaturePair> pairs) {

    /** The bundle that fixes nothing. Shared because it is immutable and common. */
    public static final FeatureBundle EMPTY = new FeatureBundle(List.of());

    public FeatureBundle {
        pairs = List.copyOf(pairs);
    }

    /**
     * Checks the features against the schema and puts them in schema order, reporting every bad
     * pair at once. The input is read in its own iteration order, which only decides the order of
     * the problems.
     */
    public static FeatureBundle of(FeatureSchema schema, Map<String, String> input) {
        ProblemCollector problems = new ProblemCollector();
        List<FeaturePair> pairs = new ArrayList<>();

        for (Map.Entry<String, String> entry : input.entrySet()) {
            String dimension = entry.getKey();
            String value = entry.getValue();
            if (schema.dimensionOf(dimension).isEmpty()) {
                problems.add(dimension, "is not a dimension of " + schema.language());
                continue;
            }
            if (!schema.hasValue(dimension, value)) {
                problems.add(dimension, "does not admit the value " + value);
                continue;
            }
            pairs.add(new FeaturePair(dimension, value));
        }

        problems.throwIfAny("bundle is not valid for " + schema.language());
        pairs.sort(Comparator.comparingInt(pair -> schema.dimensionIndex(pair.dimension())));
        return new FeatureBundle(pairs);
    }

    /** Builds without checking. Only the codec uses this, on data it already wrote. */
    public static FeatureBundle unsafe(List<FeaturePair> pairs) {
        return new FeatureBundle(pairs);
    }

    public Optional<String> valueOf(String dimension) {
        return pairs.stream()
                .filter(pair -> pair.dimension().equals(dimension))
                .map(FeaturePair::value)
                .findFirst();
    }

    public String requireValue(String dimension) {
        return valueOf(dimension)
                .orElseThrow(() -> ValidationException.field("bundle", "has no value for " + dimension));
    }

    public List<String> dimensions() {
        return pairs.stream().map(FeaturePair::dimension).toList();
    }

    public int size() {
        return pairs.size();
    }

    /** The features as a map in schema order, the shape {@link #of} takes. */
    public Map<String, String> toInput() {
        Map<String, String> input = new LinkedHashMap<>();
        for (FeaturePair pair : pairs) {
            input.put(pair.dimension(), pair.value());
        }
        return input;
    }

    /**
     * The canonical key, such as {@code case=nom;number=sg}. Two bundles that mean the same thing
     * produce the same string, which is what a cell id is derived from and what a syncretism map is
     * keyed on. Never change this format without a journal migration.
     */
    public String key() {
        return pairs.stream().map(pair -> pair.dimension() + "=" + pair.value()).collect(Collectors.joining(";"));
    }

    /** Reads a canonical key back, checking it against the schema. */
    public static FeatureBundle parseKey(FeatureSchema schema, String key) {
        if (key.isEmpty()) {
            return EMPTY;
        }
        Map<String, String> input = new LinkedHashMap<>();
        for (String part : Text.split(key, ";")) {
            int equals = part.indexOf('=');
            if (equals <= 0) {
                throw ValidationException.field("bundleKey", "is not a dimension value pair: " + part);
            }
            String dimension = part.substring(0, equals);
            if (input.containsKey(dimension)) {
                throw ValidationException.field("bundleKey", "names " + dimension + " twice");
            }
            input.put(dimension, part.substring(equals + 1));
        }
        return of(schema, input);
    }

    /** True when every pair of this, the broader bundle, appears in the narrower one. */
    public boolean subsumes(FeatureBundle narrower) {
        return pairs.stream().allMatch(pair -> narrower.valueOf(pair.dimension())
                .filter(pair.value()::equals)
                .isPresent());
    }

    /** A copy with the dimension set to the value, checked against the schema. */
    public FeatureBundle with(FeatureSchema schema, String dimension, String value) {
        Map<String, String> input = toInput();
        input.put(dimension, value);
        return of(schema, input);
    }

    /** A copy without the dimension, which tolerates a dimension that was never fixed. */
    public FeatureBundle without(String dimension) {
        return new FeatureBundle(pairs.stream()
                .filter(pair -> !pair.dimension().equals(dimension))
                .toList());
    }

    /** A bundle is complete when it fixes every dimension the schema requires. */
    public boolean isComplete(FeatureSchema schema) {
        return missingDimensions(schema).isEmpty();
    }

    /** The required dimensions the bundle leaves open, in schema order. */
    public List<String> missingDimensions(FeatureSchema schema) {
        return schema.requiredDimensions().stream()
                .map(FeatureDimension::key)
                .filter(key -> valueOf(key).isEmpty())
                .toList();
    }

    /**
     * Orders bundles the way a paradigm table reads: by the first dimension the schema declares,
     * then the next, with a missing value sorting last.
     */
    public static int compare(FeatureSchema schema, FeatureBundle a, FeatureBundle b) {
        for (FeatureDimension dimension : schema.dimensions()) {
            Optional<String> left = a.valueOf(dimension.key());
            Optional<String> right = b.valueOf(dimension.key());
            if (left.equals(right)) {
                continue;
            }
            if (left.isEmpty()) {
                return 1;
            }
            if (right.isEmpty()) {
                return -1;
            }
            return schema.valueIndex(dimension.key(), left.get()) - schema.valueIndex(dimension.key(), right.get());
        }
        return 0;
    }

    /** {@link #compare} as a comparator for one schema. */
    public static Comparator<FeatureBundle> tableOrder(FeatureSchema schema) {
        return (a, b) -> compare(schema, a, b);
    }

    /** Every complete bundle the schema can express, in table order. */
    public static List<FeatureBundle> enumerate(FeatureSchema schema) {
        List<List<FeaturePair>> bundles = List.of(List.of());
        for (FeatureDimension dimension : schema.dimensions()) {
            List<List<FeaturePair>> grown = new ArrayList<>();
            for (List<FeaturePair> bundle : bundles) {
                for (FeatureValue value : dimension.values()) {
                    List<FeaturePair> next = new ArrayList<>(bundle);
                    next.add(new FeaturePair(dimension.key(), value.key()));
                    grown.add(next);
                }
            }
            bundles = grown;
        }
        return bundles.stream().map(FeatureBundle::new).toList();
    }
}
