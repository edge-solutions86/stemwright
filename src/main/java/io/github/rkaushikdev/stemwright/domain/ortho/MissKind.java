package io.github.rkaushikdev.stemwright.domain.ortho;

import io.github.rkaushikdev.stemwright.domain.text.Normalise;
import io.github.rkaushikdev.stemwright.domain.text.Text;
import java.util.Arrays;
import java.util.Optional;

/** What the learner most likely got wrong, for the hint rather than the mark. */
public enum MissKind {
    ACCENT("accent"),
    CAPITAL("capital"),
    ENDING("ending"),
    STEM("stem"),
    ORDER("order"),
    SPACING("spacing"),
    OTHER("other");

    private final String key;

    MissKind(String key) {
        this.key = key;
    }

    /** The word a hint and a description print, such as {@code ending}. */
    public String key() {
        return key;
    }

    public static Optional<MissKind> fromKey(String key) {
        return Arrays.stream(values()).filter(kind -> kind.key.equals(key)).findFirst();
    }

    /**
     * Names the likeliest miss, asking in turn whether the two forms differ only in spacing, only in
     * capitals, only in accents, or only by one adjacent swap. Failing all of those, a miss that
     * keeps the first half of the shorter form is the ending and any other is the stem. Two forms
     * that are the same once normalised are {@link #OTHER}.
     */
    public static MissKind classify(String expected, String given) {
        String left = Normalise.normaliseInput(expected);
        String right = Normalise.normaliseInput(given);
        if (left.equals(right)) {
            return OTHER;
        }
        if (withoutSpaces(left).equals(withoutSpaces(right))) {
            return SPACING;
        }
        if (Text.lower(left).equals(Text.lower(right))) {
            return CAPITAL;
        }
        if (Normalise.stripMarks(left).equals(Normalise.stripMarks(right))) {
            return ACCENT;
        }
        if (Distance.isTransposition(left, right)) {
            return ORDER;
        }

        int shared = Distance.sharedPrefixLength(left, right);
        int stemLength = Math.ceilDiv(Math.min(left.length(), right.length()), 2);
        return shared >= stemLength ? ENDING : STEM;
    }

    private static String withoutSpaces(String text) {
        return Text.SPACE_RUN.matcher(text).replaceAll("");
    }
}
