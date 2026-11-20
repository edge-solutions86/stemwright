package io.github.rkaushikdev.stemwright.domain.course;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What counts as done on an {@link Assignment}. It is the only place in the model where a target
 * exists at all: the drill layer has no notion of finishing.
 *
 * <p>Two targets are supported because departments ask for both. A mastery target says how many
 * cells must reach the top of the ladder, and an effort target says how many days the learner must
 * have practised. Neither implies the other, and an assignment may set both.
 *
 * @param masteredCells how many cells must reach the top of the ladder, when the assignment asks
 *     for mastery
 * @param practiceDays how many days the learner must have practised, when the assignment asks for
 *     effort
 */
public record AssignmentTarget(Optional<Integer> masteredCells, Optional<Integer> practiceDays) {

    public AssignmentTarget {
        Objects.requireNonNull(masteredCells, "masteredCells");
        Objects.requireNonNull(practiceDays, "practiceDays");
    }

    /**
     * Reads the target out, such as {@code 8 cells to the top of the ladder and 5 days of practice},
     * naming only the targets that are set.
     */
    public String describe() {
        List<String> parts = new ArrayList<>(2);
        masteredCells.ifPresent(cells -> parts.add(cells + " cells to the top of the ladder"));
        practiceDays.ifPresent(days -> parts.add(days + " days of practice"));
        return String.join(" and ", parts);
    }
}
