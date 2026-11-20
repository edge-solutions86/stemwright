package io.github.rkaushikdev.stemwright.domain.paradigm;

/**
 * One line of a join trace: the rule that fired, and the form before and after it.
 *
 * @param rule the key of the rule that fired
 * @param before the plain concatenation of stem and affix
 * @param after the form once the rule rewrote the seam
 */
public record JoinStep(String rule, String before, String after) {}
