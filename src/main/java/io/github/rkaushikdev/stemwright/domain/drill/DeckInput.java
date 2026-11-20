package io.github.rkaushikdev.stemwright.domain.drill;

import java.util.Objects;
import java.util.Optional;

/**
 * A deck as an editor or a request writes it, before {@link Deck#of} checks it.
 *
 * @param key the deck key, which becomes a slug
 * @param label the printed name, defaulted to the key when absent
 * @param scope the scope, {@link DeckScopeInput#EMPTY} for a deck that takes the whole language
 * @param toleranceLevel the tolerance level the deck runs at, the language default when absent
 * @param note an editor's remark on the deck
 */
public record DeckInput(
        String key,
        Optional<String> label,
        DeckScopeInput scope,
        Optional<String> toleranceLevel,
        Optional<String> note) {

    public DeckInput {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(toleranceLevel, "toleranceLevel");
        Objects.requireNonNull(note, "note");
    }

    /** A deck that takes the whole language under its key, with nothing else set. */
    public static DeckInput of(String key) {
        return builder(key).build();
    }

    /** A builder whose label, tolerance level and note start absent and whose scope starts empty. */
    public static Builder builder(String key) {
        return new Builder(key);
    }

    /** Collects the optional parts of a deck. */
    public static final class Builder {

        private final String key;
        private String label;
        private DeckScopeInput scope = DeckScopeInput.EMPTY;
        private String toleranceLevel;
        private String note;

        private Builder(String key) {
            this.key = Objects.requireNonNull(key, "key");
        }

        public Builder label(String label) {
            this.label = Objects.requireNonNull(label, "label");
            return this;
        }

        public Builder scope(DeckScopeInput scope) {
            this.scope = Objects.requireNonNull(scope, "scope");
            return this;
        }

        public Builder toleranceLevel(String toleranceLevel) {
            this.toleranceLevel = Objects.requireNonNull(toleranceLevel, "toleranceLevel");
            return this;
        }

        public Builder note(String note) {
            this.note = Objects.requireNonNull(note, "note");
            return this;
        }

        public DeckInput build() {
            return new DeckInput(
                    key,
                    Optional.ofNullable(label),
                    scope,
                    Optional.ofNullable(toleranceLevel),
                    Optional.ofNullable(note));
        }
    }
}
