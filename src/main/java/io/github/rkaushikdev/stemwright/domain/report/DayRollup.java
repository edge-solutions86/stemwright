package io.github.rkaushikdev.stemwright.domain.report;

import io.github.rkaushikdev.stemwright.domain.mastery.AttemptTally;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One day of a learner's attempts, the row a progress chart plots.
 *
 * @param on the study day
 * @param tally how that day's attempts were marked
 * @param accuracy the share of them that were correct, zero on a day with none
 * @param distinctCells how many different cells were answered, however many times each
 */
public record DayRollup(LocalDate on, AttemptTally tally, double accuracy, int distinctCells) {

    public DayRollup {
        Objects.requireNonNull(on, "on");
        Objects.requireNonNull(tally, "tally");
    }
}
