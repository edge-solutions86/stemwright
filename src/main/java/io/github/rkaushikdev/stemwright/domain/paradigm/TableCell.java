package io.github.rkaushikdev.stemwright.domain.paradigm;

import java.util.Optional;

/**
 * One cell of a printed paradigm table. A cell with no form is either blocked, because the lexeme's
 * inherent features rule the bundle out, or a hole the profile says nothing about.
 *
 * @param bundle the canonical bundle key, empty when no bundle of the schema fits the cell
 * @param form the realised form, absent for a blocked cell or a hole
 * @param blocked whether the cell has no answer for this lexeme
 */
public record TableCell(String bundle, Optional<String> form, boolean blocked) {}
