package io.github.rkaushikdev.stemwright.domain.report;

/**
 * How many findings of each level a health check made, nought for a level nothing reached.
 *
 * @param errors how many errors
 * @param warnings how many warnings
 * @param notes how many notes
 */
public record FindingCounts(int errors, int warnings, int notes) {}
