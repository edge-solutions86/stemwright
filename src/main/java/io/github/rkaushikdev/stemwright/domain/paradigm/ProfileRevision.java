package io.github.rkaushikdev.stemwright.domain.paradigm;

import io.github.rkaushikdev.stemwright.domain.IdKind;
import io.github.rkaushikdev.stemwright.domain.Identifiers;
import java.time.LocalDate;
import java.util.Optional;

/**
 * One published state of a paradigm profile: the profile and its syncretism map, and the study day
 * from which they are what an answer is graded against. {@link RevisionHistory} numbers and orders
 * them.
 *
 * @param id the derived revision id, see {@link #idFor}
 * @param profileKey the key of the profile the revision belongs to
 * @param ordinal the position in the history, from one in date order
 * @param effectiveFrom the first study day the revision is in force
 * @param profile the profile as published
 * @param syncretism the syncretism declared with it
 * @param reason why the revision was published
 */
public record ProfileRevision(
        String id,
        String profileKey,
        int ordinal,
        LocalDate effectiveFrom,
        ParadigmProfile profile,
        SyncretismMap syncretism,
        Optional<String> reason) {

    /**
     * The id is derived from the profile key and the ordinal, so a history rebuilt from the same
     * revisions gives every revision the same id it had before.
     */
    public static String idFor(String profileKey, int ordinal) {
        return Identifiers.derivedId(IdKind.REVISION, profileKey, Integer.toString(ordinal));
    }
}
