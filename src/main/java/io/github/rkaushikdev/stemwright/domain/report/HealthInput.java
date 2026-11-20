package io.github.rkaushikdev.stemwright.domain.report;

import io.github.rkaushikdev.stemwright.domain.drill.Deck;
import io.github.rkaushikdev.stemwright.domain.drill.DrillContext;
import io.github.rkaushikdev.stemwright.domain.drill.Membership;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * What the health check reads: one language's drill context, its decks as already resolved, and
 * the day. The memberships are taken resolved rather than resolved here, so a caller cannot check
 * one day and report another.
 *
 * @param context the language's schema, classes, profile book and lexicon
 * @param decks every deck of the language with its membership on the day
 * @param on the day the check is for
 */
public record HealthInput(DrillContext context, List<ResolvedDeck> decks, LocalDate on) {

    public HealthInput {
        Objects.requireNonNull(context, "context");
        decks = List.copyOf(decks);
        Objects.requireNonNull(on, "on");
    }

    /**
     * A deck and what it resolved to on the day of the check.
     *
     * @param deck the deck as written
     * @param membership the cells and lexemes it took on the day
     */
    public record ResolvedDeck(Deck deck, Membership membership) {

        public ResolvedDeck {
            Objects.requireNonNull(deck, "deck");
            Objects.requireNonNull(membership, "membership");
        }
    }
}
