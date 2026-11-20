package io.github.rkaushikdev.stemwright.domain.drill;

import java.util.Arrays;
import java.util.Optional;

/**
 * Why a cell of the membership is not in the day's queue. Every cell left out gets exactly one,
 * because "the queue is short today" is a question every learner asks.
 */
public enum ExclusionReason {
    /** The policy rests on this weekday, so nothing is asked. */
    REST_DAY("rest-day"),
    /** A twin of an answer given earlier in the sitting buried it. */
    BURIED("buried"),
    /** The cell is suspended, usually as a leech. */
    SUSPENDED("suspended"),
    /** The queue already holds as many cells of the lexeme as the policy allows. */
    LEXEME_CAP("lexeme-cap"),
    /** A cell of the same row was asked too recently. */
    ROW_SPACING("row-spacing"),
    /** The day's review budget ran out before the cell was reached. */
    REVIEW_BUDGET("review-budget"),
    /** The day's budget for new cells ran out before the cell was reached. */
    NEW_BUDGET("new-budget"),
    /** The cell has been seen and is not due yet. */
    NOT_DUE("not-due");

    private final String key;

    ExclusionReason(String key) {
        this.key = key;
    }

    /** The reason as it is printed, such as {@code review-budget}. */
    public String key() {
        return key;
    }

    public static Optional<ExclusionReason> fromKey(String key) {
        return Arrays.stream(values()).filter(reason -> reason.key.equals(key)).findFirst();
    }
}
