package io.github.rkaushikdev.stemwright.domain.ortho;

import java.util.Optional;

/**
 * The tolerance levels named for one answer, at each of the places a level can be fixed. The most
 * specific one present wins, as {@link OrthoProfile#resolveTolerance(ToleranceRequest)} decides.
 *
 * @param deckLevel the level the deck runs at, or nothing to take the language default
 * @param cellLevel a level fixed on one cell, which beats the deck
 * @param assignmentLevel a level fixed on one assignment, which beats both
 */
public record ToleranceRequest(
        Optional<String> deckLevel, Optional<String> cellLevel, Optional<String> assignmentLevel) {

    /** A request that names no level, so the language default applies. */
    public static final ToleranceRequest NONE =
            new ToleranceRequest(Optional.empty(), Optional.empty(), Optional.empty());

    public static Builder builder() {
        return new Builder();
    }

    /** Names the levels one at a time; any left unnamed stay empty. */
    public static final class Builder {

        private Optional<String> deckLevel = Optional.empty();
        private Optional<String> cellLevel = Optional.empty();
        private Optional<String> assignmentLevel = Optional.empty();

        private Builder() {}

        public Builder deckLevel(String key) {
            this.deckLevel = Optional.of(key);
            return this;
        }

        public Builder cellLevel(String key) {
            this.cellLevel = Optional.of(key);
            return this;
        }

        public Builder assignmentLevel(String key) {
            this.assignmentLevel = Optional.of(key);
            return this;
        }

        public ToleranceRequest build() {
            return new ToleranceRequest(deckLevel, cellLevel, assignmentLevel);
        }
    }
}
