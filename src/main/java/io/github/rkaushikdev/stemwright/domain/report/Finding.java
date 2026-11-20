package io.github.rkaushikdev.stemwright.domain.report;

import java.util.Objects;

/**
 * One thing the health check found.
 *
 * @param level how serious it is
 * @param area the part of the language it is about: {@code classes}, {@code profiles}, {@code
 *     lexicon} or {@code decks}
 * @param message what was found, as an editor reads it
 */
public record Finding(FindingLevel level, String area, String message) {

    public Finding {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(message, "message");
    }
}
