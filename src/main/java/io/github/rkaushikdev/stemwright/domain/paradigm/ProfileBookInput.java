package io.github.rkaushikdev.stemwright.domain.paradigm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A profile book as a loader assembles it, before {@link ProfileBook#of} checks it.
 *
 * @param language the language key, which becomes a slug
 * @param histories the revision histories, at most one per profile key
 * @param bindings the profile key each inflection class is bound to, both as written; the order
 *     written decides the order problems are reported in
 */
public record ProfileBookInput(String language, List<RevisionHistory> histories, Map<String, String> bindings) {

    public ProfileBookInput {
        histories = List.copyOf(histories);
        bindings = Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
    }
}
