package io.github.rkaushikdev.stemwright.domain.report;

import java.util.Objects;

/**
 * One row of a coverage report: the cells of one bundle or of one lexeme, split by where the learner
 * stands on each. Every cell falls in exactly one of untouched, learning, mature and suspended; due
 * counts across the first three.
 *
 * @param key the bundle key or the lexeme id the row is for, {@code all} for a total
 * @param total how many cells the row holds
 * @param untouched how many have never been asked
 * @param learning how many have been asked and are below the top of the ladder
 * @param mature how many sit at the top of the ladder
 * @param due how many are due on the day, suspended cells never among them
 * @param suspended how many are out of the queue
 * @param accuracy the mean accuracy of the cells that have been answered, nought when none has
 */
public record CoverageRow(
        String key, int total, int untouched, int learning, int mature, int due, int suspended, double accuracy) {

    public CoverageRow {
        Objects.requireNonNull(key, "key");
    }

    /** The share of the row that is mature, which is what a progress bar shows. */
    public double maturity() {
        return total == 0 ? 0 : (double) mature / total;
    }

    /** The share of the row that has been met at all, mature, learning or suspended. */
    public double touchedShare() {
        return total == 0 ? 0 : (double) (total - untouched) / total;
    }
}
