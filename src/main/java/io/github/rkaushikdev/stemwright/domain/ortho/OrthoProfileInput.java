package io.github.rkaushikdev.stemwright.domain.ortho;

import java.util.List;
import java.util.Optional;

/**
 * An orthography profile as a caller writes it, before {@link OrthoProfile#of} checks it.
 *
 * @param language the language key, which becomes a slug
 * @param levels the levels in declaration order
 * @param defaultLevel the key of the level that applies when nothing more specific names one
 */
public record OrthoProfileInput(String language, List<Level> levels, String defaultLevel) {

    public OrthoProfileInput {
        levels = List.copyOf(levels);
    }

    public static OrthoProfileInput of(String language, String defaultLevel, Level... levels) {
        return new OrthoProfileInput(language, List.of(levels), defaultLevel);
    }

    /**
     * One tolerance level as written.
     *
     * @param key the level key, which becomes a slug
     * @param label the printed name, defaulted to the key when absent
     * @param folds the fold step keys, in any order and possibly repeated
     */
    public record Level(String key, Optional<String> label, List<String> folds) {

        public Level {
            folds = List.copyOf(folds);
        }

        /** A level printed under its own key. */
        public static Level of(String key, List<String> folds) {
            return new Level(key, Optional.empty(), folds);
        }

        /** A level printed under a label of its own. */
        public static Level of(String key, String label, List<String> folds) {
            return new Level(key, Optional.of(label), folds);
        }
    }
}
