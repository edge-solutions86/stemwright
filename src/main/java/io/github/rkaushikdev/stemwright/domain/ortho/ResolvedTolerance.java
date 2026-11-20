package io.github.rkaushikdev.stemwright.domain.ortho;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The level that applies to one answer, and where it came from. The source comes back with the level
 * because a learner who is marked down at a tolerance their deck did not ask for is owed an answer
 * about where the tightening came from.
 *
 * @param level the level that applies
 * @param source the place that named it
 */
public record ResolvedTolerance(ToleranceLevel level, Source source) {

    /** Where a resolved level was named, from the most specific place to the least. */
    public enum Source {
        ASSIGNMENT("assignment"),
        CELL("cell"),
        DECK("deck"),
        LANGUAGE("language");

        private final String key;

        Source(String key) {
            this.key = key;
        }

        /** The word a description and a stored record use, such as {@code deck}. */
        public String key() {
            return key;
        }

        public static Optional<Source> fromKey(String key) {
            return Arrays.stream(values())
                    .filter(source -> source.key.equals(key))
                    .findFirst();
        }
    }

    /**
     * Reads the level out in words and says where it came from, such as {@code Lenient will ignore
     * capitals and ignore accents and breathings, set by the cell}.
     */
    public String describe() {
        List<String> folds = Folding.describeFolds(level.folds());
        String from = "set by the " + source.key();
        if (folds.isEmpty()) {
            return level.label() + " marks exactly as written, " + from;
        }
        return level.label() + " will " + String.join(" and ", folds) + ", " + from;
    }
}
