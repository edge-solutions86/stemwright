package io.github.rkaushikdev.stemwright.domain.drill;

import io.github.rkaushikdev.stemwright.domain.features.FeatureSchema;
import io.github.rkaushikdev.stemwright.domain.lexicon.ClassRegistry;
import io.github.rkaushikdev.stemwright.domain.lexicon.Lexicon;
import io.github.rkaushikdev.stemwright.domain.paradigm.ProfileBook;
import java.util.Objects;

/**
 * Everything the drill layer needs to turn a deck back into cells for one language: the schema the
 * bundles are written in, the class tree, the book binding classes to profile revisions, and the
 * lexicon. The burial rule reads the same four things to find the twins of a cell.
 *
 * @param schema the feature schema of the language
 * @param classes the inflection classes of the language
 * @param book the profile histories and their bindings
 * @param lexicon the lexemes of the language
 */
public record DrillContext(FeatureSchema schema, ClassRegistry classes, ProfileBook book, Lexicon lexicon) {

    public DrillContext {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(classes, "classes");
        Objects.requireNonNull(book, "book");
        Objects.requireNonNull(lexicon, "lexicon");
    }
}
