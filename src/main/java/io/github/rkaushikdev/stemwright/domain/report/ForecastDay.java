package io.github.rkaushikdev.stemwright.domain.report;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One day of a forecast: how many reviews fall due on it as the ledger stands.
 *
 * @param on the study day
 * @param due how many cells fall due that day, the first day also holding everything already overdue
 * @param overCeiling whether that is more than the policy allows in a day
 * @param resting whether the policy rests on that weekday, so the reviews spill onto the next day
 */
public record ForecastDay(LocalDate on, int due, boolean overCeiling, boolean resting) {

    public ForecastDay {
        Objects.requireNonNull(on, "on");
    }
}
