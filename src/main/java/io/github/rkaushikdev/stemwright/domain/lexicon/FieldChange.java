package io.github.rkaushikdev.stemwright.domain.lexicon;

/**
 * One field of a lexeme that differs between two versions, each side written as text.
 *
 * @param field the field name, such as {@code class}, {@code glosses} or {@code stem.aorist}
 * @param before the field before, empty when it was absent
 * @param after the field after, empty when it is absent
 */
public record FieldChange(String field, String before, String after) {}
