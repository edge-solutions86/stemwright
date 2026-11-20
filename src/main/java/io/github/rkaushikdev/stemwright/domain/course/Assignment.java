package io.github.rkaushikdev.stemwright.domain.course;

import io.github.rkaushikdev.stemwright.domain.IdKind;
import io.github.rkaushikdev.stemwright.domain.Identifiers;
import io.github.rkaushikdev.stemwright.domain.drill.DrillPolicyPatch;
import io.github.rkaushikdev.stemwright.domain.errors.ProblemCollector;
import io.github.rkaushikdev.stemwright.domain.features.FeatureSchema;
import io.github.rkaushikdev.stemwright.domain.features.Selector;
import io.github.rkaushikdev.stemwright.domain.text.Normalise;
import io.github.rkaushikdev.stemwright.domain.time.DayRange;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * A piece of work with a deadline. An assignment narrows one of the course decks and states what
 * counts as done, in its {@link AssignmentTarget}.
 *
 * <p>Build one through {@link #of(FeatureSchema, Course, AssignmentInput)}, which checks it; the
 * canonical constructor only copies what it is given.
 *
 * @param id the derived assignment id, see {@link #idFor}
 * @param courseKey the key of the course it is set on
 * @param key the assignment key, a slug
 * @param title the printed name, the key when none was given
 * @param deckKey the course deck it narrows
 * @param narrowing the part of the deck it covers, {@link Selector#EMPTY} for the whole deck
 * @param window the days it runs, the last of them the day it is due
 * @param target what counts as done
 * @param toleranceLevel the tolerance level it marks at, when it overrides the deck's
 * @param policyPatch its adjustment of the drilling policy, {@link DrillPolicyPatch#EMPTY} for none
 */
public record Assignment(
        String id,
        String courseKey,
        String key,
        String title,
        String deckKey,
        Selector narrowing,
        DayRange window,
        AssignmentTarget target,
        Optional<String> toleranceLevel,
        DrillPolicyPatch policyPatch) {

    private static final int TITLE_LIMIT = 160;

    public Assignment {
        Objects.requireNonNull(narrowing, "narrowing");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(toleranceLevel, "toleranceLevel");
        Objects.requireNonNull(policyPatch, "policyPatch");
    }

    /** The id is derived from the course and the key rather than allocated. */
    public static String idFor(String courseKey, String key) {
        return Identifiers.derivedId(IdKind.ASSIGNMENT, courseKey, key);
    }

    /**
     * Checks an assignment against the course it is set on and the schema its narrowing is written
     * in. A deck the course does not teach, a window that ends before it starts or misses the term
     * entirely, a narrowing the schema refuses, a target below one and the absence of any target are
     * reported together; a key, deck key or tolerance level that is not a slug, and an unusable
     * title, fail on the spot.
     *
     * <p>The window only has to overlap the term, not lie inside it, so revision work set in the
     * last week may run on past the final day.
     *
     * @throws io.github.rkaushikdev.stemwright.domain.errors.ValidationException naming every
     *     problem
     */
    public static Assignment of(FeatureSchema schema, Course course, AssignmentInput input) {
        ProblemCollector problems = new ProblemCollector();
        String key = Identifiers.asSlug("assignment.key", input.key());
        String deckKey = Identifiers.asSlug("assignment.deckKey", input.deckKey());

        problems.addIf(!course.teaches(deckKey), "deckKey", "is not a deck of " + course.key() + ": " + deckKey);

        DayRange term = course.term();
        Optional<DayRange> window = problems.absorbFlat("window", () -> new DayRange(input.from(), input.to()));
        problems.addIf(
                window.isPresent() && !window.get().overlaps(term),
                "window",
                "falls outside the term " + term.from() + " to " + term.to());

        Selector narrowing = problems.absorbFlat(
                        "narrowing",
                        () -> Selector.parse(schema, input.narrowing().orElse(""), "narrowing"))
                .orElse(Selector.EMPTY);

        checkTarget(problems, "masteredCells", input.masteredCells());
        checkTarget(problems, "practiceDays", input.practiceDays());
        problems.addIf(
                input.masteredCells().isEmpty() && input.practiceDays().isEmpty(),
                "target",
                "must set at least one of masteredCells and practiceDays");

        problems.throwIfAny("assignment " + key + " is not usable");

        return new Assignment(
                idFor(course.key(), key),
                course.key(),
                key,
                input.title()
                        .map(title -> Normalise.requireText("assignment.title", title, TITLE_LIMIT))
                        .orElse(key),
                deckKey,
                narrowing,
                window.orElseThrow(),
                new AssignmentTarget(input.masteredCells(), input.practiceDays()),
                input.toleranceLevel().map(level -> Identifiers.asSlug("assignment.toleranceLevel", level)),
                input.policyPatch().orElse(DrillPolicyPatch.EMPTY));
    }

    private static void checkTarget(ProblemCollector problems, String path, Optional<Integer> value) {
        value.filter(count -> count < 1)
                .ifPresent(count -> problems.add(path, "must be a whole number above zero: " + count));
    }

    /** Whether the day falls inside the window, either end included. */
    public boolean isOpenOn(LocalDate on) {
        return window.contains(on);
    }

    /** Whether the day falls after the last day of the window, the day it was due. */
    public boolean isOverdueOn(LocalDate on) {
        return on.isAfter(window.to());
    }

    /** Whether the day is the first day of the window or any day after it. */
    public boolean hasStartedOn(LocalDate on) {
        return !on.isBefore(window.from());
    }

    /**
     * One line for a listing, such as {@code Singular cases: all narrowed to number=sg, 12 cells to
     * the top of the ladder, due 2024-10-27}. An assignment that narrows nothing names the bare deck.
     */
    public String describe() {
        String narrowed = narrowing.clauses().isEmpty() ? deckKey : deckKey + " narrowed to " + narrowing.format();
        return title + ": " + narrowed + ", " + target.describe() + ", due " + window.to();
    }
}
