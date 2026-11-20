package io.github.rkaushikdev.stemwright.domain.features;

import java.util.List;

/**
 * One clause of a selector: a dimension and the values it is limited to, or with {@code negated}
 * the values it excludes.
 *
 * @param dimension the dimension key
 * @param values the listed value keys
 * @param negated whether the listed values are excluded rather than required
 */
public record SelectorClause(String dimension, List<String> values, boolean negated) {

    public SelectorClause {
        values = List.copyOf(values);
    }

    public static SelectorClause of(String dimension, String... values) {
        return new SelectorClause(dimension, List.of(values), false);
    }

    public static SelectorClause not(String dimension, String... values) {
        return new SelectorClause(dimension, List.of(values), true);
    }
}
