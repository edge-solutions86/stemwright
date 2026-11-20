package io.github.rkaushikdev.stemwright.domain.paradigm;

import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.features.FeatureBundle;
import io.github.rkaushikdev.stemwright.domain.features.FeatureSchema;
import io.github.rkaushikdev.stemwright.domain.features.Notation;
import io.github.rkaushikdev.stemwright.domain.lexicon.Lexeme;
import io.github.rkaushikdev.stemwright.domain.text.Text;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A short human reference to a cell, such as {@code puella/gen.sg} or {@code malus(2)/nom.pl}, as
 * the cli and the logs print it. The lemma is left as text rather than resolved here, because
 * resolving it needs a lexicon and this layer is meant to stay usable without one.
 *
 * @param lemma the lemma as written
 * @param homograph the homograph index, one when the reference gives none
 * @param notation the bundle in the dotted notation, as written
 */
public record CellRef(String lemma, int homograph, String notation) {

    /**
     * A lemma, optional spacing, then a homograph index in parentheses closing the text. The lemma
     * part refuses only the four line terminators and the spacing is the desk's own set, because
     * Java's dot and {@code \s} each draw that line somewhere else.
     */
    private static final Pattern WITH_HOMOGRAPH =
            Pattern.compile("([^\\n\\r\\u2028\\u2029]+?)(?:" + Text.SPACE_RUN.pattern() + ")?\\((\\d+)\\)");

    /** The citation form, a slash, then the bundle in abbreviations. */
    public static String format(FeatureSchema schema, Lexeme entry, FeatureBundle bundle) {
        return entry.citationForm() + "/" + Notation.formatBundle(schema, bundle);
    }

    /** {@link #parse(String, String)} reporting failures under {@code cell}. */
    public static CellRef parse(String text) {
        return parse(text, "cell");
    }

    /**
     * Reads a reference back into its parts, splitting at the last slash and tolerating spacing
     * around the slash and before the homograph marker.
     *
     * @throws ValidationException when either side of the slash is missing, or the homograph index
     *     is below one or too large for any lexeme to have
     */
    public static CellRef parse(String text, String path) {
        int slash = text.lastIndexOf('/');
        if (slash <= 0 || slash == text.length() - 1) {
            throw ValidationException.field(path, "is not a lemma and a bundle: " + text);
        }
        String head = Text.trim(text.substring(0, slash));
        String notation = Text.trim(text.substring(slash + 1));

        Matcher match = WITH_HOMOGRAPH.matcher(head);
        if (!match.matches()) {
            return new CellRef(head, 1, notation);
        }
        int homograph;
        try {
            homograph = Integer.parseInt(match.group(2));
        } catch (NumberFormatException tooLarge) {
            throw ValidationException.field(path, "has a homograph index no lexeme can have: " + text);
        }
        if (homograph < 1) {
            throw ValidationException.field(path, "has a homograph index below one: " + text);
        }
        return new CellRef(Text.trim(match.group(1)), homograph, notation);
    }
}
