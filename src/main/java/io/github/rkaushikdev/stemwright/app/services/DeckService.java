package io.github.rkaushikdev.stemwright.app.services;

import io.github.rkaushikdev.stemwright.app.Clock;
import io.github.rkaushikdev.stemwright.app.Loaders;
import io.github.rkaushikdev.stemwright.app.Stores;
import io.github.rkaushikdev.stemwright.domain.course.Actor;
import io.github.rkaushikdev.stemwright.domain.course.CourseScope;
import io.github.rkaushikdev.stemwright.domain.course.Permission;
import io.github.rkaushikdev.stemwright.domain.drill.Deck;
import io.github.rkaushikdev.stemwright.domain.drill.DeckInput;
import io.github.rkaushikdev.stemwright.domain.drill.DeckScope;
import io.github.rkaushikdev.stemwright.domain.drill.DeckScopeInput;
import io.github.rkaushikdev.stemwright.domain.drill.Membership;
import io.github.rkaushikdev.stemwright.domain.errors.ConflictException;
import io.github.rkaushikdev.stemwright.domain.errors.ForbiddenException;
import io.github.rkaushikdev.stemwright.domain.errors.NotFoundException;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.features.FeatureSchema;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Deck editing, with one rule the domain deliberately does not enforce: a deck that selects nothing
 * is legal to build and refused on save.
 *
 * <p>The domain has to allow it, because a deck can go empty later when a class is renamed or a
 * profile is retired, and a model that could not represent that state could not report it either.
 * The service is the right place to stop somebody creating one on purpose.
 */
public final class DeckService {

    private final Stores stores;
    private final Clock clock;
    private final DrillService drill;

    /**
     * @param drill the drill service the deck's membership is resolved through
     */
    public DeckService(Stores stores, Clock clock, DrillService drill) {
        this.stores = Objects.requireNonNull(stores, "stores");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.drill = Objects.requireNonNull(drill, "drill");
    }

    /**
     * The deck with the key in the language.
     *
     * @throws NotFoundException naming {@code language/key} when it is not stored
     */
    public Deck get(String language, String key) {
        return Loaders.requireDeck(stores, language, key);
    }

    /** Every deck of the language, by key as a reader sorts it. */
    public List<Deck> list(String language) {
        return stores.decks().listByLanguage(language);
    }

    /**
     * Checks and stores a new deck, provided it selects at least one cell today.
     *
     * @throws ForbiddenException when the actor may not edit decks of the course
     * @throws ConflictException when the language already has a deck with the key, or when the deck
     *     selects no cells today
     * @throws NotFoundException for the language or its profile book
     * @throws ValidationException when the domain refuses the deck
     */
    public Deck create(Actor actor, CourseScope scope, String language, DeckInput input) {
        actor.requirePermission(Permission.EDIT_DECK, scope);
        if (stores.decks().get(language, input.key()).isPresent()) {
            throw new ConflictException("deck " + language + "/" + input.key() + " already exists");
        }
        return saveChecked(language, input, clock.today());
    }

    /**
     * Replaces the parts of a stored deck's scope that the change names and stores it again,
     * provided it still selects at least one cell today. The label, tolerance level and note are
     * kept. The cell selector is the change's own: a change that names no cells clears it.
     *
     * @throws ForbiddenException when the actor may not edit decks of the course
     * @throws NotFoundException naming {@code language/key} when the deck is not stored, or for the
     *     language or its profile book
     * @throws ConflictException when the rescoped deck selects no cells today
     * @throws ValidationException when the domain refuses the rescoped deck
     */
    public Deck rescope(Actor actor, CourseScope scope, String language, String key, DeckScopeInput change) {
        actor.requirePermission(Permission.EDIT_DECK, scope);
        Deck existing = Loaders.requireDeck(stores, language, key);
        Deck widened = existing.withScope(schemaOf(language), change);

        DeckScope merged = widened.scope();
        DeckScopeInput.Builder rescoped = DeckScopeInput.builder()
                .classKeys(merged.classKeys())
                .tags(merged.tags())
                .lexemeIds(merged.lexemeIds());
        change.cells().ifPresent(rescoped::cells);

        DeckInput.Builder input =
                DeckInput.builder(widened.key()).label(widened.label()).scope(rescoped.build());
        widened.toleranceLevel().ifPresent(input::toleranceLevel);
        widened.note().ifPresent(input::note);
        return saveChecked(language, input.build(), clock.today());
    }

    /**
     * Removes a stored deck.
     *
     * @throws ForbiddenException when the actor may not edit decks of the course
     * @throws NotFoundException naming {@code language/key} when the deck is not stored
     */
    public void remove(Actor actor, CourseScope scope, String language, String key) {
        actor.requirePermission(Permission.EDIT_DECK, scope);
        Loaders.requireDeck(stores, language, key);
        stores.decks().remove(language, key);
    }

    /** What the deck would select today, without saving it. */
    public DeckPreview preview(String language, DeckInput input) {
        return preview(language, input, clock.today());
    }

    /**
     * What the deck would select on the day, without saving it. A deck that selects nothing is
     * reported in the warnings rather than refused.
     *
     * @throws NotFoundException for the language or its profile book
     * @throws ValidationException when the domain refuses the deck
     */
    public DeckPreview preview(String language, DeckInput input, LocalDate on) {
        Deck built = Deck.of(schemaOf(language), input);
        Membership membership = Membership.resolve(drill.context(language), built, on);
        return new DeckPreview(built, membership, membership.warnings());
    }

    private FeatureSchema schemaOf(String language) {
        return Loaders.requireLanguage(stores, language).schema();
    }

    private Deck saveChecked(String language, DeckInput input, LocalDate on) {
        DeckPreview preview = preview(language, input, on);
        if (preview.membership().cells().isEmpty()) {
            throw new ConflictException("deck " + input.key() + " selects no cells on " + on);
        }
        stores.decks().put(preview.deck());
        return preview.deck();
    }
}
