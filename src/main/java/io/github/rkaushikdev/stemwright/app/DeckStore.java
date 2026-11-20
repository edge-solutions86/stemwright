package io.github.rkaushikdev.stemwright.app;

import io.github.rkaushikdev.stemwright.domain.drill.Deck;
import java.util.List;
import java.util.Optional;

/** The decks of every language. A deck key is unique within its language, not across languages. */
public interface DeckStore {

    Optional<Deck> get(String language, String key);

    /** Every deck of the language, by key as a reader sorts it. */
    List<Deck> listByLanguage(String language);

    /** Stores the deck under its language and key, in place of any deck stored there before. */
    void put(Deck entry);

    /** Removes the deck with the language and key, doing nothing when there is none. */
    void remove(String language, String key);
}
