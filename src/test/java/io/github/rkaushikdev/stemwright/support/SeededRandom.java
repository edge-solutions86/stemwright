package io.github.rkaushikdev.stemwright.support;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * A seeded generator, so the property tests are properties rather than flakes. Every run of the
 * suite draws the same values, and a failure can be reproduced by reading the seed out of the
 * message.
 *
 * <p>This is deliberately not a fuzzing library. The point is to cover a lot of ordinary inputs
 * cheaply, not to find adversarial ones. The generator is a 32 bit xorshift, so a seed gives the
 * same draws on every machine and every run.
 */
public final class SeededRandom {

    private static final String LETTERS = "abcdefghiklmnoprstuvx";
    private static final double TWO_TO_THE_32 = 4_294_967_296.0;

    private int state;

    public SeededRandom(long seed) {
        int truncated = (int) seed;
        this.state = truncated == 0 ? 0x9e3779b9 : truncated;
    }

    /** A double in [0, 1). */
    public double next() {
        state ^= state << 13;
        state ^= state >>> 17;
        state ^= state << 5;
        return Integer.toUnsignedLong(state) / TWO_TO_THE_32;
    }

    /** A whole number between the two bounds, both included. */
    public int nextInt(int lowest, int highest) {
        return lowest + (int) Math.floor(next() * (highest - lowest + 1));
    }

    public <T> T pick(List<T> items) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("cannot pick from an empty list");
        }
        return items.get(nextInt(0, items.size() - 1));
    }

    public <T> List<T> shuffle(List<T> items) {
        List<T> out = new ArrayList<>(items);
        for (int index = out.size() - 1; index > 0; index -= 1) {
            int swap = nextInt(0, index);
            T held = out.get(index);
            out.set(index, out.get(swap));
            out.set(swap, held);
        }
        return out;
    }

    public <T> List<T> sample(List<T> items, int count) {
        List<T> shuffled = shuffle(items);
        return new ArrayList<>(shuffled.subList(0, Math.min(count, shuffled.size())));
    }

    public boolean bool() {
        return bool(0.5);
    }

    public boolean bool(double chance) {
        return next() < chance;
    }

    public String word() {
        return word(3, 8);
    }

    public String word(int minimum, int maximum) {
        int length = nextInt(minimum, maximum);
        List<String> letters = LETTERS.chars().mapToObj(Character::toString).toList();
        StringBuilder out = new StringBuilder(length);
        for (int index = 0; index < length; index += 1) {
            out.append(pick(letters));
        }
        return out.toString();
    }

    /** Runs a property over a fixed number of draws and reports the first failure with its seed. */
    public static void forEachDraw(int count, long seed, BiConsumer<SeededRandom, Integer> body) {
        for (int draw = 0; draw < count; draw += 1) {
            long used = seed + draw * 7919L;
            try {
                body.accept(new SeededRandom(used), draw);
            } catch (AssertionError | RuntimeException error) {
                throw new AssertionError(
                        "property failed on draw " + draw + " with seed " + used + ": " + error.getMessage(), error);
            }
        }
    }
}
