package io.github.rkaushikdev.stemwright.domain.paradigm;

import java.util.List;
import java.util.Optional;

/**
 * A paradigm profile as an author or a paradigm file writes it, before {@link ParadigmProfile#of}
 * checks it.
 *
 * @param key the profile key, which becomes a slug
 * @param classKey the key of the inflection class the profile was written for, which becomes a slug
 * @param label the printed name, defaulted to the key when absent
 * @param slots the slots in the order they were written
 */
public record ParadigmProfileInput(String key, String classKey, Optional<String> label, List<ParadigmSlotInput> slots) {

    public ParadigmProfileInput {
        slots = List.copyOf(slots);
    }

    /** A profile printed under its own key. */
    public static ParadigmProfileInput of(String key, String classKey, ParadigmSlotInput... slots) {
        return of(key, classKey, List.of(slots));
    }

    /** A profile printed under its own key. */
    public static ParadigmProfileInput of(String key, String classKey, List<ParadigmSlotInput> slots) {
        return new ParadigmProfileInput(key, classKey, Optional.empty(), slots);
    }

    /** A copy printed under the label. */
    public ParadigmProfileInput withLabel(String label) {
        return new ParadigmProfileInput(key, classKey, Optional.of(label), slots);
    }
}
