package io.github.rkaushikdev.stemwright.domain.text;

import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Puts learner input into one shape before anything compares it.
 *
 * <p>Input arrives from a browser field, a csv column or a cli argument, and the three disagree
 * about normalisation form, invisible marks and what counts as a space. Everything that will be
 * compared goes through here first, so a comparison never has to ask where its input came from.
 *
 * <p>Nothing here is written as a literal invisible character. Every code point that cannot be seen
 * in an editor is named by its number, because a paste that silently loses one would otherwise
 * change behaviour with no visible diff.
 */
public final class Normalise {

    /** Zero width space, zero width non joiner, zero width joiner, word joiner, byte order mark. */
    private static final Set<Integer> ZERO_WIDTH = Set.of(0x200b, 0x200c, 0x200d, 0x2060, 0xfeff);

    private static final int DELETE = 0x7f;
    private static final int UNIT_SEPARATOR = 0x1f;
    private static final int TAB = 0x09;
    private static final int LINE_FEED = 0x0a;
    private static final int CARRIAGE_RETURN = 0x0d;

    private static final Pattern MARKS = Pattern.compile("\\p{M}");

    private static final int DEFAULT_MAX_LENGTH = 512;

    private Normalise() {}

    public static String toForm(String value, Normalizer.Form form) {
        return Normalizer.normalize(value, form);
    }

    private static boolean isDroppable(int point) {
        if (ZERO_WIDTH.contains(point)) {
            return true;
        }
        if (point == TAB || point == LINE_FEED || point == CARRIAGE_RETURN) {
            return false;
        }
        return point <= UNIT_SEPARATOR || point == DELETE;
    }

    /** Removes marks the learner cannot see and never meant to type. */
    public static String stripInvisible(String value) {
        StringBuilder out = new StringBuilder(value.length());
        value.codePoints().filter(point -> !isDroppable(point)).forEach(out::appendCodePoint);
        return out.toString();
    }

    /**
     * Collapses every run of any space character to one plain space and trims. The set covers the
     * no break space, the en and em spaces and the ideographic space, which is exactly the set a
     * learner can produce by accident on a phone keyboard.
     */
    public static String collapseSpaces(String value) {
        return Text.trim(Text.SPACE_RUN.matcher(value).replaceAll(" "));
    }

    /** {@link #normaliseInput(String, Normalizer.Form)} into the composed form. */
    public static String normaliseInput(String value) {
        return normaliseInput(value, Normalizer.Form.NFC);
    }

    /**
     * The single entry point. Order matters: invisible marks go first so a zero width joiner cannot
     * survive inside a run of spaces, then spaces collapse, then the result is put in one
     * normalisation form.
     */
    public static String normaliseInput(String value, Normalizer.Form form) {
        return toForm(collapseSpaces(stripInvisible(value)), form);
    }

    public static boolean isBlank(String value) {
        return normaliseInput(value).isEmpty();
    }

    /** {@link #requireText(String, String, int)} with the default cap of 512 characters. */
    public static String requireText(String path, String value) {
        return requireText(path, value, DEFAULT_MAX_LENGTH);
    }

    /**
     * Normalises and returns the value, refusing a blank one and one longer than the cap. The cap
     * counts utf-16 units, as every length check in the desk does.
     */
    public static String requireText(String path, String value, int maxLength) {
        String cleaned = normaliseInput(value);
        if (cleaned.isEmpty()) {
            throw ValidationException.field(path, "must not be blank");
        }
        if (cleaned.length() > maxLength) {
            throw ValidationException.field(path, "must be at most " + maxLength + " characters");
        }
        return cleaned;
    }

    /** Code points, not utf-16 units, so a surrogate pair counts once. */
    public static List<String> codePoints(String value) {
        return value.codePoints().mapToObj(Character::toString).toList();
    }

    public static int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    /**
     * Splits into base plus trailing combining marks. A Greek vowel with a breathing and an accent is
     * one unit to a learner and three code points to the platform, and every tolerance rule in the
     * orthography layer is stated over these units.
     */
    public static List<String> markClusters(String value) {
        List<String> clusters = new ArrayList<>();
        for (String point : codePoints(toForm(value, Normalizer.Form.NFD))) {
            if (!clusters.isEmpty() && isMark(point)) {
                int last = clusters.size() - 1;
                clusters.set(last, clusters.get(last) + point);
            } else {
                clusters.add(point);
            }
        }
        return clusters;
    }

    /** The first code point of a cluster, or the empty string for an empty one. */
    public static String baseOf(String cluster) {
        List<String> points = codePoints(cluster);
        return points.isEmpty() ? "" : points.get(0);
    }

    /** Every code point of a cluster after its base. */
    public static List<String> marksOf(String cluster) {
        List<String> points = codePoints(cluster);
        return points.isEmpty() ? List.of() : points.subList(1, points.size());
    }

    public static boolean hasMarks(String value) {
        return MARKS.matcher(toForm(value, Normalizer.Form.NFD)).find();
    }

    /** Drops every combining mark, leaving the bare letters. */
    public static String stripMarks(String value) {
        String bare = MARKS.matcher(toForm(value, Normalizer.Form.NFD)).replaceAll("");
        return toForm(bare, Normalizer.Form.NFC);
    }

    /** True when the code point is a combining mark of any kind. */
    public static boolean isMark(String point) {
        return MARKS.matcher(point).find();
    }
}
