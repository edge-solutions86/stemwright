package io.github.rkaushikdev.stemwright.domain.paradigm;

import java.util.List;

/**
 * A stem joined to an affix, with the trace of what the join rules did on the way.
 *
 * @param form the joined form
 * @param steps the rules that fired, at most one since rules do not cascade
 */
public record JoinResult(String form, List<JoinStep> steps) {

    public JoinResult {
        steps = List.copyOf(steps);
    }
}
