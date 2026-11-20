package io.github.rkaushikdev.stemwright.domain.ortho;

import io.github.rkaushikdev.stemwright.domain.Identifiers;
import io.github.rkaushikdev.stemwright.domain.errors.ProblemCollector;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.ortho.ResolvedTolerance.Source;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The tolerance levels one language declares, and the one that applies when nothing names a level.
 *
 * <p>The levels themselves are per language. Ignoring accents is a real choice in Greek and
 * meaningless in Latin, so the fold list behind a level name is part of the language's orthography
 * profile rather than a constant.
 *
 * @param language the language key
 * @param levels the levels in declaration order
 * @param defaultLevel the key of the level that applies when nothing more specific names one
 */
public record OrthoProfile(String language, List<ToleranceLevel> levels, String defaultLevel) {

    public OrthoProfile {
        levels = List.copyOf(levels);
    }

    /**
     * Checks a profile as written, reporting every problem with its levels at once. Each level's
     * folds are put into {@link Folding#ORDER} and a missing label becomes the key. A language or
     * level key that is not a slug is refused on its own, as soon as it is read.
     */
    public static OrthoProfile of(OrthoProfileInput input) {
        ProblemCollector problems = new ProblemCollector();
        String language = Identifiers.asSlug("language", input.language());
        List<ToleranceLevel> levels = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        List<OrthoProfileInput.Level> declared = input.levels();
        for (int index = 0; index < declared.size(); index += 1) {
            OrthoProfileInput.Level raw = declared.get(index);
            String path = "levels." + index;
            String key = Identifiers.asSlug(path + ".key", raw.key());
            if (!seen.add(key)) {
                problems.add(path, "repeats the level key " + key);
                continue;
            }
            problems.absorbFlat(path, () -> Folding.normaliseFoldList(raw.folds()))
                    .ifPresent(folds ->
                            levels.add(new ToleranceLevel(key, raw.label().orElse(key), folds)));
        }

        if (levels.isEmpty()) {
            problems.add("levels", "must declare at least one level");
        }
        String defaultLevel = Identifiers.asSlug("defaultLevel", input.defaultLevel());
        if (!levels.isEmpty() && !seen.contains(defaultLevel)) {
            problems.add("defaultLevel", "names a level that is not declared: " + defaultLevel);
        }

        problems.throwIfAny("orthography profile for " + language + " is not usable");
        return new OrthoProfile(language, levels, defaultLevel);
    }

    public Optional<ToleranceLevel> levelOf(String key) {
        return levels.stream().filter(level -> level.key().equals(key)).findFirst();
    }

    /**
     * The level declared under the key.
     *
     * @throws ValidationException on the {@code level} field when the language declares no such level
     */
    public ToleranceLevel requireLevel(String key) {
        return levelOf(key)
                .orElseThrow(() -> ValidationException.field("level", "is not a level of " + language + ": " + key));
    }

    /** The level {@link #defaultLevel()} names. */
    public ToleranceLevel defaultToleranceLevel() {
        return requireLevel(defaultLevel);
    }

    /** The level keys in declaration order. */
    public List<String> levelKeys() {
        return levels.stream().map(ToleranceLevel::key).toList();
    }

    /** The language default, for an answer nothing names a level for. */
    public ResolvedTolerance resolveTolerance() {
        return resolveTolerance(ToleranceRequest.NONE);
    }

    /**
     * Resolves which level applies, most specific first: the assignment, then the cell, then the
     * deck, and the language default when none of them names one.
     *
     * @throws ValidationException when the level named is not one this language declares, wherever
     *     it was named
     */
    public ResolvedTolerance resolveTolerance(ToleranceRequest request) {
        return request.assignmentLevel()
                .map(key -> new ResolvedTolerance(requireLevel(key), Source.ASSIGNMENT))
                .or(() -> request.cellLevel().map(key -> new ResolvedTolerance(requireLevel(key), Source.CELL)))
                .or(() -> request.deckLevel().map(key -> new ResolvedTolerance(requireLevel(key), Source.DECK)))
                .orElseGet(() -> new ResolvedTolerance(defaultToleranceLevel(), Source.LANGUAGE));
    }
}
