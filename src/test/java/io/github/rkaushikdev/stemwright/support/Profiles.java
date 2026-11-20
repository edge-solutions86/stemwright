package io.github.rkaushikdev.stemwright.support;

import io.github.rkaushikdev.stemwright.domain.features.FeatureSchema;
import io.github.rkaushikdev.stemwright.domain.features.FeatureSchemaInput;
import io.github.rkaushikdev.stemwright.domain.features.FeatureSchemaInput.Dimension;
import io.github.rkaushikdev.stemwright.domain.paradigm.ParadigmProfile;
import io.github.rkaushikdev.stemwright.domain.paradigm.ParadigmProfileInput;
import io.github.rkaushikdev.stemwright.domain.paradigm.ParadigmSlotInput;

/** The paradigm profiles the tests share, and the schema they are written against. */
public final class Profiles {

    private Profiles() {}

    /** Case and number only, so a profile can be written out by hand in full. */
    public static final FeatureSchema LATIN_NOUN = FeatureSchema.of(FeatureSchemaInput.of(
            "latin", Dimension.of("case", "nom", "gen", "dat", "acc", "abl"), Dimension.of("number", "sg", "pl")));

    /** The first declension, puella style. Every cell is stem plus ending. */
    public static final ParadigmProfile FIRST_DECLENSION = ParadigmProfile.of(
            LATIN_NOUN,
            ParadigmProfileInput.of(
                            "first-declension",
                            "first-declension",
                            ParadigmSlotInput.of("nom.sg", "a"),
                            ParadigmSlotInput.of("gen.sg", "ae"),
                            ParadigmSlotInput.of("dat.sg", "ae"),
                            ParadigmSlotInput.of("acc.sg", "am"),
                            ParadigmSlotInput.of("abl.sg", "a"),
                            ParadigmSlotInput.of("nom.pl", "ae"),
                            ParadigmSlotInput.of("gen.pl", "arum"),
                            ParadigmSlotInput.of("dat.pl", "is"),
                            ParadigmSlotInput.of("acc.pl", "as"),
                            ParadigmSlotInput.of("abl.pl", "is"))
                    .withLabel("First declension"));

    /** The second declension neuter, bellum style. */
    public static final ParadigmProfile SECOND_NEUTER = ParadigmProfile.of(
            LATIN_NOUN,
            ParadigmProfileInput.of(
                    "second-neuter",
                    "second-declension.neuter",
                    ParadigmSlotInput.of("nom.sg", "um"),
                    ParadigmSlotInput.of("gen.sg", "i"),
                    ParadigmSlotInput.of("dat.sg", "o"),
                    ParadigmSlotInput.of("acc.sg", "um"),
                    ParadigmSlotInput.of("abl.sg", "o"),
                    ParadigmSlotInput.of("nom.pl", "a"),
                    ParadigmSlotInput.of("gen.pl", "orum"),
                    ParadigmSlotInput.of("dat.pl", "is"),
                    ParadigmSlotInput.of("acc.pl", "a"),
                    ParadigmSlotInput.of("abl.pl", "is")));
}
