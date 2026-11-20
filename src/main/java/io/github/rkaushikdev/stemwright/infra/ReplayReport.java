package io.github.rkaushikdev.stemwright.infra;

/**
 * What a replay did with the entries it was handed.
 *
 * @param applied the entries written into the stores
 * @param skipped the entries of a kind the replay does not handle, passed over
 * @param lastSeq the sequence number of the last entry walked, or zero when there was none
 */
public record ReplayReport(int applied, int skipped, long lastSeq) {}
