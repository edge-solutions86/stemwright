package io.github.rkaushikdev.stemwright.domain.lexicon;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A lexeme as an editor, an import row or a request writes it, before {@link Lexeme#of} checks it.
 *
 * @param lemma the dictionary form
 * @param classKey the key of the inflection class it follows
 * @param stems the stems, already checked
 * @param inherent the inherent features as dimension to value; the order written decides only the
 *     order problems are reported in
 * @param glosses the glosses, the primary one first
 * @param homograph which of several lexemes sharing the lemma this is, from one
 * @param tags the tags as written
 * @param note an editor's remark on the lexeme
 */
public record LexemeInput(
        String lemma,
        String classKey,
        StemSet stems,
        Map<String, String> inherent,
        List<String> glosses,
        int homograph,
        List<String> tags,
        Optional<String> note) {

    public LexemeInput {
        inherent = Collections.unmodifiableMap(new LinkedHashMap<>(inherent));
        glosses = List.copyOf(glosses);
        tags = List.copyOf(tags);
    }

    /** A first homograph with no inherent features, glosses, tags or note. */
    public static LexemeInput of(String lemma, String classKey, StemSet stems) {
        return builder(lemma, classKey, stems).build();
    }

    /** A builder whose optional parts start empty and whose homograph starts at one. */
    public static Builder builder(String lemma, String classKey, StemSet stems) {
        return new Builder(lemma, classKey, stems);
    }

    /** Collects the optional parts of a lexeme. */
    public static final class Builder {

        private final String lemma;
        private final String classKey;
        private final StemSet stems;
        private Map<String, String> inherent = Map.of();
        private List<String> glosses = List.of();
        private int homograph = 1;
        private List<String> tags = List.of();
        private String note;

        private Builder(String lemma, String classKey, StemSet stems) {
            this.lemma = Objects.requireNonNull(lemma, "lemma");
            this.classKey = Objects.requireNonNull(classKey, "classKey");
            this.stems = Objects.requireNonNull(stems, "stems");
        }

        /** The inherent features, read in the map's own iteration order. */
        public Builder inherent(Map<String, String> inherent) {
            this.inherent = new LinkedHashMap<>(inherent);
            return this;
        }

        public Builder glosses(String... glosses) {
            return glosses(List.of(glosses));
        }

        public Builder glosses(List<String> glosses) {
            this.glosses = List.copyOf(glosses);
            return this;
        }

        public Builder homograph(int homograph) {
            this.homograph = homograph;
            return this;
        }

        public Builder tags(String... tags) {
            return tags(List.of(tags));
        }

        public Builder tags(List<String> tags) {
            this.tags = List.copyOf(tags);
            return this;
        }

        public Builder note(String note) {
            this.note = Objects.requireNonNull(note, "note");
            return this;
        }

        public LexemeInput build() {
            return new LexemeInput(
                    lemma, classKey, stems, inherent, glosses, homograph, tags, Optional.ofNullable(note));
        }
    }
}
