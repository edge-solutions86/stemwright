package io.github.rkaushikdev.stemwright.domain.mastery;

import java.util.List;
import java.util.Objects;

/**
 * What a credit rule did across every twin of an answered cell.
 *
 * @param ledger the ledger with every moved twin written back
 * @param outcomes one outcome for each twin, in the order the twins were handed over
 */
public record CreditReport(MasteryLedger ledger, List<CreditOutcome> outcomes) {

    public CreditReport {
        Objects.requireNonNull(ledger, "ledger");
        outcomes = List.copyOf(outcomes);
    }

    /** How many twins moved. */
    public int movedCount() {
        return (int) outcomes.stream().filter(CreditOutcome::moved).count();
    }

    /** How many twins climbed a rung. */
    public int climbedCount() {
        return (int) outcomes.stream().filter(CreditOutcome::climbed).count();
    }
}
