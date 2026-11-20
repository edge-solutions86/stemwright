package io.github.rkaushikdev.stemwright.app.services;

import io.github.rkaushikdev.stemwright.app.Clock;
import io.github.rkaushikdev.stemwright.app.DomainEvent;
import io.github.rkaushikdev.stemwright.app.EventBus;
import io.github.rkaushikdev.stemwright.app.LanguageBundle;
import io.github.rkaushikdev.stemwright.app.Loaders;
import io.github.rkaushikdev.stemwright.app.Stores;
import io.github.rkaushikdev.stemwright.domain.course.Actor;
import io.github.rkaushikdev.stemwright.domain.course.Permission;
import io.github.rkaushikdev.stemwright.domain.errors.ForbiddenException;
import io.github.rkaushikdev.stemwright.domain.errors.NotFoundException;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.lexicon.Lexeme;
import io.github.rkaushikdev.stemwright.domain.lexicon.LexemeContext;
import io.github.rkaushikdev.stemwright.domain.lexicon.LexemeInput;
import io.github.rkaushikdev.stemwright.domain.lexicon.Lexicon;
import io.github.rkaushikdev.stemwright.domain.text.Normalise;
import io.github.rkaushikdev.stemwright.domain.text.Text;
import java.util.List;
import java.util.Objects;

/**
 * The lexicon service. It is thin on purpose: every rule about what a lexeme may be lives in the
 * domain, and what is left here is loading what the domain needs, checking who is asking, and
 * telling the world afterwards.
 *
 * <p>It rebuilds the whole {@link Lexicon} on every read. That is the right trade at a few thousand
 * lexemes and the wrong one at a million, and the note is here so the next person changing it knows
 * which side of that line the decision was made on.
 */
public final class LexiconService {

    private final Stores stores;
    private final EventBus bus;
    private final Clock clock;

    public LexiconService(Stores stores, EventBus bus, Clock clock) {
        this.stores = Objects.requireNonNull(stores, "stores");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * The language's bundle.
     *
     * @throws NotFoundException when the language is not configured
     */
    public LanguageBundle language(String name) {
        return Loaders.requireLanguage(stores, name);
    }

    /** A lexicon of every lexeme stored for the language, empty for a language with none. */
    public Lexicon load(String language) {
        return Lexicon.build(language, stores.lexemes().listByLanguage(language));
    }

    /**
     * The lexeme with the id.
     *
     * @throws NotFoundException of kind {@code lexeme} when it is not stored
     */
    public Lexeme get(String id) {
        return requireLexeme(id);
    }

    /**
     * Checks a new lexeme against the language, stores it and announces it.
     *
     * @throws ForbiddenException when the actor may not edit the lexicon
     * @throws NotFoundException when the language is not configured
     * @throws ValidationException when the domain refuses the lexeme, which is then not stored
     */
    public Lexeme add(Actor actor, String language, LexemeInput input) {
        actor.requirePermission(Permission.EDIT_LEXICON);
        Lexeme built = Lexeme.of(contextOf(language(language)), input);
        stores.lexemes().put(built);
        bus.publish(new DomainEvent.LexemeAdded(clock.now(), actor.id(), built.id(), language));
        return built;
    }

    /**
     * Checks the input against the language and stores it in place of the lexeme with the id. The
     * stored lexeme takes the id its lemma and homograph derive, so an input that changes either is
     * stored beside the lexeme it was meant to replace rather than over it.
     *
     * @throws ForbiddenException when the actor may not edit the lexicon
     * @throws NotFoundException of kind {@code lexeme} when nothing is stored under the id, or when
     *     the language is not configured
     * @throws ValidationException when the domain refuses the lexeme
     */
    public Lexeme replace(Actor actor, String language, String id, LexemeInput input) {
        actor.requirePermission(Permission.EDIT_LEXICON);
        requireLexeme(id);
        Lexeme built = Lexeme.of(contextOf(language(language)), input);
        stores.lexemes().put(built);
        return built;
    }

    /**
     * Removes the lexeme with the id.
     *
     * @throws ForbiddenException when the actor may not edit the lexicon
     * @throws NotFoundException of kind {@code lexeme} when it is not stored
     */
    public void remove(Actor actor, String id) {
        actor.requirePermission(Permission.EDIT_LEXICON);
        requireLexeme(id);
        stores.lexemes().remove(id);
    }

    /**
     * The language's lexemes whose lemma or any gloss contains the term, ignoring case and the space
     * around the term, in word list order. A blank term matches nothing.
     */
    public List<Lexeme> search(String language, String term) {
        String needle = Text.lower(Normalise.normaliseInput(term));
        if (needle.isEmpty()) {
            return List.of();
        }
        return stores.lexemes().listByLanguage(language).stream()
                .filter(entry -> Text.lower(entry.lemma()).contains(needle)
                        || entry.glosses().stream()
                                .anyMatch(gloss -> Text.lower(gloss).contains(needle)))
                .toList();
    }

    /**
     * The language's lexemes of the class or of any class beneath it, in word list order.
     *
     * @throws NotFoundException when the language is not configured
     */
    public List<Lexeme> byClass(String language, String classKey) {
        LanguageBundle bundle = language(language);
        return load(language).lexemesUnderClass(bundle.classes(), classKey);
    }

    /** The language's lexemes carrying the tag, in word list order. */
    public List<Lexeme> byTag(String language, String tag) {
        return load(language).lexemesByTag(tag);
    }

    private Lexeme requireLexeme(String id) {
        return stores.lexemes().get(id).orElseThrow(() -> new NotFoundException("lexeme", id));
    }

    private static LexemeContext contextOf(LanguageBundle bundle) {
        return new LexemeContext(bundle.schema(), bundle.classes());
    }
}
