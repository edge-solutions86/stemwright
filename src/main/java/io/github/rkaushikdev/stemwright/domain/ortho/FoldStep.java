package io.github.rkaushikdev.stemwright.domain.ortho;

import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.text.Normalise;
import io.github.rkaushikdev.stemwright.domain.text.Text;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * A fold: a named, reversible in spirit simplification of a written form.
 *
 * <p>Grading never compares raw strings: it folds both sides by the same list of steps and compares
 * the results, so what counts as the same answer is a property of the deck rather than of the
 * comparison code.
 *
 * <p>Every step is total and idempotent. Applying a list twice gives what applying it once gave,
 * which is what lets a fold list be stored on a deck, on a cell override and on a report without
 * anybody having to reason about order. The constants are declared in the order the steps run, which
 * is {@link Folding#ORDER}.
 */
public enum FoldStep {
    PUNCTUATION("punctuation", "ignore punctuation", FoldStep::dropPunctuation),
    LOWERCASE("lowercase", "ignore capitals", Text::lower),
    FINAL_FORMS("final-forms", "accept a medial letter at the end of a word", FoldStep::foldFinalForms),
    LENGTH_MARKS("length-marks", "ignore macrons and breves", FoldStep::foldLengthMarks),
    MARKS("marks", "ignore accents and breathings", Normalise::stripMarks),
    SPACES("spaces", "ignore spacing", FoldStep::dropSpaces);

    /**
     * Letters that take a different shape at the end of a word, mapped to their medial shape. A
     * learner typing on a keyboard that does not switch automatically produces the medial form, and
     * whether that counts as wrong is a teaching decision, not a technical one.
     *
     * <p>Named by number because a pair of right to left letters written on one line displays in the
     * opposite order in most editors, which would make the Hebrew pairs read backwards.
     */
    private static final Map<Integer, Integer> MEDIAL_FORMS = Map.of(
            0x03c2, 0x03c3, // Greek final sigma
            0x05da, 0x05db, // Hebrew final kaf
            0x05dd, 0x05de, // Hebrew final mem
            0x05df, 0x05e0, // Hebrew final nun
            0x05e3, 0x05e4, // Hebrew final pe
            0x05e5, 0x05e6); // Hebrew final tsadi

    /** The combining macron and breve, marks that only say how long a vowel is and many editions omit. */
    private static final Set<Integer> LENGTH_MARK_POINTS = Set.of(0x0304, 0x0306);

    private static final Pattern PUNCTUATION_MARK = Pattern.compile("[.,;:!?'\"()\\[\\]{}]");

    private final String key;
    private final String label;
    private final UnaryOperator<String> fold;

    FoldStep(String key, String label, UnaryOperator<String> fold) {
        this.key = key;
        this.label = label;
        this.fold = fold;
    }

    /** The key a deck, a cell override and a report store the step under, such as {@code final-forms}. */
    public String key() {
        return key;
    }

    /** What the step forgives, in the words a learner is shown. */
    public String label() {
        return label;
    }

    /** Applies this step alone, to text that is already normalised. */
    public String apply(String text) {
        return fold.apply(text);
    }

    public static Optional<FoldStep> fromKey(String key) {
        return Arrays.stream(values()).filter(step -> step.key.equals(key)).findFirst();
    }

    /**
     * The step stored under the key.
     *
     * @throws ValidationException on the {@code fold} field when no step has that key
     */
    public static FoldStep require(String key) {
        return fromKey(key).orElseThrow(() -> ValidationException.field("fold", "is not a fold step: " + key));
    }

    private static String dropPunctuation(String text) {
        return PUNCTUATION_MARK.matcher(text).replaceAll("");
    }

    private static String foldFinalForms(String text) {
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().map(point -> MEDIAL_FORMS.getOrDefault(point, point)).forEach(out::appendCodePoint);
        return out.toString();
    }

    private static String foldLengthMarks(String text) {
        StringBuilder out = new StringBuilder(text.length());
        Normalise.toForm(text, Normalizer.Form.NFD)
                .codePoints()
                .filter(point -> !LENGTH_MARK_POINTS.contains(point))
                .forEach(out::appendCodePoint);
        return Normalise.toForm(out.toString(), Normalizer.Form.NFC);
    }

    private static String dropSpaces(String text) {
        return Text.SPACE_RUN.matcher(text).replaceAll("");
    }
}
