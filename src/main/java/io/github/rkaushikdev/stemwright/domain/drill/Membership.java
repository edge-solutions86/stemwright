package io.github.rkaushikdev.stemwright.domain.drill;

import io.github.rkaushikdev.stemwright.domain.Grouping;
import io.github.rkaushikdev.stemwright.domain.features.FeatureBundle;
import io.github.rkaushikdev.stemwright.domain.lexicon.ClassRegistry;
import io.github.rkaushikdev.stemwright.domain.lexicon.Lexeme;
import io.github.rkaushikdev.stemwright.domain.paradigm.Cell;
import io.github.rkaushikdev.stemwright.domain.paradigm.ResolvedProfile;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A deck turned back into cells on one day. Everything the drill layer does starts here, and it
 * depends on the day: the profile in force decides which slots exist, so the same deck can hold a
 * different number of cells this term than last.
 *
 * @param deckKey the key of the deck that was resolved
 * @param on the day it was resolved for
 * @param cells every cell the deck takes, lexeme by lexeme in word list order, each in slot order
 * @param lexemes the lexemes that contributed at least one cell, in word list order
 * @param skipped every other lexeme of the language and why it contributed nothing
 */
public record Membership(
        String deckKey, LocalDate on, List<Cell> cells, List<Lexeme> lexemes, List<SkippedLexeme> skipped) {

    public Membership {
        Objects.requireNonNull(deckKey, "deckKey");
        Objects.requireNonNull(on, "on");
        cells = List.copyOf(cells);
        lexemes = List.copyOf(lexemes);
        skipped = List.copyOf(skipped);
    }

    /**
     * The parts of the scope intersect, and a lexeme that fails any of them is out. A lexeme that is
     * in scope but yields no cells is reported separately, because the two are different editorial
     * problems: the first is a deck that is narrower than the editor thought, the second is a
     * profile that is missing or a cell selector nothing satisfies.
     */
    public static Membership resolve(DrillContext context, Deck deck, LocalDate on) {
        List<Cell> cells = new ArrayList<>();
        List<Lexeme> lexemes = new ArrayList<>();
        List<SkippedLexeme> skipped = new ArrayList<>();

        for (Lexeme entry : context.lexicon().allLexemes()) {
            if (!inLexemeScope(deck.scope(), context.classes(), entry)) {
                skipped.add(skip(entry, SkipReason.OUT_OF_SCOPE));
                continue;
            }

            if (context.book().bindingFor(context.classes(), entry.classKey()).isEmpty()) {
                skipped.add(skip(entry, SkipReason.UNBOUND_CLASS));
                continue;
            }

            Optional<ResolvedProfile> resolved = context.book().profileFor(context.classes(), entry.classKey(), on);
            if (resolved.isEmpty()) {
                skipped.add(skip(entry, SkipReason.PROFILE_NOT_PUBLISHED));
                continue;
            }

            List<Cell> expanded = Cell.expand(
                            context.schema(), entry, resolved.get().revision().profile())
                    .stream()
                    .filter(cell ->
                            deck.scope().cells().matches(FeatureBundle.parseKey(context.schema(), cell.bundle())))
                    .toList();

            if (expanded.isEmpty()) {
                skipped.add(skip(entry, SkipReason.NO_CELL_IN_SCOPE));
                continue;
            }

            lexemes.add(entry);
            cells.addAll(expanded);
        }

        return new Membership(deck.key(), on, cells, lexemes, skipped);
    }

    private static SkippedLexeme skip(Lexeme entry, SkipReason reason) {
        return new SkippedLexeme(entry.id(), entry.lemma(), reason);
    }

    private static boolean inLexemeScope(DeckScope scope, ClassRegistry classes, Lexeme entry) {
        if (!scope.lexemeIds().isEmpty() && !scope.lexemeIds().contains(entry.id())) {
            return false;
        }
        if (!scope.classKeys().isEmpty()
                && scope.classKeys().stream()
                        .noneMatch(classKey -> entry.classKey().equals(classKey)
                                || classes.isDescendantOf(entry.classKey(), classKey))) {
            return false;
        }
        return entry.tags().containsAll(scope.tags());
    }

    /** How many cells the deck takes. */
    public int size() {
        return cells.size();
    }

    public boolean isEmpty() {
        return cells.isEmpty();
    }

    /** The lexemes skipped for the reason, in word list order. */
    public List<SkippedLexeme> skippedFor(SkipReason reason) {
        return skipped.stream().filter(entry -> entry.reason() == reason).toList();
    }

    /**
     * The reasons worth telling an editor about. {@link SkipReason#OUT_OF_SCOPE} is not one: a deck
     * excluding most of the lexicon is the normal case, and reporting it would bury the two reasons
     * that mean something is broken.
     */
    public List<String> warnings() {
        List<String> warnings = new ArrayList<>();
        List<SkippedLexeme> unbound = skippedFor(SkipReason.UNBOUND_CLASS);
        if (!unbound.isEmpty()) {
            warnings.add(unbound.size() + " lexemes have no paradigm bound to their class");
        }
        List<SkippedLexeme> unpublished = skippedFor(SkipReason.PROFILE_NOT_PUBLISHED);
        if (!unpublished.isEmpty()) {
            warnings.add(unpublished.size() + " lexemes use a profile with no revision in force on " + on);
        }
        List<SkippedLexeme> empty = skippedFor(SkipReason.NO_CELL_IN_SCOPE);
        if (!empty.isEmpty()) {
            warnings.add(empty.size() + " lexemes have no cell matching the deck selector");
        }
        if (cells.isEmpty()) {
            warnings.add("the deck selects nothing at all");
        }
        return warnings;
    }

    /** The cells under their lexeme id, lexemes in the order their first cell comes. */
    public Map<String, List<Cell>> cellsByLexeme() {
        Map<String, List<Cell>> grouped = new LinkedHashMap<>();
        Grouping.groupBy(cells, Cell::lexemeId)
                .forEach((lexemeId, bucket) -> grouped.put(lexemeId, List.copyOf(bucket)));
        return Collections.unmodifiableMap(grouped);
    }

    /** Every bundle some cell sits in, once each, in natural string order. */
    public List<String> bundlesCovered() {
        return cells.stream().map(Cell::bundle).distinct().sorted().toList();
    }
}
