package io.github.rkaushikdev.stemwright.domain.report;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One day of an optimistic projection, which assumes every answer is right.
 *
 * @param on the study day
 * @param reviews how many cells come back for review that day
 * @param introductions how many new cells are introduced that day
 */
public record ProjectionDay(LocalDate on, int reviews, int introductions) {

    public ProjectionDay {
        Objects.requireNonNull(on, "on");
    }
}
