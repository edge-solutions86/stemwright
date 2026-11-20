package io.github.rkaushikdev.stemwright.domain.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The string primitives every layer leans on, fixed to one meaning.
 *
 * <p>Java's own {@code trim}, {@code split} and {@code toLowerCase} each answer a slightly
 * different question from the one this desk asks: {@code trim} stops at U+0020, {@code split}
 * takes a regular expression and drops trailing empty parts, and {@code toLowerCase} follows the
 * default locale. The functions here are the ones the domain means, and every caller goes through
 * them so that a Turkish laptop and a Linux runner agree.
 */
public final class Text {

    /**
     * Every code point the desk treats as spacing: the ascii spaces and line ends, the no break
     * space, the Ogham space mark, the en to hair spaces, the line and paragraph separators, the
     * narrow no break space, the medium mathematical space, the ideographic space and the byte
     * order mark. Named by number, because none of them can be seen in an editor.
     */
    private static final String SPACE_CLASS =
            "[\\u0009-\\u000D\\u0020\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]";

    /** One or more spacing code points, as {@link #isSpace(int)} defines them. */
    public static final Pattern SPACE_RUN = Pattern.compile(SPACE_CLASS + "+");

    private Text() {}

    /** True for every code point {@link #SPACE_RUN} matches. */
    public static boolean isSpace(int point) {
        return (point >= 0x09 && point <= 0x0d)
                || point == 0x20
                || point == 0xa0
                || point == 0x1680
                || (point >= 0x2000 && point <= 0x200a)
                || point == 0x2028
                || point == 0x2029
                || point == 0x202f
                || point == 0x205f
                || point == 0x3000
                || point == 0xfeff;
    }

    /** Removes spacing, as {@link #isSpace(int)} defines it, from both ends. */
    public static String trim(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isSpace(value.charAt(start))) {
            start += 1;
        }
        while (end > start && isSpace(value.charAt(end - 1))) {
            end -= 1;
        }
        return value.substring(start, end);
    }

    /** Lower cases by the Unicode default mapping, whatever the machine's locale. */
    public static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    /** Upper cases by the Unicode default mapping, whatever the machine's locale. */
    public static String upper(String value) {
        return value.toUpperCase(Locale.ROOT);
    }

    /**
     * Splits on a literal separator and keeps every part, empty ones included, so that
     * {@code split("a;;b;", ";")} is four parts and {@code split("", ";")} is one.
     */
    public static List<String> split(String value, String separator) {
        if (separator.isEmpty()) {
            throw new IllegalArgumentException("separator must not be empty");
        }
        List<String> parts = new ArrayList<>();
        int from = 0;
        int at = value.indexOf(separator, from);
        while (at >= 0) {
            parts.add(value.substring(from, at));
            from = at + separator.length();
            at = value.indexOf(separator, from);
        }
        parts.add(value.substring(from));
        return parts;
    }

    /** Pads on the left with the filler until the value is at least the given length. */
    public static String padStart(String value, int length, char filler) {
        if (value.length() >= length) {
            return value;
        }
        return String.valueOf(filler).repeat(length - value.length()) + value;
    }

    /** Pads on the right with spaces until the value is at least the given length. */
    public static String padEnd(String value, int length) {
        if (value.length() >= length) {
            return value;
        }
        return value + " ".repeat(length - value.length());
    }

    /** Pads on the left with spaces until the value is at least the given length. */
    public static String padStart(String value, int length) {
        return padStart(value, length, ' ');
    }
}
