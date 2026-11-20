package io.github.rkaushikdev.stemwright.domain.drill;

import io.github.rkaushikdev.stemwright.domain.features.Selector;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Which lexemes a deck takes, and which of their cells. The four parts combine as an intersection:
 * a deck naming both a class and a tag wants the lexemes that are in that class and carry that tag,
 * which is nearly always what a teacher means and never what a union would give them. A part left
 * empty constrains nothing.
 *
 * @param classKeys the classes a lexeme must be in or under, any one of them
 * @param tags the tags a lexeme must carry, every one of them
 * @param lexemeIds the lexemes the deck is limited to, if any are named
 * @param cells the cells of each lexeme the deck takes
 */
public record DeckScope(List<String> classKeys, List<String> tags, List<String> lexemeIds, Selector cells) {

    /** The scope that constrains nothing, so a deck with it takes the whole language. */
    public static final DeckScope EMPTY = new DeckScope(List.of(), List.of(), List.of(), Selector.EMPTY);

    public DeckScope {
        classKeys = List.copyOf(classKeys);
        tags = List.copyOf(tags);
        lexemeIds = List.copyOf(lexemeIds);
        Objects.requireNonNull(cells, "cells");
    }

    /** True when no part of the scope constrains anything. */
    public boolean isEmpty() {
        return classKeys.isEmpty()
                && tags.isEmpty()
                && lexemeIds.isEmpty()
                && cells.clauses().isEmpty();
    }

    /**
     * The scope read out for an editor, such as {@code classes first-declension or
     * second-declension, tagged core, cells where number=sg}. Named lexemes are counted rather than
     * listed.
     */
    public String describe() {
        List<String> parts = new ArrayList<>();
        if (!classKeys.isEmpty()) {
            parts.add("classes " + String.join(" or ", classKeys));
        }
        if (!tags.isEmpty()) {
            parts.add("tagged " + String.join(" and ", tags));
        }
        if (!lexemeIds.isEmpty()) {
            parts.add(lexemeIds.size() + " named lexemes");
        }
        if (!cells.clauses().isEmpty()) {
            parts.add("cells where " + cells.format());
        }
        return parts.isEmpty() ? "everything in the language" : String.join(", ", parts);
    }
}
