package io.github.rkaushikdev.stemwright.domain.ortho;

/**
 * One line of a transliteration table.
 *
 * @param from what the learner types, such as {@code th}
 * @param to what it stands for, such as {@code θ}
 */
public record TransliterationRule(String from, String to) {}
