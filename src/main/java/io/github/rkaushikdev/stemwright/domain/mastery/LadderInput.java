package io.github.rkaushikdev.stemwright.domain.mastery;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A ladder as a caller writes it, before {@link Ladder#of} checks it and fills in the label, the
 * lapse drop and the spread it left out.
 *
 * @param key the ladder key, which becomes a slug
 * @param label the printed name, defaulted to the key when absent
 * @param rungs the days to wait at each rung, which must strictly increase
 * @param lapseDrop how many rungs a lapse costs, two when absent
 * @param spread the fraction of an interval that may be spread either way, a tenth when absent
 */
public record LadderInput(
        String key, Optional<String> label, List<Integer> rungs, Optional<Integer> lapseDrop, Optional<Double> spread) {

    public LadderInput {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(label, "label");
        rungs = List.copyOf(rungs);
        Objects.requireNonNull(lapseDrop, "lapseDrop");
        Objects.requireNonNull(spread, "spread");
    }

    /** A ladder that takes every default. */
    public static LadderInput of(String key, int... rungs) {
        return builder(key, rungs).build();
    }

    public static Builder builder(String key, int... rungs) {
        return new Builder(key, Arrays.stream(rungs).boxed().toList());
    }

    /** Builds a {@link LadderInput}, leaving out whatever is not set. */
    public static final class Builder {

        private final String key;
        private final List<Integer> rungs;
        private Optional<String> label = Optional.empty();
        private Optional<Integer> lapseDrop = Optional.empty();
        private Optional<Double> spread = Optional.empty();

        private Builder(String key, List<Integer> rungs) {
            this.key = key;
            this.rungs = rungs;
        }

        public Builder label(String value) {
            label = Optional.of(value);
            return this;
        }

        public Builder lapseDrop(int value) {
            lapseDrop = Optional.of(value);
            return this;
        }

        public Builder spread(double value) {
            spread = Optional.of(value);
            return this;
        }

        public LadderInput build() {
            return new LadderInput(key, label, rungs, lapseDrop, spread);
        }
    }
}
