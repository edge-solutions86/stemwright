package io.github.rkaushikdev.stemwright.domain.ortho;

import java.util.Optional;

/**
 * The first cluster where two forms part company, counted from the left.
 *
 * @param index the position of the cluster, from zero
 * @param expected the expected form's cluster there, empty when the expected form has already ended
 * @param given the typed form's cluster there, empty when the typed form has already ended
 */
public record Divergence(int index, Optional<String> expected, Optional<String> given) {}
