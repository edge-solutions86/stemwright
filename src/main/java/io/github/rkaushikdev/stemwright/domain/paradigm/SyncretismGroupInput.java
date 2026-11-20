package io.github.rkaushikdev.stemwright.domain.paradigm;

import java.util.List;
import java.util.Optional;

/**
 * A syncretism group as an author writes it, before {@link SyncretismMap#of} checks it.
 *
 * @param key the group key, which becomes a slug
 * @param members the members, each in the dotted notation or as a canonical bundle key
 * @param note an editor's remark on the group
 */
public record SyncretismGroupInput(String key, List<String> members, Optional<String> note) {

    public SyncretismGroupInput {
        members = List.copyOf(members);
    }

    /** A group with no note. */
    public static SyncretismGroupInput of(String key, String... members) {
        return of(key, List.of(members));
    }

    /** A group with no note. */
    public static SyncretismGroupInput of(String key, List<String> members) {
        return new SyncretismGroupInput(key, members, Optional.empty());
    }

    /** A copy carrying the note. */
    public SyncretismGroupInput withNote(String note) {
        return new SyncretismGroupInput(key, members, Optional.of(note));
    }
}
