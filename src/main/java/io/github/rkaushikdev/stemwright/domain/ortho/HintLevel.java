package io.github.rkaushikdev.stemwright.domain.ortho;

import java.util.Arrays;
import java.util.Optional;

/**
 * The rungs of the hint ladder, declared in ladder order.
 *
 * <p>The order is by how much of the answer each one gives away, not by how much it says. Naming the
 * kind of miss is more informative than a skeleton and still reveals no letters, so it comes before
 * the opening rather than after it, and the ladder never goes backwards.
 */
public enum HintLevel {
    SHAPE("shape"),
    KIND("kind"),
    OPENING("opening"),
    REVEAL("reveal");

    private final String key;

    HintLevel(String key) {
        this.key = key;
    }

    /** The word a request names the level by and a hint is printed under, such as {@code opening}. */
    public String key() {
        return key;
    }

    public static Optional<HintLevel> fromKey(String key) {
        return Arrays.stream(values()).filter(level -> level.key.equals(key)).findFirst();
    }

    /** The next rung up, staying at {@link #REVEAL} once there. */
    public HintLevel next() {
        HintLevel[] ladder = values();
        return ladder[Math.min(ordinal() + 1, ladder.length - 1)];
    }
}
