package io.github.rkaushikdev.stemwright.domain.features;

import java.util.List;
import java.util.Optional;

/**
 * One axis of a paradigm, such as case or number, with the values it admits in the order they are
 * conventionally printed.
 *
 * @param key the slug that names the dimension in a bundle key
 * @param label the name printed in words
 * @param values the admitted values, in table order
 * @param optional whether a lexeme may leave the dimension out. Gender is inherent to a Latin noun
 *     and chosen for a Latin adjective, so a noun paradigm omits it entirely.
 */
public record FeatureDimension(String key, String label, List<FeatureValue> values, boolean optional) {

    public FeatureDimension {
        values = List.copyOf(values);
    }

    public Optional<FeatureValue> value(String valueKey) {
        return values.stream().filter(value -> value.key().equals(valueKey)).findFirst();
    }
}
