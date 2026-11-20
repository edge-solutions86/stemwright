package io.github.rkaushikdev.stemwright.domain.lexicon;

import java.util.Objects;
import java.util.Optional;

/**
 * An inflection class as a caller writes it, before {@link ClassRegistry#of} checks it.
 *
 * <p>The part of speech is kept as written rather than as a {@link PartOfSpeech}, because a class
 * declaration can arrive from a file, and the registry is where a word that is not one of the six
 * gets reported against the class that used it.
 *
 * @param key the class key, which becomes a slug
 * @param label the printed name, defaulted to the key when absent
 * @param partOfSpeech the part of speech as written, such as {@code noun}
 * @param parent the key of the class this one refines
 * @param note an editor's remark on the class
 */
public record InflectionClassInput(
        String key, Optional<String> label, String partOfSpeech, Optional<String> parent, Optional<String> note) {

    /** A root class with no label or note of its own. */
    public static InflectionClassInput of(String key, PartOfSpeech partOfSpeech) {
        return builder(key, partOfSpeech).build();
    }

    public static Builder builder(String key, PartOfSpeech partOfSpeech) {
        return builder(key, partOfSpeech.key());
    }

    /** A builder for a class whose part of speech is still text, as read from a declaration file. */
    public static Builder builder(String key, String partOfSpeech) {
        return new Builder(key, partOfSpeech);
    }

    /** Collects the optional parts of a class declaration. */
    public static final class Builder {

        private final String key;
        private final String partOfSpeech;
        private String label;
        private String parent;
        private String note;

        private Builder(String key, String partOfSpeech) {
            this.key = Objects.requireNonNull(key, "key");
            this.partOfSpeech = Objects.requireNonNull(partOfSpeech, "partOfSpeech");
        }

        public Builder label(String label) {
            this.label = Objects.requireNonNull(label, "label");
            return this;
        }

        public Builder parent(String parent) {
            this.parent = Objects.requireNonNull(parent, "parent");
            return this;
        }

        public Builder note(String note) {
            this.note = Objects.requireNonNull(note, "note");
            return this;
        }

        public InflectionClassInput build() {
            return new InflectionClassInput(
                    key,
                    Optional.ofNullable(label),
                    partOfSpeech,
                    Optional.ofNullable(parent),
                    Optional.ofNullable(note));
        }
    }
}
