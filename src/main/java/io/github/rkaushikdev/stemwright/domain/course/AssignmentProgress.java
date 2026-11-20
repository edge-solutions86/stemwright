package io.github.rkaushikdev.stemwright.domain.course;

import io.github.rkaushikdev.stemwright.domain.drill.Deck;
import io.github.rkaushikdev.stemwright.domain.drill.DrillContext;
import io.github.rkaushikdev.stemwright.domain.drill.Membership;
import io.github.rkaushikdev.stemwright.domain.features.FeatureBundle;
import io.github.rkaushikdev.stemwright.domain.features.Selector;
import io.github.rkaushikdev.stemwright.domain.mastery.Attempt;
import io.github.rkaushikdev.stemwright.domain.mastery.CellStrength;
import io.github.rkaushikdev.stemwright.domain.mastery.Ladder;
import io.github.rkaushikdev.stemwright.domain.mastery.MasteryLedger;
import io.github.rkaushikdev.stemwright.domain.paradigm.Cell;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import io.github.rkaushikdev.stemwright.domain.time.DayRange;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * How far a learner has got on one assignment.
 *
 * <p>The cell set is the deck narrowed by the assignment, which is an intersection of two selectors
 * and can be empty. An assignment that selects nothing is a setting mistake rather than a learner
 * failure, and it is reported as such instead of showing up as nought progress.
 *
 * <p>Practice days are counted inside the assignment window and inside the enrolment window, so a
 * learner who joined halfway through is not marked down for the days before they were on the
 * register.
 *
 * @param assignmentKey the key of the assignment
 * @param learnerId the learner measured
 * @param on the day the progress was read on
 * @param cells how many cells the assignment selects
 * @param mastered how many of them are at the top of the ladder
 * @param practiceDays how many different days the learner practised them, inside both windows
 * @param masteryMet whether the mastery target is met, true when the assignment sets none
 * @param practiceMet whether the practice target is met, true when the assignment sets none
 * @param met whether both targets are met
 * @param overdue whether the day falls after the day the assignment was due
 * @param selectsNothing whether the assignment selects no cell at all
 */
public record AssignmentProgress(
        String assignmentKey,
        String learnerId,
        LocalDate on,
        int cells,
        int mastered,
        int practiceDays,
        boolean masteryMet,
        boolean practiceMet,
        boolean met,
        boolean overdue,
        boolean selectsNothing) {

    public AssignmentProgress {
        Objects.requireNonNull(assignmentKey, "assignmentKey");
        Objects.requireNonNull(learnerId, "learnerId");
        Objects.requireNonNull(on, "on");
    }

    /**
     * The cells of the deck on the day that the assignment's narrowing also admits, in membership
     * order. Empty when the two selectors do not overlap.
     */
    public static List<Cell> cellsOf(DrillContext context, Assignment entry, Deck deck, LocalDate on) {
        Selector narrowed = deck.scope().cells().intersect(context.schema(), entry.narrowing());
        Membership membership = Membership.resolve(context, deck, on);
        return membership.cells().stream()
                .filter(cell -> narrowed.matches(FeatureBundle.parseKey(context.schema(), cell.bundle())))
                .toList();
    }

    /**
     * Reads one learner's progress on the assignment on a day.
     *
     * <p>A cell counts as mastered when it has been answered, is not suspended and sits at the top of
     * the ladder. A practice day is a day on which the learner answered any selected cell, from the
     * first day of the assignment to the day read or the day it was due, whichever comes first, and
     * inside the enrolment window.
     */
    public static AssignmentProgress of(
            DrillContext context,
            Assignment entry,
            Deck deck,
            Enrolment enrolment,
            MasteryLedger ledger,
            List<Attempt> attempts,
            Ladder ladder,
            LocalDate on) {
        List<Cell> cells = cellsOf(context, entry, deck, on);
        Set<String> cellIds = cells.stream().map(Cell::id).collect(Collectors.toSet());

        int mastered = (int) cells.stream()
                .map(cell -> ledger.strengthFor(cell.id()))
                .filter(strength -> isMastered(strength, ladder))
                .count();

        Set<LocalDate> days = new HashSet<>();
        LocalDate countedFrom = entry.window().from();
        LocalDate countedTo = CivilDates.minCivil(entry.window().to(), on);
        if (!countedTo.isBefore(countedFrom)) {
            DayRange counted = DayRange.of(countedFrom, countedTo);
            for (Attempt attempt : attempts) {
                if (cellIds.contains(attempt.cellId())
                        && counted.contains(attempt.on())
                        && enrolment.window().contains(attempt.on())) {
                    days.add(attempt.on());
                }
            }
        }

        AssignmentTarget target = entry.target();
        boolean masteryMet =
                target.masteredCells().map(wanted -> mastered >= wanted).orElse(true);
        boolean practiceMet =
                target.practiceDays().map(wanted -> days.size() >= wanted).orElse(true);

        return new AssignmentProgress(
                entry.key(),
                enrolment.learnerId(),
                on,
                cells.size(),
                mastered,
                days.size(),
                masteryMet,
                practiceMet,
                masteryMet && practiceMet,
                entry.isOverdueOn(on),
                cells.isEmpty());
    }

    private static boolean isMastered(CellStrength strength, Ladder ladder) {
        return !strength.suspended() && strength.seen() > 0 && ladder.isAtTop(strength.rung());
    }

    /** The share of the selected cells that are mastered, nought when nothing is selected. */
    public double masteryShare() {
        return cells == 0 ? 0 : (double) mastered / cells;
    }

    /**
     * One line for a listing, such as {@code week-3 is in progress, 0 of 15 cells mastered over 0
     * days}. An assignment past its deadline and not met is missed, and one that selects nothing says
     * so instead.
     */
    public String describe() {
        if (selectsNothing) {
            return assignmentKey + " selects no cells at all";
        }
        String state = met ? "met" : overdue ? "missed" : "in progress";
        return assignmentKey + " is " + state + ", " + mastered + " of " + cells + " cells mastered over "
                + practiceDays + " days";
    }

    /** The targets not yet met, {@code mastery} before {@code practice}. */
    public List<String> unmetTargets() {
        List<String> unmet = new ArrayList<>(2);
        if (!masteryMet) {
            unmet.add("mastery");
        }
        if (!practiceMet) {
            unmet.add("practice");
        }
        return List.copyOf(unmet);
    }
}
