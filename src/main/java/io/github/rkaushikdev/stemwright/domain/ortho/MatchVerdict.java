package io.github.rkaushikdev.stemwright.domain.ortho;

import java.util.Arrays;
import java.util.Optional;

/**
 * The outcome of marking one answer.
 *
 * <p>There are four outcomes and they are not a scale. {@link #EXACT} and {@link #TOLERATED} are
 * both right, and the difference between them is what the learner is told, not what they score.
 * {@link #NEAR} is wrong but worth a hint. {@link #WRONG} is wrong.
 */
public enum MatchVerdict {
    EXACT("exact"),
    TOLERATED("tolerated"),
    NEAR("near"),
    WRONG("wrong");

    private final String key;

    MatchVerdict(String key) {
        this.key = key;
    }

    /** The word an attempt record and the http api carry, such as {@code tolerated}. */
    public String key() {
        return key;
    }

    public static Optional<MatchVerdict> fromKey(String key) {
        return Arrays.stream(values())
                .filter(verdict -> verdict.key.equals(key))
                .findFirst();
    }
}
