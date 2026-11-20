package io.github.rkaushikdev.stemwright.domain.lexicon;

import io.github.rkaushikdev.stemwright.domain.errors.ProblemCollector;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.text.Normalise;
import io.github.rkaushikdev.stemwright.domain.text.Text;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The stems of one lexeme, each under its own role, in the order they were given.
 *
 * @param stems the stems, no two with the same role when built through {@link #of(List)}
 */
public record StemSet(List<Stem> stems) {

    private static final int RELATED_OPENING = 3;

    public StemSet {
        stems = List.copyOf(stems);
    }

    /** {@link #of(List)} for stems written inline. */
    public static StemSet of(StemInput... inputs) {
        return of(List.of(inputs));
    }

    /** Checks every stem and refuses an empty set and a role declared twice. */
    public static StemSet of(List<StemInput> inputs) {
        ProblemCollector problems = new ProblemCollector();
        if (inputs.isEmpty()) {
            problems.add("stems", "must hold at least one stem");
        }
        List<Stem> stems = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (StemInput input : inputs) {
            Stem built = Stem.of(input);
            if (!seen.add(built.role())) {
                problems.add("stems." + built.role(), "is declared twice");
                continue;
            }
            stems.add(built);
        }
        problems.throwIfAny("stem set is not usable");
        return new StemSet(stems);
    }

    /** A set holding only a {@link Stem#DEFAULT_ROLE} stem of the given form. */
    public static StemSet single(String form) {
        return of(StemInput.of(Stem.DEFAULT_ROLE, form));
    }

    public List<String> roles() {
        return stems.stream().map(Stem::role).toList();
    }

    public Optional<Stem> find(String role) {
        return stems.stream().filter(entry -> entry.role().equals(role)).findFirst();
    }

    /**
     * Resolves the stem a paradigm slot asks for, falling back to the base stem. The fallback is
     * what makes a one stem lexeme usable by a profile written for a four stem language, and it is
     * why the role a slot names is never assumed to exist.
     *
     * @throws ValidationException when the set has neither the role nor a base stem
     */
    public Stem resolve(String role) {
        return find(role)
                .or(() -> find(Stem.DEFAULT_ROLE))
                .orElseThrow(
                        () -> ValidationException.field("stems", "has neither a " + role + " stem nor a base stem"));
    }

    public boolean hasSuppletion() {
        return stems.stream().anyMatch(Stem::suppletive);
    }

    public List<String> suppletiveRoles() {
        return stems.stream().filter(Stem::suppletive).map(Stem::role).toList();
    }

    /** A copy with the stem added, or put in place of the one with the same role, at the end. */
    public StemSet with(StemInput input) {
        Stem built = Stem.of(input);
        List<Stem> next = new ArrayList<>(stemsOtherThan(built.role()));
        next.add(built);
        return new StemSet(next);
    }

    /**
     * A copy without the role, which tolerates a role that was never there.
     *
     * @throws ValidationException when the role is the only stem left
     */
    public StemSet without(String role) {
        List<Stem> kept = stemsOtherThan(role);
        if (kept.isEmpty()) {
            throw ValidationException.field("stems", "would be left with no stems at all");
        }
        return new StemSet(kept);
    }

    private List<Stem> stemsOtherThan(String role) {
        return stems.stream().filter(entry -> !entry.role().equals(role)).toList();
    }

    /** {@link #looksRelated(String, String, int)} with an opening of three. */
    public static boolean looksRelated(String first, String second) {
        return looksRelated(first, second, RELATED_OPENING);
    }

    /**
     * Two stems are variants when one is a prefix of the other or they share a long enough opening.
     * Used to warn an editor that a stem marked suppletive probably is not, which is a common
     * mistake in a hand written lexicon. The comparison is over normalised, lower cased text, and
     * the shorter stem caps how long an opening can be asked for.
     */
    public static boolean looksRelated(String first, String second, int minimum) {
        String a = Text.lower(Normalise.normaliseInput(first));
        String b = Text.lower(Normalise.normaliseInput(second));
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        int limit = Math.min(a.length(), b.length());
        int shared = 0;
        while (shared < limit && a.charAt(shared) == b.charAt(shared)) {
            shared += 1;
        }
        return shared >= Math.min(minimum, limit);
    }

    /** The roles marked suppletive whose form looks related to the base stem, in set order. */
    public List<String> suspectSuppletion() {
        return find(Stem.DEFAULT_ROLE)
                .map(base -> stems.stream()
                        .filter(entry -> entry.suppletive() && looksRelated(entry.form(), base.form()))
                        .map(Stem::role)
                        .toList())
                .orElse(List.of());
    }
}
