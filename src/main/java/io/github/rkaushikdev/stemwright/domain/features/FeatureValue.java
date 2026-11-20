package io.github.rkaushikdev.stemwright.domain.features;

/**
 * One value a dimension admits, such as the genitive of case.
 *
 * @param key the slug that names the value in a bundle key
 * @param label the name printed in words
 * @param abbreviation the short form printed in a table header, such as {@code gen}
 */
public record FeatureValue(String key, String label, String abbreviation) {}
