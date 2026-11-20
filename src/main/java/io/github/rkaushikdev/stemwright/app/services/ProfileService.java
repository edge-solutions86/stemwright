package io.github.rkaushikdev.stemwright.app.services;

import io.github.rkaushikdev.stemwright.app.Clock;
import io.github.rkaushikdev.stemwright.app.DomainEvent;
import io.github.rkaushikdev.stemwright.app.EventBus;
import io.github.rkaushikdev.stemwright.app.LanguageBundle;
import io.github.rkaushikdev.stemwright.app.Loaders;
import io.github.rkaushikdev.stemwright.app.Stores;
import io.github.rkaushikdev.stemwright.domain.course.Actor;
import io.github.rkaushikdev.stemwright.domain.course.Permission;
import io.github.rkaushikdev.stemwright.domain.errors.ConflictException;
import io.github.rkaushikdev.stemwright.domain.errors.ForbiddenException;
import io.github.rkaushikdev.stemwright.domain.errors.NotFoundException;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.paradigm.ProfileBook;
import io.github.rkaushikdev.stemwright.domain.paradigm.ProfileRevision;
import io.github.rkaushikdev.stemwright.domain.paradigm.ProfileRevisionInput;
import io.github.rkaushikdev.stemwright.domain.paradigm.ResolvedProfile;
import io.github.rkaushikdev.stemwright.domain.paradigm.RevisionHistory;
import io.github.rkaushikdev.stemwright.domain.paradigm.SyncretismDisagreement;
import io.github.rkaushikdev.stemwright.domain.time.DayRange;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Publishing a paradigm. The one rule this service adds on top of the domain is that a revision may
 * not take effect in the past: a learner already graded under the old profile would silently be
 * regraded, and the attempt log would then disagree with the mark they were shown.
 *
 * <p>Backdating is possible, and it is a separate call with a separate permission so that it can
 * never happen by accident.
 */
public final class ProfileService {

    private final Stores stores;
    private final EventBus bus;
    private final Clock clock;

    public ProfileService(Stores stores, EventBus bus, Clock clock) {
        this.stores = Objects.requireNonNull(stores, "stores");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * The language's profile book.
     *
     * @throws NotFoundException when the language has none
     */
    public ProfileBook book(String language) {
        return Loaders.requireBook(stores, language);
    }

    /**
     * The revision history of one profile in the language's book.
     *
     * @throws NotFoundException for the book, or of kind {@code profile} naming the key when the
     *     book holds no such profile
     */
    public RevisionHistory history(String language, String profileKey) {
        return book(language).historyFor(profileKey).orElseThrow(() -> new NotFoundException("profile", profileKey));
    }

    /**
     * The revision a class is graded against on the day, found through the class chain.
     *
     * @throws NotFoundException for the language or its book, or of kind {@code profile} naming the
     *     class and the day when no revision is in force for it then
     */
    public ResolvedProfile resolve(String language, String classKey, LocalDate on) {
        LanguageBundle bundle = Loaders.requireLanguage(stores, language);
        return book(language).requireProfileFor(bundle.classes(), classKey, on);
    }

    /**
     * Adds the revision to its profile's history, starting a history when the book holds none for
     * the profile, stores the book and announces the revision.
     *
     * @return the newest revision of the history, which is the one published unless another already
     *     starts later
     * @throws ForbiddenException when the actor may not publish profiles
     * @throws ConflictException when the revision would take effect before today
     * @throws NotFoundException when the language has no book
     * @throws ValidationException when the history refuses the revision, as it does one starting on
     *     a day another revision already holds
     */
    public ProfileRevision publish(Actor actor, String language, ProfileRevisionInput revision) {
        actor.requirePermission(Permission.PUBLISH_PROFILE);
        LocalDate effectiveFrom = revision.effectiveFrom();
        if (effectiveFrom.isBefore(clock.today())) {
            throw new ConflictException(
                    "a revision may not take effect before today, " + effectiveFrom + " is in the past");
        }

        ProfileBook book = book(language);
        String profileKey = revision.profile().key();
        Optional<RevisionHistory> existing = book.historyFor(profileKey);
        RevisionHistory history = existing.map(found -> found.withRevision(revision))
                .orElseGet(() -> RevisionHistory.of(profileKey, List.of(revision)));

        stores.books().put(book.withHistory(history));
        ProfileRevision published = history.latestRevision();
        bus.publish(
                new DomainEvent.ProfilePublished(clock.now(), actor.id(), profileKey, published.id(), effectiveFrom));
        return published;
    }

    /**
     * Binds the class to the profile in the language's book, in place of any binding it had.
     *
     * @throws ForbiddenException when the actor may not publish profiles
     * @throws NotFoundException when the language has no book
     * @throws ValidationException when the book holds no such profile
     */
    public ProfileBook bind(Actor actor, String language, String classKey, String profileKey) {
        actor.requirePermission(Permission.PUBLISH_PROFILE);
        ProfileBook grown = book(language).withBinding(classKey, profileKey);
        stores.books().put(grown);
        return grown;
    }

    /**
     * The language's classes with no binding anywhere up their chain, sorted.
     *
     * @throws NotFoundException for the language or its book
     */
    public List<String> gaps(String language) {
        LanguageBundle bundle = Loaders.requireLanguage(stores, language);
        return book(language).unboundClasses(bundle.classes());
    }

    /**
     * Every pair of bundles the newest revision of the profile declares syncretic and its endings do
     * not make so, or the other way round, one line each, such as {@code case=nom;number=sg and
     * case=abl;number=sg are produced but not declared}.
     *
     * @throws NotFoundException for the book or the profile
     */
    public List<String> disagreements(String language, String profileKey) {
        ProfileRevision current = history(language, profileKey).latestRevision();
        return current.syncretism().reconcileWith(current.profile()).stream()
                .map(ProfileService::describe)
                .toList();
    }

    /**
     * Every revision of the profile in force on at least one day of the range, in ordinal order.
     *
     * @throws NotFoundException for the book or the profile
     */
    public List<ProfileRevision> revisionsIn(String language, String profileKey, DayRange range) {
        return history(language, profileKey).revisionsAcross(range);
    }

    private static String describe(SyncretismDisagreement entry) {
        return entry.first() + " and " + entry.second() + " are " + (entry.declared() ? "declared" : "produced")
                + " but not " + (entry.produced() ? "declared" : "produced");
    }
}
