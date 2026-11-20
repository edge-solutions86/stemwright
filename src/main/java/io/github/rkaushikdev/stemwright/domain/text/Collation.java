package io.github.rkaushikdev.stemwright.domain.text;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The order a person expects a list of words in: the root collation order, compared at three
 * levels.
 *
 * <p>This is the Unicode root collation, the order an ICU collator gives for an English locale.
 * Java's own {@link java.text.Collator} disagrees with it on punctuation (it ignores a hyphen and a
 * space) and on Greek case, so it cannot stand in. The comparison runs in three passes, each
 * consulted only when the one before is a tie:
 *
 * <ol>
 *   <li>the letters, digits and punctuation, ignoring accents and case: spacing sorts before
 *       punctuation, punctuation before digits, and digits before letters, with Latin before
 *       Greek before Hebrew in code point order;
 *   <li>the accents, an unmarked letter sorting before a marked one;
 *   <li>the case, a lower case letter sorting before its capital.
 * </ol>
 *
 * <p>Two strings that tie at all three levels compare as equal, so a stable sort keeps them in
 * the order it found them. Control characters are ignored entirely. The order of the ascii
 * punctuation follows the root table; other symbols sort after it by code point, and accents
 * order among themselves by code point. Those last two are the only places it is an approximation,
 * and neither is reachable from a lemma, a key or an id.
 */
public final class Collation {

    /** The comparator form, for {@link java.util.List#sort} and the stream api. */
    public static final Comparator<String> ORDER = Collation::compare;

    /** The ascii punctuation and symbols, in root collation order. */
    private static final String ASCII_PUNCTUATION = "_-,;:!?.'\"()[]{}@*/\\&#%`^+<=>|~$";

    private static final int SPACING = 0x100;
    private static final int PUNCTUATION = 0x200;
    private static final int OTHER_SYMBOL = 0x1000;
    private static final int DIGIT = 0x20_0000;
    private static final int LETTER = 0x30_0000;

    private static final int GREEK_SMALL_FINAL_SIGMA = 0x03c2;
    private static final int GREEK_SMALL_SIGMA = 0x03c3;

    private Collation() {}

    /** Negative, zero or positive as {@code left} sorts before, with or after {@code right}. */
    public static int compare(String left, String right) {
        if (left.equals(right)) {
            return 0;
        }
        List<Element> a = elements(left);
        List<Element> b = elements(right);
        int byPrimary = compareLevel(a, b, 0);
        if (byPrimary != 0) {
            return byPrimary;
        }
        int bySecondary = compareLevel(a, b, 1);
        if (bySecondary != 0) {
            return bySecondary;
        }
        return compareLevel(a, b, 2);
    }

    private record Element(int primary, int secondary, int tertiary) {

        int weight(int level) {
            return switch (level) {
                case 0 -> primary;
                case 1 -> secondary;
                default -> tertiary;
            };
        }
    }

    private static int compareLevel(List<Element> a, List<Element> b, int level) {
        List<Integer> left = weights(a, level);
        List<Integer> right = weights(b, level);
        int shared = Math.min(left.size(), right.size());
        for (int index = 0; index < shared; index += 1) {
            int difference = Integer.compare(left.get(index), right.get(index));
            if (difference != 0) {
                return difference;
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    private static List<Integer> weights(List<Element> elements, int level) {
        List<Integer> out = new ArrayList<>(elements.size());
        for (Element element : elements) {
            int weight = element.weight(level);
            if (level != 0 || weight != 0) {
                out.add(weight);
            }
        }
        return out;
    }

    private static List<Element> elements(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        List<Element> out = new ArrayList<>(decomposed.length());
        decomposed.codePoints().forEach(point -> {
            Element element = elementOf(point);
            if (element != null) {
                out.add(element);
            }
        });
        return out;
    }

    /** The weights of one code point, or {@code null} for one the order ignores entirely. */
    private static Element elementOf(int point) {
        int type = Character.getType(point);
        if (type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK) {
            return new Element(0, 1 + point, 0);
        }
        if (type == Character.CONTROL && !Text.isSpace(point) || type == Character.FORMAT) {
            return null;
        }
        if (Text.isSpace(point)) {
            return new Element(SPACING + spacingRank(point), 0, point == ' ' ? 0 : 1);
        }
        if (Character.isLetter(point)) {
            return letter(point);
        }
        if (Character.isDigit(point)) {
            int value = Character.digit(point, 10);
            return new Element(DIGIT + value, 0, point < 0x80 ? 0 : 1);
        }
        int ascii = point < 0x80 ? ASCII_PUNCTUATION.indexOf(point) : -1;
        if (ascii >= 0) {
            return new Element(PUNCTUATION + ascii, 0, 0);
        }
        return new Element(OTHER_SYMBOL + point, 0, 0);
    }

    private static Element letter(int point) {
        if (point == GREEK_SMALL_FINAL_SIGMA) {
            return new Element(LETTER + GREEK_SMALL_SIGMA, 0, 2);
        }
        int folded = Character.toLowerCase(point);
        int tertiary = folded == point && !Character.isUpperCase(point) && !Character.isTitleCase(point) ? 0 : 1;
        return new Element(LETTER + folded, 0, tertiary);
    }

    private static int spacingRank(int point) {
        return switch (point) {
            case 0x09 -> 1;
            case 0x0a -> 2;
            case 0x0b -> 3;
            case 0x0c -> 4;
            case 0x0d -> 5;
            case 0x2028 -> 6;
            case 0x2029 -> 7;
            default -> 8;
        };
    }
}
