package io.github.rkaushikdev.stemwright.domain.features;

/**
 * One fixed feature in a bundle: a dimension and the value it takes, such as case and genitive.
 *
 * @param dimension the dimension key
 * @param value the value key
 */
public record FeaturePair(String dimension, String value) {}
