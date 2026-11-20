package io.github.rkaushikdev.stemwright.domain.report;

import io.github.rkaushikdev.stemwright.domain.mastery.AttemptTally;
import java.util.Objects;

/**
 * Every attempt from one deck, the row a term report prints.
 *
 * @param deckKey the deck
 * @param tally how its attempts were marked
 * @param accuracy the share of them that were correct
 * @param days how many different days the deck was drilled on
 */
public record DeckRollup(String deckKey, AttemptTally tally, double accuracy, int days) {

    public DeckRollup {
        Objects.requireNonNull(deckKey, "deckKey");
        Objects.requireNonNull(tally, "tally");
    }
}
