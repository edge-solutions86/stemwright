package io.github.rkaushikdev.stemwright.cli;

import io.github.rkaushikdev.stemwright.app.Clock;
import io.github.rkaushikdev.stemwright.domain.course.AccountRole;
import io.github.rkaushikdev.stemwright.domain.course.Actor;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.text.Numbers;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import java.time.LocalDate;
import java.util.Optional;

/**
 * The flags more than one command declares, and the readers that turn them into the values the
 * layer below wants.
 *
 * <p>Every command used to write its own {@code --on} handling, and they had drifted: two of them
 * fell back to the clock in two different ways, and would not have stayed the same for long. A day
 * is the argument this whole tool is organised around, so there is exactly one place that decides
 * what happens when it is left off.
 */
public final class Flags {

    /** {@code --language}: the language key. Required. */
    public static final FlagSpec LANGUAGE =
            FlagSpec.of(FlagKind.STRING, "the language key").asRequired();

    /** {@code --deck}, or {@code -d}: the deck key. Required. */
    public static final FlagSpec DECK =
            FlagSpec.of(FlagKind.STRING, "the deck key").withAlias("d").asRequired();

    /** {@code --learner}, or {@code -l}: the learner id. Required. */
    public static final FlagSpec LEARNER =
            FlagSpec.of(FlagKind.STRING, "the learner id").withAlias("l").asRequired();

    /** {@code --on}, or any flag a command names for a day: the civil day to resolve against. */
    public static final FlagSpec DAY = FlagSpec.of(FlagKind.STRING, "the civil day to resolve against");

    /** {@code --actor}: the instructor id doing the work. */
    public static final FlagSpec ACTOR = FlagSpec.of(FlagKind.STRING, "the instructor id doing the work");

    private Flags() {}

    /** {@link #dayFrom(Clock, ParsedArgs, String)} for the {@code on} flag. */
    public static LocalDate dayFrom(Clock clock, ParsedArgs args) {
        return dayFrom(clock, args, "on");
    }

    /**
     * The day the command should resolve against. Left off, it is today according to the clock the
     * application was built with, which is the clock every service below also reads. A command that
     * reached for its own idea of today would be a second clock in one process, and a queue built on
     * one and marked against the other is a bug nobody would find from the symptom.
     *
     * @throws ValidationException naming the flag when its value is not a real iso calendar date
     */
    public static LocalDate dayFrom(Clock clock, ParsedArgs args, String name) {
        return args.stringFlag(name)
                .map(value -> CivilDates.parseCivilDate(value, name))
                .orElseGet(clock::today);
    }

    /** {@link #actorFrom(ParsedArgs, String)} for the {@code actor} flag. */
    public static Actor actorFrom(ParsedArgs args) {
        return actorFrom(args, "actor");
    }

    /**
     * A local operator has already been trusted by the file system, so the cli names an instructor
     * rather than authenticating one. The id is worth taking from a flag anyway, because it is what
     * lands in the journal and {@code cli} in every entry tells a later reader nothing.
     *
     * @return an instructor with the id the flag gives, or {@code cli} when it is left off
     */
    public static Actor actorFrom(ParsedArgs args, String name) {
        return new Actor(args.stringFlag(name).orElse("cli"), AccountRole.INSTRUCTOR);
    }

    /**
     * A count flag that has to be a whole number above zero if it is given.
     *
     * @return the count, or the fallback when the flag was left off
     * @throws ValidationException naming the flag for a count that is fractional, below one, or
     *     larger than an {@code int} holds
     */
    public static int countFrom(ParsedArgs args, String name, int fallback) {
        Optional<Double> given = args.numberFlag(name);
        if (given.isEmpty()) {
            return fallback;
        }
        double value = given.get();
        if (!Numbers.isInteger(value) || value < 1) {
            throw ValidationException.field(name, "must be a whole number above zero, not " + Numbers.format(value));
        }
        if (value > Integer.MAX_VALUE) {
            throw ValidationException.field(
                    name, "must be at most " + Integer.MAX_VALUE + ", not " + Numbers.format(value));
        }
        return (int) value;
    }
}
