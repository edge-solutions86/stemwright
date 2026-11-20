package io.github.rkaushikdev.stemwright.domain.lexicon;

import java.util.Arrays;
import java.util.Optional;

/**
 * The part of speech an inflection class declines or conjugates as. A class and its parent always
 * agree on it, because a verb class under a noun class would hand verbs a noun paradigm.
 */
public enum PartOfSpeech {
    NOUN("noun"),
    VERB("verb"),
    ADJECTIVE("adjective"),
    PRONOUN("pronoun"),
    NUMERAL("numeral"),
    PARTICIPLE("participle");

    private final String key;

    PartOfSpeech(String key) {
        this.key = key;
    }

    /** The lower case name a class declaration and a report use, such as {@code noun}. */
    public String key() {
        return key;
    }

    public static Optional<PartOfSpeech> fromKey(String key) {
        return Arrays.stream(values()).filter(part -> part.key.equals(key)).findFirst();
    }

    /** True for exactly the keys of the six parts, compared as written. */
    public static boolean isPartOfSpeech(String value) {
        return fromKey(value).isPresent();
    }
}
