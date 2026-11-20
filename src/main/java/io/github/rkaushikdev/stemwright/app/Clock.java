package io.github.rkaushikdev.stemwright.app;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Where the services read the time and the study day. The domain has no clock of its own, so this
 * is the one place an answer, a queue or a report learns what day it is, and every service in a
 * process shares one: two clocks in one process is two ideas of what day it is.
 *
 * <p>The instant and the day are separate readings rather than one derived from the other. The
 * study day is a civil date, and which one it is depends on where the learner is, not on the
 * instant alone; the fixed and steppable clocks are handed both.
 */
public interface Clock {

    /** The present instant. */
    Instant now();

    /** The present study day. */
    LocalDate today();

    /**
     * A clock that never moves, which every test uses and no production wiring does.
     *
     * @param instant what {@link #now()} always answers
     * @param day what {@link #today()} always answers
     */
    static Clock fixed(Instant instant, LocalDate day) {
        return new FixedClock(instant, day);
    }

    /** The system clock: the present instant, and the study day as the date in UTC. */
    static Clock system() {
        return new SystemClock(java.time.Clock.systemUTC());
    }

    /**
     * A clock that steps forward only when told to. Useful for a simulation and for a test that
     * wants several days to pass without waiting for them.
     *
     * @param start what {@link #now()} answers until the clock is set
     * @param day what {@link #today()} answers until the clock is set
     */
    static SteppableClock steppable(Instant start, LocalDate day) {
        return new SteppableClock(start, day);
    }
}
