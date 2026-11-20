package io.github.rkaushikdev.stemwright.domain.lexicon;

import java.util.Optional;

/**
 * A stem as a caller writes it, before {@link Stem#of} checks it.
 *
 * @param role the part of the paradigm the stem serves, which becomes a slug
 * @param form the stem itself
 * @param suppletive whether the stem is not a variant of the others at all
 * @param note an editor's remark on the stem
 */
public record StemInput(String role, String form, boolean suppletive, Optional<String> note) {

    /** An ordinary stem with no note. */
    public static StemInput of(String role, String form) {
        return new StemInput(role, form, false, Optional.empty());
    }

    /** A stem marked suppletive, with no note. */
    public static StemInput suppletive(String role, String form) {
        return new StemInput(role, form, true, Optional.empty());
    }

    /** A copy carrying the note. */
    public StemInput withNote(String note) {
        return new StemInput(role, form, suppletive, Optional.of(note));
    }
}
