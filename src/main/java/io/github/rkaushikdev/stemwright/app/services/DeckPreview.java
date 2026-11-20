package io.github.rkaushikdev.stemwright.app.services;

import io.github.rkaushikdev.stemwright.domain.drill.Deck;
import io.github.rkaushikdev.stemwright.domain.drill.Membership;
import java.util.List;
import java.util.Objects;

/**
 * What a deck would select, worked out without saving it.
 *
 * @param deck the deck as checked
 * @param membership the cells it takes on the day previewed
 * @param warnings what the membership found worth telling an editor, an empty selection included
 */
public record DeckPreview(Deck deck, Membership membership, List<String> warnings) {

    public DeckPreview {
        Objects.requireNonNull(deck, "deck");
        Objects.requireNonNull(membership, "membership");
        warnings = List.copyOf(warnings);
    }
}
