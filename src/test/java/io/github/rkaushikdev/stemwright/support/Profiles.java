package io.github.rkaushikdev.stemwright.support;

import io.github.rkaushikdev.stemwright.domain.features.FeatureSchema;
import io.github.rkaushikdev.stemwright.domain.features.FeatureSchemaInput;
import io.github.rkaushikdev.stemwright.domain.features.FeatureSchemaInput.Dimension;

/** The paradigm profiles the tests share, and the schema they are written against. */
public final class Profiles {

    private Profiles() {}

    /** Case and number only, so a profile can be written out by hand in full. */
    public static final FeatureSchema LATIN_NOUN = FeatureSchema.of(FeatureSchemaInput.of(
            "latin", Dimension.of("case", "nom", "gen", "dat", "acc", "abl"), Dimension.of("number", "sg", "pl")));
}
