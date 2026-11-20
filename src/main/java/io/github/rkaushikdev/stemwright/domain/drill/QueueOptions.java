package io.github.rkaushikdev.stemwright.domain.drill;

import java.util.Objects;
import java.util.Optional;

/**
 * The parts of building a queue that come from the sitting rather than the policy.
 *
 * @param rowAcross the dimension the spacing rule treats as running across a row, the schema's last
 *     dimension when absent
 * @param buried the cells the open session has buried, {@link BurialSet#NONE} for a new sitting
 */
public record QueueOptions(Optional<String> rowAcross, BurialSet buried) {

    /** The schema's last dimension across a row, and nothing buried. */
    public static final QueueOptions DEFAULT = new QueueOptions(Optional.empty(), BurialSet.NONE);

    public QueueOptions {
        Objects.requireNonNull(rowAcross, "rowAcross");
        Objects.requireNonNull(buried, "buried");
    }

    /** A copy whose rows run across the dimension. */
    public QueueOptions withRowAcross(String dimension) {
        return new QueueOptions(Optional.of(dimension), buried);
    }

    /** A copy that leaves the buried cells out. */
    public QueueOptions withBuried(BurialSet set) {
        return new QueueOptions(rowAcross, set);
    }
}
