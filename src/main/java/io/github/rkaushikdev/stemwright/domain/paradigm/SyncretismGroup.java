package io.github.rkaushikdev.stemwright.domain.paradigm;

import java.util.List;
import java.util.Optional;

/**
 * Bundles a language treats as one form, such as the Latin first declension genitive singular,
 * dative singular and nominative plural.
 *
 * @param key the group key
 * @param members the canonical bundle keys of the members, in the order they were declared
 * @param note an editor's remark on the group
 */
public record SyncretismGroup(String key, List<String> members, Optional<String> note) {

    public SyncretismGroup {
        members = List.copyOf(members);
    }
}
