package io.github.rkaushikdev.stemwright.domain.report;

import io.github.rkaushikdev.stemwright.domain.course.Actor;
import io.github.rkaushikdev.stemwright.domain.course.AssignmentProgress;
import io.github.rkaushikdev.stemwright.domain.course.CourseScope;
import io.github.rkaushikdev.stemwright.domain.text.Collation;
import io.github.rkaushikdev.stemwright.domain.text.Numbers;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A cohort report over one assignment. It is built from progress rows that were computed elsewhere,
 * so it never touches a ledger and never has to be told which day it is.
 *
 * <p>What it does own is the redaction. A viewer sees the rows they are allowed to see and a count
 * of the rows they are not, rather than a shorter list that quietly reads as a smaller class. A
 * report that hides its own gaps is worse than one that refuses to run.
 *
 * @param assignmentKey the assignment reported on
 * @param on the day the progress was read on
 * @param rows the rows the viewer may see, highest share first, ties in learner id order as a
 *     reader sorts them
 * @param hidden how many rows the viewer may not see
 * @param metCount how many of the visible rows have met the target
 * @param medianShare the median share over the visible rows, nought when there are none
 */
public record CohortReport(
        String assignmentKey, LocalDate on, List<CohortRow> rows, int hidden, int metCount, double medianShare) {

    private static final int BEHIND_SHOWN = 5;

    private static final Comparator<CohortRow> HIGHEST_SHARE_FIRST = Comparator.comparingDouble(CohortRow::share)
            .reversed()
            .thenComparing(CohortRow::learnerId, Collation.ORDER);

    private static final Comparator<CohortRow> LOWEST_SHARE_FIRST =
            Comparator.comparingDouble(CohortRow::share).thenComparing(CohortRow::learnerId, Collation.ORDER);

    public CohortReport {
        Objects.requireNonNull(assignmentKey, "assignmentKey");
        Objects.requireNonNull(on, "on");
        rows = List.copyOf(rows);
    }

    /** The middle value, the mean of the middle two for an even count, and nought for none. */
    public static double median(List<Double> values) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Double> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }

    /**
     * Builds the report the actor may see: a row for every learner whose work they may read, and a
     * count of the rest.
     */
    public static CohortReport of(
            Actor actor, CourseScope scope, List<AssignmentProgress> progresses, String assignmentKey, LocalDate on) {
        List<CohortRow> visible = new ArrayList<>();
        int hidden = 0;

        for (AssignmentProgress progress : progresses) {
            if (actor.canReadLearner(progress.learnerId(), scope)) {
                visible.add(CohortRow.of(progress));
            } else {
                hidden += 1;
            }
        }

        visible.sort(HIGHEST_SHARE_FIRST);

        return new CohortReport(
                assignmentKey,
                on,
                visible,
                hidden,
                (int) visible.stream().filter(CohortRow::met).count(),
                median(visible.stream().map(CohortRow::share).toList()));
    }

    /** True when some row was hidden from the viewer. */
    public boolean isRedacted() {
        return hidden > 0;
    }

    /** The share of the visible rows that have met the target, nought when there are none. */
    public double metShare() {
        return rows.isEmpty() ? 0 : (double) metCount / rows.size();
    }

    /** {@link #furthestBehind(int)} keeping five. */
    public List<CohortRow> furthestBehind() {
        return furthestBehind(BEHIND_SHOWN);
    }

    /**
     * The learners furthest from the target, which is who a teacher chases: the visible rows that
     * have not met it, lowest share first, ties in learner id order as a reader sorts them.
     *
     * @param limit how many to keep; a negative limit keeps every row but that many from the near end
     */
    public List<CohortRow> furthestBehind(int limit) {
        return Ranking.head(
                rows.stream()
                        .filter(row -> !row.met())
                        .sorted(LOWEST_SHARE_FIRST)
                        .toList(),
                limit);
    }

    /**
     * One line for the viewer, such as {@code 1 of 3 have met week-3, 33 percent}, saying how many
     * rows were hidden from them when any were.
     */
    public String describe() {
        if (rows.isEmpty()) {
            return hidden == 0
                    ? "nobody is working on " + assignmentKey
                    : "every row of " + assignmentKey + " is hidden from you";
        }
        long percent = Numbers.round(metShare() * 100);
        String tail = hidden == 0 ? "" : ", " + hidden + " rows hidden from you";
        return metCount + " of " + rows.size() + " have met " + assignmentKey + ", " + percent + " percent" + tail;
    }
}
