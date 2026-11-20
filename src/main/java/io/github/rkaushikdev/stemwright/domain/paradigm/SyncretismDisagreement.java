package io.github.rkaushikdev.stemwright.domain.paradigm;

/**
 * A pair of bundles the declared syncretism and the profile's endings disagree about. A declared
 * pair the profile does not produce means the endings are wrong or the claim is. A produced pair
 * nobody declared means the drill will accept an answer the syllabus wanted kept apart.
 *
 * @param first the canonical key of the bundle that comes first in slot order
 * @param second the canonical key of the bundle that comes later in slot order
 * @param declared whether the declaration calls the two one form
 * @param produced whether the profile builds the same string for both
 */
public record SyncretismDisagreement(String first, String second, boolean declared, boolean produced) {}
