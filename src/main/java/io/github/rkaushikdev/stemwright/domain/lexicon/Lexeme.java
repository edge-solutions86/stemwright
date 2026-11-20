package io.github.rkaushikdev.stemwright.domain.lexicon;

import io.github.rkaushikdev.stemwright.domain.IdKind;
import io.github.rkaushikdev.stemwright.domain.Identifiers;
import io.github.rkaushikdev.stemwright.domain.errors.ProblemCollector;
import io.github.rkaushikdev.stemwright.domain.features.FeatureBundle;
import io.github.rkaushikdev.stemwright.domain.text.Collation;
import io.github.rkaushikdev.stemwright.domain.text.Normalise;
import io.github.rkaushikdev.stemwright.domain.text.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * A lexeme is a dictionary word, not a form of one. {@code oikos} is a lexeme and {@code oikou} is
 * a cell of it.
 *
 * <p>Inherent features are the ones the word carries rather than chooses. A Latin noun has a gender
 * and does not decline for it, so gender is inherent, and a bundle asking for a gender the lexeme
 * does not have is asking about a cell that does not exist. An adjective is the opposite case: it
 * declines for gender, so gender is not inherent and every gender is a real cell.
 *
 * @param id the derived lexeme id, see {@link #idFor}
 * @param language the language key, taken from the class registry
 * @param lemma the dictionary form, normalised
 * @param classKey the key of the inflection class it follows
 * @param stems the stems the endings attach to
 * @param inherent the features the word carries rather than chooses
 * @param glosses the glosses, the primary one first
 * @param homograph which of several lexemes sharing the lemma this is, from one
 * @param tags the tags, as slugs
 * @param note an editor's remark on the lexeme
 */
public record Lexeme(
        String id,
        String language,
        String lemma,
        String classKey,
        StemSet stems,
        FeatureBundle inherent,
        List<String> glosses,
        int homograph,
        List<String> tags,
        Optional<String> note) {

    /** {@link #compare} as a comparator: word list order. */
    public static final Comparator<Lexeme> ORDER = Lexeme::compare;

    private static final int LEMMA_LIMIT = 96;
    private static final int GLOSS_LIMIT = 200;
    private static final int NOTE_LIMIT = 500;

    public Lexeme {
        glosses = List.copyOf(glosses);
        tags = List.copyOf(tags);
    }

    /**
     * The id is derived rather than allocated, from the language, the lemma and the homograph
     * index. Re importing the same lexicon therefore produces the same ids, which is what makes an
     * import idempotent and a journal replay safe. The lemma is normalised and lower cased first, so
     * case and surrounding space do not change the id.
     */
    public static String idFor(String language, String lemma, int homograph) {
        return Identifiers.derivedId(
                IdKind.LEXEME, language, Text.lower(Normalise.normaliseInput(lemma)), Integer.toString(homograph));
    }

    /**
     * Checks a lexeme as written. The class, the homograph index, the inherent features and repeated
     * tags are reported together; a blank lemma, a class key that is not a slug, an unusable gloss
     * or tag, and an unusable note fail on the spot.
     */
    public static Lexeme of(LexemeContext context, LexemeInput input) {
        ProblemCollector problems = new ProblemCollector();
        String lemma = Normalise.requireText("lemma", input.lemma(), LEMMA_LIMIT);
        String classKey = Identifiers.asSlug("classKey", input.classKey());
        String language = context.classes().language();

        if (context.classes().classOf(classKey).isEmpty()) {
            problems.add("classKey", "is not a class of " + language + ": " + classKey);
        }

        int homograph = input.homograph();
        problems.addIf(homograph < 1, "homograph", "must be a positive whole number: " + homograph);

        FeatureBundle inherent = problems.absorb("inherent", () -> FeatureBundle.of(context.schema(), input.inherent()))
                .orElse(FeatureBundle.EMPTY);

        List<String> glosses = eachChecked(
                "glosses", input.glosses(), (path, gloss) -> Normalise.requireText(path, gloss, GLOSS_LIMIT));
        List<String> tags = eachChecked("tags", input.tags(), Identifiers::asSlug);
        problems.addIf(new HashSet<>(tags).size() != tags.size(), "tags", "holds the same tag twice");

        problems.throwIfAny("lexeme " + lemma + " is not usable");

        return new Lexeme(
                idFor(language, lemma, homograph),
                language,
                lemma,
                classKey,
                input.stems(),
                inherent,
                glosses,
                homograph,
                tags,
                input.note().map(note -> Normalise.requireText("note", note, NOTE_LIMIT)));
    }

    /** Checks each value under its own indexed path, such as {@code glosses.1}, failing on the first. */
    private static List<String> eachChecked(
            String path, List<String> values, BiFunction<String, String, String> check) {
        List<String> checked = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index += 1) {
            checked.add(check.apply(path + "." + index, values.get(index)));
        }
        return checked;
    }

    /** The form printed in a word list: the lemma, with the homograph index after a later homograph. */
    public String citationForm() {
        return homograph == 1 ? lemma : lemma + " (" + homograph + ")";
    }

    public Optional<String> primaryGloss() {
        return glosses.stream().findFirst();
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    /**
     * A bundle is answerable for a lexeme when it does not contradict the inherent features. Asking
     * a masculine noun for its feminine genitive is not a hard question, it is a question with no
     * answer.
     */
    public boolean contradictsInherent(FeatureBundle bundle) {
        return inherent.pairs().stream().anyMatch(pair -> bundle.valueOf(pair.dimension())
                .filter(asked -> !asked.equals(pair.value()))
                .isPresent());
    }

    /** The canonical key of the inherent features, such as {@code gender=fem}. */
    public String inherentKey() {
        return inherent.key();
    }

    /** True when both are the same lemma of the same language, whatever the case. */
    public boolean sameLemma(Lexeme other) {
        return language.equals(other.language) && Text.lower(lemma).equals(Text.lower(other.lemma));
    }

    /** Orders by lemma as a reader sorts words, then by homograph index. */
    public static int compare(Lexeme a, Lexeme b) {
        int byLemma = Collation.compare(a.lemma, b.lemma);
        return byLemma != 0 ? byLemma : Integer.compare(a.homograph, b.homograph);
    }
}
