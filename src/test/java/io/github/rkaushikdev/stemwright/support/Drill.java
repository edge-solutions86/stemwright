package io.github.rkaushikdev.stemwright.support;

import io.github.rkaushikdev.stemwright.domain.drill.DrillContext;
import io.github.rkaushikdev.stemwright.domain.lexicon.Lexeme;
import io.github.rkaushikdev.stemwright.domain.lexicon.LexemeContext;
import io.github.rkaushikdev.stemwright.domain.lexicon.LexemeInput;
import io.github.rkaushikdev.stemwright.domain.lexicon.Lexicon;
import io.github.rkaushikdev.stemwright.domain.lexicon.StemInput;
import io.github.rkaushikdev.stemwright.domain.lexicon.StemSet;
import java.util.List;

/**
 * The Latin drill context the drill tests share: four nouns checked against {@link
 * Profiles#LATIN_NOUN}, which has no gender, and drilled through {@link Book#LATIN_BOOK}.
 *
 * <p>These are not the nouns of {@link Lexemes}: they carry no inherent gender, so every slot of
 * their profile is a cell, and their tags are the ones the deck tests narrow by.
 */
public final class Drill {

    private Drill() {}

    private static final LexemeContext LEXEME_CONTEXT = new LexemeContext(Profiles.LATIN_NOUN, Classes.LATIN);

    /** First declension, tagged core. */
    public static final Lexeme PUELLA = lexeme("puella", "first-declension", "puell", "girl", "core");

    /** Second declension neuter, tagged core. */
    public static final Lexeme BELLUM = lexeme("bellum", "second-declension.neuter", "bell", "war", "core");

    /** Second declension r stem, tagged advanced, drilled through the neuter profile its parent is bound to. */
    public static final Lexeme AGER = lexeme("ager", "second-declension.r-stem", "agr", "field", "advanced");

    /** Third declension is deliberately unbound in the book, so it gets skipped. */
    public static final Lexeme REX = lexeme("rex", "third-declension", "reg", "king", "core");

    public static final Lexicon LATIN_LEXICON = Lexicon.build("latin", List.of(PUELLA, BELLUM, AGER, REX));

    public static final DrillContext DRILL_CONTEXT =
            new DrillContext(Profiles.LATIN_NOUN, Classes.LATIN, Book.LATIN_BOOK, LATIN_LEXICON);

    private static Lexeme lexeme(String lemma, String classKey, String stem, String gloss, String tag) {
        return Lexeme.of(
                LEXEME_CONTEXT,
                LexemeInput.builder(lemma, classKey, StemSet.of(StemInput.of("base", stem)))
                        .glosses(gloss)
                        .tags(tag)
                        .build());
    }
}
