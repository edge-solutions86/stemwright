package io.github.rkaushikdev.stemwright.domain.mastery;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * What a credit rule did to one twin of an answered cell.
 *
 * @param cellId the twin that was looked at
 * @param moved whether the twin's schedule changed at all
 * @param climbed whether the twin climbed a rung
 * @param from the twin's due date before, absent for a twin that was never scheduled
 * @param to the twin's due date after, the same as {@code from} when it did not move
 */
public record CreditOutcome(
        String cellId, boolean moved, boolean climbed, Optional<LocalDate> from, Optional<LocalDate> to) {

    public CreditOutcome {
        Objects.requireNonNull(cellId, "cellId");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
    }
}
