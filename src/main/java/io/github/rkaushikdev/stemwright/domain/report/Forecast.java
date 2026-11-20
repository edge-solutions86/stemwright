package io.github.rkaushikdev.stemwright.domain.report;

import io.github.rkaushikdev.stemwright.domain.drill.DrillPolicy;
import io.github.rkaushikdev.stemwright.domain.mastery.CellStrength;
import io.github.rkaushikdev.stemwright.domain.mastery.Ladder;
import io.github.rkaushikdev.stemwright.domain.mastery.MasteryLedger;
import io.github.rkaushikdev.stemwright.domain.paradigm.Cell;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import io.github.rkaushikdev.stemwright.domain.time.DayRange;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * How much work is coming. A forecast is not a plan: it reads the due dates already written down
 * and says how many fall on each day, without pretending to know whether the learner will get them
 * right.
 *
 * <p>The optimistic projection does pretend, and says so in its name. It assumes every review is
 * answered correctly on the day it falls due and follows the ladder forward, which gives the
 * lightest possible future and is useful only as a floor.
 */
public final class Forecast {

    private static final Comparator<ForecastDay> BUSIEST_FIRST =
            Comparator.comparingInt(ForecastDay::due).reversed().thenComparing(ForecastDay::on);

    private Forecast() {}

    /**
     * One row for every day of the range, in order, counting the cells that fall due on it. A cell
     * already overdue when the range starts is counted on its first day, and a suspended cell or one
     * nobody has answered is not counted at all.
     */
    public static List<ForecastDay> daily(List<Cell> cells, MasteryLedger ledger, DayRange range, DrillPolicy policy) {
        Map<LocalDate, Integer> counts = new HashMap<>();
        for (Cell cell : cells) {
            CellStrength strength = ledger.strengthFor(cell.id());
            if (strength.suspended()) {
                continue;
            }
            strength.dueOn().ifPresent(due -> counts.merge(CivilDates.maxCivil(due, range.from()), 1, Integer::sum));
        }

        return range.eachDay()
                .map(day -> {
                    int due = counts.getOrDefault(day, 0);
                    return new ForecastDay(
                            day, due, due > policy.reviewsPerDay(), !policy.allowsDay(CivilDates.weekday(day)));
                })
                .toList();
    }

    /** The day with the most due, the earliest of several equally busy; nothing for no days. */
    public static Optional<ForecastDay> busiestDay(List<ForecastDay> days) {
        return days.stream().min(BUSIEST_FIRST);
    }

    public static int totalDue(List<ForecastDay> days) {
        return days.stream().mapToInt(ForecastDay::due).sum();
    }

    public static List<ForecastDay> daysOverCeiling(List<ForecastDay> days) {
        return days.stream().filter(ForecastDay::overCeiling).toList();
    }

    /** Reviews due on a day the policy rests, which will spill onto the next day. */
    public static int spilledOnRestDays(List<ForecastDay> days) {
        return days.stream()
                .filter(ForecastDay::resting)
                .mapToInt(ForecastDay::due)
                .sum();
    }

    /**
     * Walks forward assuming every answer is right, introducing new cells at the policy budget. Each
     * cell is followed along the ladder, so the shape of the curve comes from the rungs rather than
     * from a decay model. A rest day does nothing at all, and a suspended cell takes no part.
     */
    public static List<ProjectionDay> optimisticProjection(
            List<Cell> cells, MasteryLedger ledger, Ladder ladder, DrillPolicy policy, DayRange range) {
        List<Pending> pending = new ArrayList<>();
        int untouched = 0;

        for (Cell cell : cells) {
            CellStrength strength = ledger.strengthFor(cell.id());
            if (strength.suspended()) {
                continue;
            }
            if (strength.isNew()) {
                untouched += 1;
                continue;
            }
            strength.dueOn().ifPresent(due -> pending.add(new Pending(due, strength.rung())));
        }

        List<ProjectionDay> days = new ArrayList<>();
        int queueOfNew = untouched;

        for (LocalDate day : range.days()) {
            if (!policy.allowsDay(CivilDates.weekday(day))) {
                days.add(new ProjectionDay(day, 0, 0));
                continue;
            }

            int reviews = 0;
            for (Pending entry : pending) {
                if (entry.due.isAfter(day)) {
                    continue;
                }
                reviews += 1;
                entry.rung = (int) Math.min(entry.rung + 1L, ladder.topRung());
                entry.due = CivilDates.addDays(day, ladder.intervalAt(entry.rung));
            }

            int introductions = Math.min(policy.newPerDay(), queueOfNew);
            queueOfNew -= introductions;
            for (int index = 0; index < introductions; index += 1) {
                pending.add(new Pending(CivilDates.addDays(day, ladder.intervalAt(0)), 0));
            }

            days.add(new ProjectionDay(day, reviews, introductions));
        }

        return List.copyOf(days);
    }

    /**
     * How many days pass before the first day with nothing to review and nothing to introduce, the
     * whole span when no day is quiet.
     */
    public static int daysToClearBacklog(List<ProjectionDay> days) {
        for (int index = 0; index < days.size(); index += 1) {
            ProjectionDay day = days.get(index);
            if (day.introductions() == 0 && day.reviews() == 0) {
                return index;
            }
        }
        return days.size();
    }

    /** A cell the projection is following: when it next comes back and at which rung. */
    private static final class Pending {

        private LocalDate due;
        private int rung;

        private Pending(LocalDate due, int rung) {
            this.due = due;
            this.rung = rung;
        }
    }
}
