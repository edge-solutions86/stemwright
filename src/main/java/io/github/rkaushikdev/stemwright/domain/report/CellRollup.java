package io.github.rkaushikdev.stemwright.domain.report;

import io.github.rkaushikdev.stemwright.domain.mastery.AttemptTally;
import io.github.rkaushikdev.stemwright.domain.ortho.MatchVerdict;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Every attempt on one cell, the row a weak spot list ranks.
 *
 * @param cellId the cell
 * @param tally how its attempts were marked
 * @param accuracy the share of them that were correct
 * @param lastVerdict how the latest attempt was marked
 * @param lastSeenOn the day of the latest attempt
 */
public record CellRollup(
        String cellId, AttemptTally tally, double accuracy, MatchVerdict lastVerdict, LocalDate lastSeenOn) {

    public CellRollup {
        Objects.requireNonNull(cellId, "cellId");
        Objects.requireNonNull(tally, "tally");
        Objects.requireNonNull(lastVerdict, "lastVerdict");
        Objects.requireNonNull(lastSeenOn, "lastSeenOn");
    }
}
