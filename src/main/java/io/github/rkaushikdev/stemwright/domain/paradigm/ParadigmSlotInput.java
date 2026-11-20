package io.github.rkaushikdev.stemwright.domain.paradigm;

import java.util.Objects;
import java.util.Optional;

/**
 * A slot as a profile author writes it, before {@link ParadigmProfile#of} checks it.
 *
 * @param bundle either the dotted notation, such as {@code gen.sg}, or a canonical bundle key
 * @param stemRole the role of the stem to attach to, the base stem when absent
 * @param affix the ending; a slot needs this or a literal
 * @param literal a full form that replaces stem plus affix; a slot needs this or an affix
 * @param note an editor's remark on the slot
 */
public record ParadigmSlotInput(
        String bundle,
        Optional<String> stemRole,
        Optional<String> affix,
        Optional<String> literal,
        Optional<String> note) {

    /** The common shape: an ending on the base stem. */
    public static ParadigmSlotInput of(String bundle, String affix) {
        return builder(bundle).affix(affix).build();
    }

    /** A cell the profile states outright rather than builds. */
    public static ParadigmSlotInput literal(String bundle, String literal) {
        return builder(bundle).literal(literal).build();
    }

    /** A builder whose optional parts start empty. */
    public static Builder builder(String bundle) {
        return new Builder(bundle);
    }

    /** Collects the optional parts of a slot. */
    public static final class Builder {

        private final String bundle;
        private String stemRole;
        private String affix;
        private String literal;
        private String note;

        private Builder(String bundle) {
            this.bundle = Objects.requireNonNull(bundle, "bundle");
        }

        public Builder stemRole(String stemRole) {
            this.stemRole = Objects.requireNonNull(stemRole, "stemRole");
            return this;
        }

        public Builder affix(String affix) {
            this.affix = Objects.requireNonNull(affix, "affix");
            return this;
        }

        public Builder literal(String literal) {
            this.literal = Objects.requireNonNull(literal, "literal");
            return this;
        }

        public Builder note(String note) {
            this.note = Objects.requireNonNull(note, "note");
            return this;
        }

        public ParadigmSlotInput build() {
            return new ParadigmSlotInput(
                    bundle,
                    Optional.ofNullable(stemRole),
                    Optional.ofNullable(affix),
                    Optional.ofNullable(literal),
                    Optional.ofNullable(note));
        }
    }
}
