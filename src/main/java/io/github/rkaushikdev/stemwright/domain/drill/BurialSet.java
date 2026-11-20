package io.github.rkaushikdev.stemwright.domain.drill;

import io.github.rkaushikdev.stemwright.domain.lexicon.Lexeme;
import io.github.rkaushikdev.stemwright.domain.paradigm.Cell;
import io.github.rkaushikdev.stemwright.domain.paradigm.ResolvedProfile;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Burial is the rule that stops a session asking the same question twice in different clothes. If
 * {@code puellae} answers the genitive singular, the dative singular and the nominative plural,
 * asking all three in one sitting teaches the learner that the answer is always {@code puellae},
 * which is the opposite of what a paradigm drill is for.
 *
 * <p>Which cells are twins is not a fact about the cell. It comes from the syncretism map of the
 * profile revision in force on the day, so the same session run against last term's revision buries
 * a different set.
 *
 * <p>A burial set is immutable. Every change returns a new set, and one that changes nothing
 * returns this very set.
 *
 * @param buried the ids of the buried cells, in the order they were buried
 */
public record BurialSet(Set<String> buried) {

    /** The set a session starts with, burying nothing. */
    public static final BurialSet NONE = new BurialSet(Set.of());

    public BurialSet {
        buried = Collections.unmodifiableSet(new LinkedHashSet<>(buried));
    }

    /**
     * The ids of the cells the syncretism map of the revision in force ties to this one, never the
     * cell itself. A cell whose lexeme the lexicon does not hold, or whose profile has no revision
     * in force on the day, has no twins.
     */
    public static List<String> twinCellsOf(DrillContext context, Cell cell, LocalDate on) {
        Optional<Lexeme> entry = context.lexicon().lexemeById(cell.lexemeId());
        if (entry.isEmpty()) {
            return List.of();
        }
        Optional<ResolvedProfile> resolved =
                context.book().profileFor(context.classes(), entry.get().classKey(), on);
        if (resolved.isEmpty()) {
            return List.of();
        }
        return resolved.get().revision().syncretism().twinsOf(cell.bundle()).stream()
                .map(bundle -> Cell.idFor(cell.lexemeId(), bundle))
                .toList();
    }

    /**
     * Burying a cell buries the cell itself as well as its twins. A session that buried only the
     * twins would be free to ask the very cell it just asked.
     */
    public BurialSet bury(DrillContext context, Cell cell, LocalDate on) {
        Set<String> next = new LinkedHashSet<>(buried);
        next.add(cell.id());
        next.addAll(twinCellsOf(context, cell, on));
        return new BurialSet(next);
    }

    public boolean isBuried(String cellId) {
        return buried.contains(cellId);
    }

    /** How many cells are buried. */
    public int size() {
        return buried.size();
    }

    /** A copy without the cell, or this very set when the cell was never buried. */
    public BurialSet unbury(String cellId) {
        if (!buried.contains(cellId)) {
            return this;
        }
        Set<String> next = new LinkedHashSet<>(buried);
        next.remove(cellId);
        return new BurialSet(next);
    }

    /** Every cell of the list that burial blocks, in the order given. */
    public List<Cell> blocked(List<Cell> cells) {
        return cells.stream().filter(cell -> isBuried(cell.id())).toList();
    }

    /** Every cell of the list that burial lets through, in the order given. */
    public List<Cell> surviving(List<Cell> cells) {
        return cells.stream().filter(cell -> !isBuried(cell.id())).toList();
    }
}
