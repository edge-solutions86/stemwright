package io.github.rkaushikdev.stemwright.domain.drill;

import io.github.rkaushikdev.stemwright.domain.IdKind;
import io.github.rkaushikdev.stemwright.domain.Identifiers;
import io.github.rkaushikdev.stemwright.domain.errors.ProblemCollector;
import io.github.rkaushikdev.stemwright.domain.features.FeatureSchema;
import io.github.rkaushikdev.stemwright.domain.features.Selector;
import io.github.rkaushikdev.stemwright.domain.text.Normalise;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A deck is a saved question about the paradigm space: which lexemes, and which of their cells. It
 * is not a list of cells. Stating it as a query is what lets a lexicon import add fifty nouns to a
 * first year deck without anybody editing the deck, and it is why a deck can be wrong in a way a
 * list cannot, by selecting nothing.
 *
 * <p>Build one through {@link #of(FeatureSchema, DeckInput)}, which checks it; the canonical
 * constructor only copies what it is given.
 *
 * @param id the derived deck id, see {@link #idFor}
 * @param key the deck key, a slug
 * @param label the printed name
 * @param language the language key, taken from the schema
 * @param scope which lexemes and cells the deck takes
 * @param toleranceLevel the tolerance level the deck runs at, absent for the language default
 * @param note an editor's remark on the deck
 */
public record Deck(
        String id,
        String key,
        String label,
        String language,
        DeckScope scope,
        Optional<String> toleranceLevel,
        Optional<String> note) {

    private static final int LABEL_LIMIT = 96;
    private static final int NOTE_LIMIT = 500;

    public Deck {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(toleranceLevel, "toleranceLevel");
        Objects.requireNonNull(note, "note");
    }

    /**
     * The id is derived from the language and the key, so a deck keeps its id through every
     * rescoping.
     */
    public static String idFor(String language, String key) {
        return Identifiers.derivedId(IdKind.DECK, language, key);
    }

    /**
     * Checks a deck as written. A class, tag or lexeme named twice and a cell selector the schema
     * rejects are reported together; a key, class, tag or tolerance level that is not a slug, and a
     * blank or overlong label or note, fail on the spot.
     */
    public static Deck of(FeatureSchema schema, DeckInput input) {
        ProblemCollector problems = new ProblemCollector();
        String key = Identifiers.asSlug("deck.key", input.key());
        DeckScopeInput scope = input.scope();

        List<String> classKeys = slugs("scope.classKeys", scope.classKeys().orElse(List.of()));
        List<String> tags = slugs("scope.tags", scope.tags().orElse(List.of()));
        List<String> lexemeIds = scope.lexemeIds().orElse(List.of());

        problems.addIf(repeats(classKeys), "scope.classKeys", "names the same class twice");
        problems.addIf(repeats(tags), "scope.tags", "names the same tag twice");
        problems.addIf(repeats(lexemeIds), "scope.lexemeIds", "names the same lexeme twice");

        Selector cells = problems.absorbFlat(
                        "scope.cells",
                        () -> Selector.parse(schema, scope.cells().orElse(""), "scope.cells"))
                .orElse(Selector.EMPTY);

        problems.throwIfAny("deck " + key + " is not usable");

        return new Deck(
                idFor(schema.language(), key),
                key,
                input.label()
                        .map(label -> Normalise.requireText("deck.label", label, LABEL_LIMIT))
                        .orElse(key),
                schema.language(),
                new DeckScope(classKeys, tags, lexemeIds, cells),
                input.toleranceLevel().map(level -> Identifiers.asSlug("deck.toleranceLevel", level)),
                input.note().map(note -> Normalise.requireText("deck.note", note, NOTE_LIMIT)));
    }

    private static List<String> slugs(String path, List<String> values) {
        List<String> slugs = new ArrayList<>();
        for (int index = 0; index < values.size(); index += 1) {
            slugs.add(Identifiers.asSlug(path + "." + index, values.get(index)));
        }
        return slugs;
    }

    private static boolean repeats(List<String> values) {
        return new HashSet<>(values).size() != values.size();
    }

    /** A deck that constrains nothing takes the whole language, which is legal. */
    public boolean isWholeLanguage() {
        return scope.isEmpty();
    }

    /**
     * A copy with the parts of the scope the input names replaced and every other part kept, checked
     * again. The key is unchanged, so the copy keeps this deck's id, and so are the label, the
     * tolerance level and the note.
     */
    public Deck withScope(FeatureSchema schema, DeckScopeInput change) {
        DeckScopeInput merged = DeckScopeInput.builder()
                .classKeys(change.classKeys().orElse(scope.classKeys()))
                .tags(change.tags().orElse(scope.tags()))
                .lexemeIds(change.lexemeIds().orElse(scope.lexemeIds()))
                .cells(change.cells().orElseGet(() -> scope.cells().format()))
                .build();
        DeckInput.Builder input = DeckInput.builder(key).label(label).scope(merged);
        toleranceLevel.ifPresent(input::toleranceLevel);
        note.ifPresent(input::note);
        return of(schema, input.build());
    }
}
