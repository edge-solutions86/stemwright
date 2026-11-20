package io.github.rkaushikdev.stemwright.support;

import io.github.rkaushikdev.stemwright.domain.features.FeatureSchema;
import io.github.rkaushikdev.stemwright.domain.features.FeatureSchemaInput;
import io.github.rkaushikdev.stemwright.domain.features.FeatureSchemaInput.Dimension;
import io.github.rkaushikdev.stemwright.domain.features.FeatureSchemaInput.Value;
import java.util.List;
import java.util.Optional;

/** The schemas the unit tests share. Kept small enough to enumerate by hand. */
public final class Schemas {

    private Schemas() {}

    public static final FeatureSchema LATIN_NOUN = FeatureSchema.of(FeatureSchemaInput.of(
            "latin",
            Dimension.of("case", "nom", "gen", "dat", "acc", "abl", "voc"),
            Dimension.of("number", "sg", "pl")));

    public static final FeatureSchema LATIN_ADJECTIVE = FeatureSchema.of(FeatureSchemaInput.of(
            "latin-adj",
            Dimension.of("case", "nom", "gen", "dat", "acc", "abl", "voc"),
            Dimension.of("number", "sg", "pl"),
            Dimension.of("gender", "masc", "fem", "neut")));

    public static final FeatureSchema GREEK_VERB = FeatureSchema.of(FeatureSchemaInput.of(
            "greek",
            Dimension.of("tense", "pres", "impf", "fut", "aor", "perf"),
            Dimension.of("mood", "ind", "subj", "opt", "imp"),
            Dimension.of("voice", "act", "mid", "pass"),
            new Dimension(
                    "person",
                    Optional.empty(),
                    List.of(Value.of("first", "1"), Value.of("second", "2"), Value.of("third", "3")),
                    false),
            Dimension.optional("number", "sg", "du", "pl")));
}
