package io.github.rkaushikdev.stemwright.domain.paradigm;

import io.github.rkaushikdev.stemwright.domain.Identifiers;
import io.github.rkaushikdev.stemwright.domain.errors.NotFoundException;
import io.github.rkaushikdev.stemwright.domain.errors.ProblemCollector;
import io.github.rkaushikdev.stemwright.domain.lexicon.ClassRegistry;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The book is where a class meets its paradigm. It holds every revision history by profile key, and
 * a binding from inflection class to profile key.
 *
 * <p>Bindings are inherited up the class tree, so {@code second-declension.neuter} with no binding
 * of its own uses whatever {@code second-declension} uses. That is the useful default and it is
 * also the dangerous one, which is why every lookup returns the class it resolved through rather
 * than only the profile.
 *
 * @param language the language key
 * @param histories every revision history by profile key
 * @param bindings the profile key each class is bound to, by class key
 */
public record ProfileBook(String language, Map<String, RevisionHistory> histories, Map<String, String> bindings) {

    public ProfileBook {
        histories = Collections.unmodifiableMap(new LinkedHashMap<>(histories));
        bindings = Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
    }

    /**
     * Checks a book as assembled, reporting every history given twice and every binding to a
     * profile the book does not hold at once. A class key, profile key or language that is not a
     * slug fails on the spot.
     */
    public static ProfileBook of(ProfileBookInput input) {
        ProblemCollector problems = new ProblemCollector();
        Map<String, RevisionHistory> histories = new LinkedHashMap<>();

        for (RevisionHistory history : input.histories()) {
            if (histories.containsKey(history.profileKey())) {
                problems.add("histories." + history.profileKey(), "appears twice");
                continue;
            }
            histories.put(history.profileKey(), history);
        }

        Map<String, String> bindings = new LinkedHashMap<>();
        for (Map.Entry<String, String> binding : input.bindings().entrySet()) {
            String classKey = Identifiers.asSlug("bindings", binding.getKey());
            String profileKey = Identifiers.asSlug("bindings." + classKey, binding.getValue());
            if (!histories.containsKey(profileKey)) {
                problems.add("bindings." + classKey, "names a profile the book does not hold: " + profileKey);
                continue;
            }
            bindings.put(classKey, profileKey);
        }

        problems.throwIfAny("profile book for " + input.language() + " is not usable");
        return new ProfileBook(Identifiers.asSlug("language", input.language()), histories, bindings);
    }

    public Optional<RevisionHistory> historyFor(String profileKey) {
        return Optional.ofNullable(histories.get(profileKey));
    }

    /** The binding a class uses, its own or the nearest ancestor's, or nothing up the whole chain. */
    public Optional<Binding> bindingFor(ClassRegistry classes, String classKey) {
        return classes.resolveThroughChain(classKey, candidate -> Optional.ofNullable(bindings.get(candidate)))
                .map(resolved -> new Binding(resolved.value(), resolved.through()));
    }

    /**
     * The profile revision a class was graded against on a day. Three things can go missing here
     * and they are different failures: the class may have no binding, the binding may name a
     * history that is gone, or the profile may not have been published yet on that day. Each gives
     * nothing rather than a fallback.
     */
    public Optional<ResolvedProfile> profileFor(ClassRegistry classes, String classKey, LocalDate on) {
        return bindingFor(classes, classKey).flatMap(binding -> historyFor(binding.profileKey())
                .flatMap(history -> history.revisionInForce(on))
                .map(revision -> new ResolvedProfile(revision, binding.boundThrough())));
    }

    /**
     * The profile revision a class was graded against on a day.
     *
     * @throws NotFoundException naming the class and the day when there is none
     */
    public ResolvedProfile requireProfileFor(ClassRegistry classes, String classKey, LocalDate on) {
        return profileFor(classes, classKey, on)
                .orElseThrow(() -> new NotFoundException("profile", classKey + " on " + on));
    }

    /** Classes with no binding anywhere up their chain, which is an editorial hole, sorted. */
    public List<String> unboundClasses(ClassRegistry classes) {
        return classes.classes().keySet().stream()
                .filter(classKey -> bindingFor(classes, classKey).isEmpty())
                .sorted()
                .toList();
    }

    /**
     * Bindings that are shadowed and so can never be reached, sorted: each sits on a class the
     * registry does not hold, so no lookup ever walks through it.
     */
    public List<String> shadowedBindings(ClassRegistry classes) {
        return bindings.keySet().stream()
                .filter(classKey -> classes.chain(classKey).isEmpty())
                .sorted()
                .toList();
    }

    /** A copy with the class bound to the profile, in place of any binding it had, checked again. */
    public ProfileBook withBinding(String classKey, String profileKey) {
        Map<String, String> next = new LinkedHashMap<>(bindings);
        next.put(classKey, profileKey);
        return of(new ProfileBookInput(language, List.copyOf(histories.values()), next));
    }

    /** A copy holding the history in place of any with the same profile key, checked again. */
    public ProfileBook withHistory(RevisionHistory history) {
        List<RevisionHistory> next = new ArrayList<>();
        for (RevisionHistory candidate : histories.values()) {
            if (!candidate.profileKey().equals(history.profileKey())) {
                next.add(candidate);
            }
        }
        next.add(history);
        return of(new ProfileBookInput(language, next, bindings));
    }

    /**
     * The profile a class is bound to and the class the binding was found on.
     *
     * @param profileKey the key of the bound profile
     * @param boundThrough the class whose binding was used, the class itself or an ancestor
     */
    public record Binding(String profileKey, String boundThrough) {}
}
