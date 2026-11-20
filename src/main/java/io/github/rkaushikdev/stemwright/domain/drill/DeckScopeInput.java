package io.github.rkaushikdev.stemwright.domain.drill;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A deck scope as an editor writes it, before {@link Deck#of} checks it. A part left absent is
 * different from a part given empty only when the input rescopes an existing deck: {@link
 * Deck#withScope} keeps the deck's own value for an absent part and clears it for an empty one.
 *
 * @param classKeys the classes as written
 * @param tags the tags as written
 * @param lexemeIds the lexeme ids as written
 * @param cells the cell selector in its written form
 */
public record DeckScopeInput(
        Optional<List<String>> classKeys,
        Optional<List<String>> tags,
        Optional<List<String>> lexemeIds,
        Optional<String> cells) {

    /** A scope input that names no part at all. */
    public static final DeckScopeInput EMPTY = builder().build();

    public DeckScopeInput {
        classKeys = classKeys.map(List::copyOf);
        tags = tags.map(List::copyOf);
        lexemeIds = lexemeIds.map(List::copyOf);
        Objects.requireNonNull(cells, "cells");
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Names the parts one at a time; any left unnamed stay absent. */
    public static final class Builder {

        private Optional<List<String>> classKeys = Optional.empty();
        private Optional<List<String>> tags = Optional.empty();
        private Optional<List<String>> lexemeIds = Optional.empty();
        private Optional<String> cells = Optional.empty();

        private Builder() {}

        public Builder classKeys(String... values) {
            return classKeys(List.of(values));
        }

        public Builder classKeys(List<String> values) {
            classKeys = Optional.of(values);
            return this;
        }

        public Builder tags(String... values) {
            return tags(List.of(values));
        }

        public Builder tags(List<String> values) {
            tags = Optional.of(values);
            return this;
        }

        public Builder lexemeIds(String... values) {
            return lexemeIds(List.of(values));
        }

        public Builder lexemeIds(List<String> values) {
            lexemeIds = Optional.of(values);
            return this;
        }

        /** The cell selector in its written form, such as {@code number=sg}. */
        public Builder cells(String value) {
            cells = Optional.of(value);
            return this;
        }

        public DeckScopeInput build() {
            return new DeckScopeInput(classKeys, tags, lexemeIds, cells);
        }
    }
}
