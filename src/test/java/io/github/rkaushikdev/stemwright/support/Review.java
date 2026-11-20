package io.github.rkaushikdev.stemwright.support;

import io.github.rkaushikdev.stemwright.domain.drill.ReviewSettings;
import io.github.rkaushikdev.stemwright.domain.mastery.Ladder;
import io.github.rkaushikdev.stemwright.domain.mastery.LadderInput;
import io.github.rkaushikdev.stemwright.domain.mastery.LeechPolicy;
import io.github.rkaushikdev.stemwright.domain.paradigm.RuleSet;

/**
 * The review settings the drill tests share: Latin tolerance, no join rules, a ladder with no
 * spread, and a leech after three lapses.
 *
 * <p>This shares its simple name with {@code domain.drill.Review}, so a test in that package reads
 * these through static imports.
 */
public final class Review {

    private Review() {}

    /** One, three, seven and sixteen days, with no spread, so every due date can be worked out. */
    public static final Ladder FLAT_LADDER =
            Ladder.of(LadderInput.builder("flat", 1, 3, 7, 16).spread(0).build());

    /** {@link Ortho#LATIN}, {@link RuleSet#NONE}, {@link #FLAT_LADDER} and a threshold of three. */
    public static final ReviewSettings REVIEW_SETTINGS = ReviewSettings.of(
            Ortho.LATIN,
            RuleSet.NONE,
            FLAT_LADDER,
            LeechPolicy.builder().threshold(3).build());
}
