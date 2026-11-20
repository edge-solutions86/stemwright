package io.github.rkaushikdev.stemwright.domain.lexicon;

import io.github.rkaushikdev.stemwright.domain.features.FeatureSchema;

/**
 * What a lexeme is checked against: the language's feature schema for its inherent features, and
 * its class registry for its class and its language.
 *
 * @param schema the feature schema of the language
 * @param classes the inflection classes of the language
 */
public record LexemeContext(FeatureSchema schema, ClassRegistry classes) {}
