package io.github.rkaushikdev.stemwright.domain.mastery;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One review a shield pushed out.
 *
 * @param cellId the cell whose review moved
 * @param from the day it was due, inside a shield
 * @param to the first open day after it, where it is due now
 */
public record ShieldOutcome(String cellId, LocalDate from, LocalDate to) {

    public ShieldOutcome {
        Objects.requireNonNull(cellId, "cellId");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
    }
}
