package io.github.rkaushikdev.stemwright.app;

import io.github.rkaushikdev.stemwright.domain.features.FeatureSchema;
import io.github.rkaushikdev.stemwright.domain.lexicon.ClassRegistry;
import io.github.rkaushikdev.stemwright.domain.ortho.OrthoProfile;
import io.github.rkaushikdev.stemwright.domain.paradigm.RuleSet;
import java.util.Objects;

/**
 * Everything configured for one language that the lexemes and the profile book are written against:
 * its feature schema, its inflection classes, how forgiving its marking is and how its endings join.
 *
 * @param language the language key
 * @param schema the dimensions and values its bundles are written in
 * @param classes its inflection classes
 * @param ortho its tolerance levels
 * @param rules its join rules, which realise a form from a stem and an ending
 */
public record LanguageBundle(
        String language, FeatureSchema schema, ClassRegistry classes, OrthoProfile ortho, RuleSet rules) {

    public LanguageBundle {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(classes, "classes");
        Objects.requireNonNull(ortho, "ortho");
        Objects.requireNonNull(rules, "rules");
    }
}
