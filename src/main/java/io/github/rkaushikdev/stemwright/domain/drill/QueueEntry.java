package io.github.rkaushikdev.stemwright.domain.drill;

import io.github.rkaushikdev.stemwright.domain.paradigm.Cell;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * One question in the day's queue.
 *
 * @param cell the cell to ask
 * @param kind whether the cell is due for review or being introduced
 * @param overdueDays how many days past its due date a review is, nought for a new cell
 */
public record QueueEntry(Cell cell, Kind kind, int overdueDays) {

    public QueueEntry {
        Objects.requireNonNull(cell, "cell");
        Objects.requireNonNull(kind, "kind");
    }

    /** Why a cell is asked: it has come due, or it has never been asked. */
    public enum Kind {
        REVIEW("review"),
        NEW("new");

        private final String key;

        Kind(String key) {
            this.key = key;
        }

        /** The kind as it is printed, such as {@code review}. */
        public String key() {
            return key;
        }

        public static Optional<Kind> fromKey(String key) {
            return Arrays.stream(values()).filter(kind -> kind.key.equals(key)).findFirst();
        }
    }
}
