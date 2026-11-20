package io.github.rkaushikdev.stemwright.domain.paradigm;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * A revision as a publisher writes it, before {@link RevisionHistory#of} numbers it.
 *
 * @param effectiveFrom the first study day the revision is to be in force
 * @param profile the profile to publish
 * @param syncretism the syncretism to declare with it, nothing declared when absent
 * @param reason why the revision is being published
 */
public record ProfileRevisionInput(
        LocalDate effectiveFrom, ParadigmProfile profile, Optional<SyncretismMap> syncretism, Optional<String> reason) {

    public ProfileRevisionInput {
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(syncretism, "syncretism");
        Objects.requireNonNull(reason, "reason");
    }

    /** A revision that declares no syncretism and gives no reason. */
    public static ProfileRevisionInput of(LocalDate effectiveFrom, ParadigmProfile profile) {
        return new ProfileRevisionInput(effectiveFrom, profile, Optional.empty(), Optional.empty());
    }

    /** A copy declaring the syncretism. */
    public ProfileRevisionInput withSyncretism(SyncretismMap syncretism) {
        return new ProfileRevisionInput(effectiveFrom, profile, Optional.of(syncretism), reason);
    }

    /** A copy carrying the reason. */
    public ProfileRevisionInput withReason(String reason) {
        return new ProfileRevisionInput(effectiveFrom, profile, syncretism, Optional.of(reason));
    }
}
