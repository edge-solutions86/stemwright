package io.github.rkaushikdev.stemwright.domain.paradigm;

import io.github.rkaushikdev.stemwright.domain.errors.NotFoundException;
import io.github.rkaushikdev.stemwright.domain.errors.ProblemCollector;
import io.github.rkaushikdev.stemwright.domain.time.DayRange;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Profiles are never edited in place. A change publishes a new revision with a date it takes effect
 * from, and everything that grades an answer resolves the revision in force at the moment of the
 * answer rather than the newest one.
 *
 * <p>That is the whole reason this type exists. A student who typed {@code puellae} in March was
 * right under the revision that ran in March. Re grading them in October under October's
 * syncretism map would change history, and a term report that did so would disagree with the mark
 * the student was shown.
 *
 * @param profileKey the key of the profile every revision belongs to
 * @param revisions the revisions in date order, numbered from one
 */
public record RevisionHistory(String profileKey, List<ProfileRevision> revisions) {

    public RevisionHistory {
        revisions = List.copyOf(revisions);
    }

    /**
     * Revisions are given in any order and come back sorted. Two revisions may not share a start
     * date: which one was in force would then be undecidable, and a silent pick is exactly the kind
     * of thing that is invisible until a report disagrees with a transcript. A revision carrying
     * another profile is refused too, and every problem is reported at once.
     */
    public static RevisionHistory of(String profileKey, List<ProfileRevisionInput> inputs) {
        ProblemCollector problems = new ProblemCollector();
        if (inputs.isEmpty()) {
            problems.add("revisions", "must hold at least one revision");
        }

        List<ProfileRevisionInput> sorted = new ArrayList<>(inputs);
        sorted.sort(Comparator.comparing(ProfileRevisionInput::effectiveFrom));
        List<ProfileRevision> revisions = new ArrayList<>();

        for (int index = 0; index < sorted.size(); index += 1) {
            ProfileRevisionInput input = sorted.get(index);
            if (index > 0 && sorted.get(index - 1).effectiveFrom().equals(input.effectiveFrom())) {
                problems.add(
                        "revisions." + index, "starts on " + input.effectiveFrom() + " and so does the one before it");
                continue;
            }
            if (!input.profile().key().equals(profileKey)) {
                problems.add(
                        "revisions." + index,
                        "carries profile " + input.profile().key() + " in the history of " + profileKey);
                continue;
            }
            int ordinal = revisions.size() + 1;
            revisions.add(new ProfileRevision(
                    ProfileRevision.idFor(profileKey, ordinal),
                    profileKey,
                    ordinal,
                    input.effectiveFrom(),
                    input.profile(),
                    input.syncretism().orElse(SyncretismMap.NONE),
                    input.reason()));
        }

        problems.throwIfAny("revision history for " + profileKey + " is not usable");
        return new RevisionHistory(profileKey, revisions);
    }

    /**
     * The newest revision, whether or not it is in force yet.
     *
     * @throws NotFoundException when the history holds no revision at all
     */
    public ProfileRevision latestRevision() {
        if (revisions.isEmpty()) {
            throw new NotFoundException("revision", profileKey);
        }
        return revisions.get(revisions.size() - 1);
    }

    /**
     * The oldest revision.
     *
     * @throws NotFoundException when the history holds no revision at all
     */
    public ProfileRevision firstRevision() {
        if (revisions.isEmpty()) {
            throw new NotFoundException("revision", profileKey);
        }
        return revisions.get(0);
    }

    /**
     * The revision in force on a day, or nothing if the profile had not been published yet. A caller
     * that treats "nothing" as "use the newest" has reintroduced the bug this type exists to
     * prevent, so the absence is returned rather than papered over.
     */
    public Optional<ProfileRevision> revisionInForce(LocalDate on) {
        ProfileRevision found = null;
        for (ProfileRevision revision : revisions) {
            if (revision.effectiveFrom().isAfter(on)) {
                break;
            }
            found = revision;
        }
        return Optional.ofNullable(found);
    }

    /**
     * The revision in force on a day.
     *
     * @throws NotFoundException naming the profile and the day when none was in force yet
     */
    public ProfileRevision requireRevisionInForce(LocalDate on) {
        return revisionInForce(on).orElseThrow(() -> new NotFoundException("revision", profileKey + " on " + on));
    }

    public Optional<ProfileRevision> revisionByOrdinal(int ordinal) {
        return revisions.stream()
                .filter(revision -> revision.ordinal() == ordinal)
                .findFirst();
    }

    public Optional<ProfileRevision> revisionById(String id) {
        return revisions.stream().filter(revision -> revision.id().equals(id)).findFirst();
    }

    /** Every revision that was in force on at least one day of the range, in ordinal order. */
    public List<ProfileRevision> revisionsAcross(DayRange range) {
        Map<String, ProfileRevision> seen = new LinkedHashMap<>();
        range.eachDay()
                .forEach(day -> revisionInForce(day).ifPresent(revision -> seen.putIfAbsent(revision.id(), revision)));
        return seen.values().stream()
                .sorted(Comparator.comparingInt(ProfileRevision::ordinal))
                .toList();
    }

    /** True when more than one revision was in force across the range. */
    public boolean spansARevisionChange(DayRange range) {
        return revisionsAcross(range).size() > 1;
    }

    /** The day a revision stopped being in force, or nothing if it still is. */
    public Optional<LocalDate> retiredOn(int ordinal) {
        return revisionByOrdinal(ordinal + 1).map(ProfileRevision::effectiveFrom);
    }

    /**
     * A copy with the revision added wherever its date puts it, every revision renumbered and the
     * whole history checked again.
     */
    public RevisionHistory withRevision(ProfileRevisionInput input) {
        List<ProfileRevisionInput> next = new ArrayList<>();
        for (ProfileRevision revision : revisions) {
            next.add(new ProfileRevisionInput(
                    revision.effectiveFrom(),
                    revision.profile(),
                    Optional.of(revision.syncretism()),
                    revision.reason()));
        }
        next.add(input);
        return of(profileKey, next);
    }
}
