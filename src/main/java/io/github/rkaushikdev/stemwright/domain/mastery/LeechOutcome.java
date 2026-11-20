package io.github.rkaushikdev.stemwright.domain.mastery;

import java.util.Objects;
import java.util.Optional;

/**
 * What a leech policy found when it looked at one cell.
 *
 * @param cellId the cell that was looked at
 * @param fired whether the cell was a leech
 * @param action what the policy did about it, present exactly when it fired
 * @param lapses the lapses the policy counted, whether it fired or not
 */
public record LeechOutcome(String cellId, boolean fired, Optional<LeechAction> action, int lapses) {

    public LeechOutcome {
        Objects.requireNonNull(cellId, "cellId");
        Objects.requireNonNull(action, "action");
    }
}
