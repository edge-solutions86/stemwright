package io.github.rkaushikdev.stemwright.domain.lexicon;

import io.github.rkaushikdev.stemwright.domain.Identifiers;
import io.github.rkaushikdev.stemwright.domain.text.Normalise;
import java.util.Optional;

/**
 * A stem is the part of a word the endings attach to. Most lexemes have one. Plenty have several,
 * keyed by the part of the paradigm they serve: a Greek verb has a present stem, a future stem, an
 * aorist stem and a perfect stem, and they are not derivable from one another.
 *
 * <p>Suppletion is the case where a stem is not a variant of the others at all. {@code fero} and
 * {@code tuli} share nothing, and neither do {@code go} and {@code went}. The model marks it rather
 * than hiding it, because a drill that asks a learner to build a suppletive form from the citation
 * stem is asking for something impossible.
 *
 * @param role the slug naming the part of the paradigm the stem serves, such as {@code aorist}
 * @param form the stem itself, normalised
 * @param suppletive whether the stem is not a variant of the others at all
 * @param note an editor's remark on the stem
 */
public record Stem(String role, String form, boolean suppletive, Optional<String> note) {

    /** The role every lexeme is expected to have, and the one a missing role falls back to. */
    public static final String DEFAULT_ROLE = "base";

    private static final int FORM_LIMIT = 128;
    private static final int NOTE_LIMIT = 240;

    /** Checks a stem as written: the role must be a slug and the form must be usable text. */
    public static Stem of(StemInput input) {
        String role = Identifiers.asSlug("stem.role", input.role());
        String form = Normalise.requireText("stem." + role + ".form", input.form(), FORM_LIMIT);
        return new Stem(
                role,
                form,
                input.suppletive(),
                input.note().map(note -> Normalise.requireText("stem." + role + ".note", note, NOTE_LIMIT)));
    }
}
