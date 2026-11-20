package io.github.rkaushikdev.stemwright.domain.mastery;

import io.github.rkaushikdev.stemwright.domain.text.Normalise;
import io.github.rkaushikdev.stemwright.domain.time.DayRange;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A shield is a stretch of days the learner is excused from. Illness, a reading week, a field trip.
 * Shields do two things and they are separate: reviews due inside one are pushed out to the far
 * side, and a streak counted across one steps over it rather than breaking.
 *
 * <p>The second is the part that is easy to get wrong. Treating a shielded day as an ordinary missed
 * day punishes the learner for something the department signed off, and treating it as a day they
 * answered credits them with work they did not do. Stepping over is neither.
 *
 * @param shields the shields as they were given, each with its reason
 * @param windows the same days merged into the smallest sorted set of ranges that covers them
 */
public record ShieldSet(List<Shield> shields, List<DayRange> windows) {

    /** A set that shields nothing. */
    public static final ShieldSet NONE = new ShieldSet(List.of(), List.of());

    private static final String NO_REASON = "no reason recorded";
    private static final int REASON_LIMIT = 200;
    private static final int STREAK_LIMIT = 400;

    public ShieldSet {
        shields = List.copyOf(shields);
        windows = List.copyOf(windows);
    }

    /**
     * Checks each reason and merges the windows.
     *
     * @throws io.github.rkaushikdev.stemwright.domain.errors.ValidationException for a blank reason or
     *     one longer than two hundred characters
     */
    public static ShieldSet of(List<ShieldInput> inputs) {
        List<Shield> shields = new ArrayList<>(inputs.size());
        for (int index = 0; index < inputs.size(); index += 1) {
            ShieldInput input = inputs.get(index);
            String path = "shields." + index + ".reason";
            String reason = input.reason()
                    .map(text -> Normalise.requireText(path, text, REASON_LIMIT))
                    .orElse(NO_REASON);
            shields.add(new Shield(input.window(), reason));
        }
        return new ShieldSet(
                shields, DayRange.normalise(shields.stream().map(Shield::window).toList()));
    }

    public boolean isShielded(LocalDate day) {
        return DayRange.coveredBy(windows, day);
    }

    /** The first shield, in the order given, whose window holds the day. */
    public Optional<Shield> covering(LocalDate day) {
        return shields.stream().filter(shield -> shield.window().contains(day)).findFirst();
    }

    /** The first day at or after this one that is not shielded. */
    public LocalDate firstOpenDay(LocalDate day) {
        LocalDate candidate = day;
        for (DayRange window : windows) {
            if (window.contains(candidate)) {
                candidate = window.to().plusDays(1);
            }
        }
        return candidate;
    }

    /** How many days of the range fall inside a shield. */
    public int shieldedDaysIn(DayRange range) {
        return windows.stream()
                .map(range::intersect)
                .flatMap(Optional::stream)
                .mapToInt(DayRange::length)
                .sum();
    }

    /**
     * Pushes every review due inside a shield out to the first open day. Cells due before the shield
     * are left alone: a learner who was already behind when the shield started does not get the
     * backlog forgiven.
     */
    public Applied applyTo(MasteryLedger ledger, List<String> cellIds) {
        List<CellStrength> changed = new ArrayList<>();
        List<ShieldOutcome> outcomes = new ArrayList<>();

        for (String cellId : cellIds) {
            CellStrength strength = ledger.strengthFor(cellId);
            if (strength.suspended()
                    || strength.dueOn().filter(this::isShielded).isEmpty()) {
                continue;
            }
            LocalDate due = strength.dueOn().orElseThrow();
            LocalDate open = firstOpenDay(due);
            changed.add(strength.deferTo(open));
            outcomes.add(new ShieldOutcome(cellId, due, open));
        }

        return new Applied(ledger.withStrengths(changed), outcomes);
    }

    /** {@link #streakThrough(Set, LocalDate, int)} looking back at most four hundred days. */
    public int streakThrough(Set<LocalDate> answeredOn, LocalDate on) {
        return streakThrough(answeredOn, on, STREAK_LIMIT);
    }

    /**
     * Counts a run of days ending at {@code on}, treating a shielded day as neither a break nor a day
     * answered. A day the learner answered on continues the run, a shielded day is stepped over, and
     * anything else ends it.
     *
     * @param limit how many days to look back before giving up and reporting the run so far
     */
    public int streakThrough(Set<LocalDate> answeredOn, LocalDate on, int limit) {
        int streak = 0;
        LocalDate cursor = on;
        for (int step = 0; step < limit; step += 1) {
            if (answeredOn.contains(cursor)) {
                streak += 1;
            } else if (!isShielded(cursor)) {
                return streak;
            }
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    public String describe() {
        if (shields.isEmpty()) {
            return "no shielded days";
        }
        return shields.stream()
                .map(shield -> shield.window().from() + " to " + shield.window().to() + " for " + shield.reason())
                .collect(Collectors.joining(", "));
    }

    /**
     * What a shield set did to a ledger.
     *
     * @param ledger the ledger afterwards
     * @param outcomes one outcome for each review that moved, in the order the cells were asked about
     */
    public record Applied(MasteryLedger ledger, List<ShieldOutcome> outcomes) {

        public Applied {
            Objects.requireNonNull(ledger, "ledger");
            outcomes = List.copyOf(outcomes);
        }
    }
}
