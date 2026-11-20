package io.github.rkaushikdev.stemwright.domain.mastery;

import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.paradigm.Cell;
import io.github.rkaushikdev.stemwright.domain.text.Numbers;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * When a learner answers a cell, its syncretic twins have been answered too, in every sense except
 * that nobody asked them. A drill that ignores this asks the same string three times over and
 * reports three separate weaknesses.
 *
 * <p>Credit is discounted rather than full. A learner who typed {@code puellae} for the genitive
 * singular has shown they know that form; they have not shown they know it is also the nominative
 * plural. So the twin moves, but less far, and a twin that has never been introduced does not move
 * at all, because there is nothing yet to move.
 *
 * <p>Build one through {@link #builder()}, which starts from {@link #DEFAULT} and checks the result;
 * the canonical constructor only copies what it is given.
 *
 * @param share the fraction of a full climb a twin receives, nought to one
 * @param allowClimb whether a twin may climb a rung, or only have its due date pushed out
 * @param introduces whether a twin the learner has never met is introduced by the credit
 */
public record CreditRule(double share, boolean allowClimb, boolean introduces) {

    /** Half the interval, a climb allowed, and no twin introduced. */
    public static final CreditRule DEFAULT = new CreditRule(0.5, true, false);

    /** The rule under which nothing passes to a twin. */
    public static final CreditRule NONE = new CreditRule(0, false, false);

    private static final int PERCENT = 100;

    /** A builder holding the default rule, for a caller that wants to change only some of it. */
    public static Builder builder() {
        return new Builder(DEFAULT);
    }

    /**
     * Works out what one twin gets. It never pulls a due date forward: a twin already scheduled
     * further out than the credit would place it keeps the later date, because credit is a reward
     * and moving a review closer is not one.
     *
     * <p>The strength that comes back is this very instance whenever the twin did not move: a
     * suspended twin, a share of nought, a new twin the rule does not introduce, and a twin that
     * neither climbs nor needs a later date.
     */
    public Credited creditFor(CellStrength strength, Ladder ladder, LocalDate on) {
        CreditOutcome unchanged =
                new CreditOutcome(strength.cellId(), false, false, strength.dueOn(), strength.dueOn());

        if (strength.suspended() || share == 0) {
            return new Credited(strength, unchanged);
        }
        if (strength.isNew() && !introduces) {
            return new Credited(strength, unchanged);
        }

        int rung = allowClimb && !strength.isNew() ? ladder.climb(strength.rung()) : strength.rung();
        int full = ladder.intervalAt(rung);
        long shared = Math.max(1, Numbers.round(full * share));
        LocalDate candidate = CivilDates.addDays(on, shared);
        Optional<LocalDate> existing = strength.dueOn().filter(due -> !due.isBefore(candidate));

        if (existing.isPresent() && rung == strength.rung()) {
            return new Credited(strength, unchanged);
        }

        LocalDate dueOn = existing.orElse(candidate);
        return new Credited(
                strength.withSchedule(rung, dueOn),
                new CreditOutcome(
                        strength.cellId(), true, rung != strength.rung(), strength.dueOn(), Optional.of(dueOn)));
    }

    /**
     * Applies credit to every twin of the answered cell, reporting one outcome for each twin in the
     * order given, moved or not. The twin list is resolved by the caller, because working it out
     * needs the profile revision in force and this layer is meant to stay free of that dependency.
     * The ledger handed in is left as it was.
     */
    public CreditReport propagate(MasteryLedger ledger, Ladder ladder, List<String> twinCellIds, LocalDate on) {
        List<CellStrength> changed = new ArrayList<>();
        List<CreditOutcome> outcomes = new ArrayList<>();

        for (String cellId : twinCellIds) {
            Credited credited = creditFor(ledger.strengthFor(cellId), ladder, on);
            outcomes.add(credited.outcome());
            if (credited.outcome().moved()) {
                changed.add(credited.strength());
            }
        }

        return new CreditReport(ledger.withStrengths(changed), outcomes);
    }

    /**
     * What the learner is told about the credit an answer passed on, such as {@code 2 twins of
     * case=gen;number=sg took 50 percent of the interval}.
     *
     * @param answered the cell that was answered
     * @param moved how many of its twins moved
     */
    public String describe(Cell answered, int moved) {
        if (share == 0) {
            return "no credit passes from " + answered.bundle();
        }
        long percent = Numbers.round(share * PERCENT);
        return moved + " twins of " + answered.bundle() + " took " + percent + " percent of the interval";
    }

    /**
     * What a rule gave one twin.
     *
     * @param strength the twin's strength afterwards
     * @param outcome whether the twin moved and where to
     */
    public record Credited(CellStrength strength, CreditOutcome outcome) {

        public Credited {
            Objects.requireNonNull(strength, "strength");
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    /** Builds a {@link CreditRule} from another one, checking the result when it is built. */
    public static final class Builder {

        private double share;
        private boolean allowClimb;
        private boolean introduces;

        private Builder(CreditRule from) {
            share = from.share;
            allowClimb = from.allowClimb;
            introduces = from.introduces;
        }

        public Builder share(double value) {
            share = value;
            return this;
        }

        public Builder allowClimb(boolean value) {
            allowClimb = value;
            return this;
        }

        public Builder introduces(boolean value) {
            introduces = value;
            return this;
        }

        /**
         * Checks the rule and builds it.
         *
         * @throws ValidationException on the {@code share} field when it is not a number from nought
         *     to one
         */
        public CreditRule build() {
            if (!Double.isFinite(share) || share < 0 || share > 1) {
                throw ValidationException.field("share", "must be between nought and one: " + printed(share));
            }
            return new CreditRule(share, allowClimb, introduces);
        }

        /**
         * A number as the original printed it. {@link Numbers#format(double)} has no text for a
         * value that is not finite, and the spellings the original used for those, {@code NaN} and
         * {@code Infinity}, are the ones {@link Double#toString(double)} gives.
         */
        private static String printed(double value) {
            return Double.isFinite(value) ? Numbers.format(value) : Double.toString(value);
        }
    }
}
