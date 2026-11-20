package io.github.rkaushikdev.stemwright.support;

import io.github.rkaushikdev.stemwright.domain.features.FeatureSchema;
import io.github.rkaushikdev.stemwright.domain.features.FeatureSchemaInput;
import io.github.rkaushikdev.stemwright.domain.features.FeatureSchemaInput.Dimension;
import io.github.rkaushikdev.stemwright.domain.lexicon.Lexeme;
import io.github.rkaushikdev.stemwright.domain.lexicon.LexemeContext;
import io.github.rkaushikdev.stemwright.domain.lexicon.LexemeInput;
import io.github.rkaushikdev.stemwright.domain.lexicon.StemInput;
import io.github.rkaushikdev.stemwright.domain.lexicon.StemSet;
import java.util.Map;

/**
 * Three Latin nouns and the context they are checked against, shared by the lexicon tests.
 *
 * <p>{@link #PUELLA} is a tagged first declension feminine, {@link #BELLUM} a second declension
 * neuter with two glosses and no tags, and {@link #AGER} a second declension r stem masculine.
 */
public final class Lexemes {

    private Lexemes() {}

    /** Latin nouns with gender as an optional dimension, so it can be inherent. */
    public static final FeatureSchema LATIN_FULL_SCHEMA = FeatureSchema.of(FeatureSchemaInput.of(
            "latin",
            Dimension.of("case", "nom", "gen", "dat", "acc", "abl", "voc"),
            Dimension.of("number", "sg", "pl"),
            Dimension.optional("gender", "masc", "fem", "neut")));

    public static final LexemeContext LATIN_CONTEXT = new LexemeContext(LATIN_FULL_SCHEMA, Classes.LATIN);

    public static final Lexeme PUELLA =
            makeLexeme(LexemeInput.builder("puella", "first-declension", StemSet.of(StemInput.of("base", "puell")))
                    .inherent(Map.of("gender", "fem"))
                    .glosses("girl")
                    .tags("core", "chapter-one")
                    .build());

    public static final Lexeme BELLUM = makeLexeme(
            LexemeInput.builder("bellum", "second-declension.neuter", StemSet.of(StemInput.of("base", "bell")))
                    .inherent(Map.of("gender", "neut"))
                    .glosses("war", "campaign")
                    .build());

    public static final Lexeme AGER =
            makeLexeme(LexemeInput.builder("ager", "second-declension.r-stem", StemSet.of(StemInput.of("base", "agr")))
                    .inherent(Map.of("gender", "masc"))
                    .glosses("field")
                    .build());

    /** Checks the input against {@link #LATIN_CONTEXT}. */
    public static Lexeme makeLexeme(LexemeInput input) {
        return Lexeme.of(LATIN_CONTEXT, input);
    }
}
