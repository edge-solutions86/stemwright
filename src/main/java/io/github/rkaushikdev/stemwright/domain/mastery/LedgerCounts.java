package io.github.rkaushikdev.stemwright.domain.mastery;

/**
 * How a list of cells splits across one learner's ledger on one day. Every cell lands in exactly one
 * of suspended, untouched and introduced; due and mature are subsets of introduced.
 *
 * @param known how many cells were asked about
 * @param introduced cells answered at least once and not suspended
 * @param due introduced cells that have come round
 * @param mature introduced cells at the top of the ladder
 * @param suspended cells taken out of the queue
 * @param untouched cells nobody has answered
 */
public record LedgerCounts(int known, int introduced, int due, int mature, int suspended, int untouched) {}
