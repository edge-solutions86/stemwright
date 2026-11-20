package io.github.rkaushikdev.stemwright.app;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A clock that answers the same instant and day until it is {@link #set}. A simulation of a term
 * sets it once per simulated day.
 *
 * <p>It may be read from any thread; {@link #set} replaces the instant and the day in a single
 * write.
 */
public final class SteppableClock implements Clock {

    private volatile Reading current;

    SteppableClock(Instant start, LocalDate day) {
        current = new Reading(start, day);
    }

    @Override
    public Instant now() {
        return current.instant();
    }

    @Override
    public LocalDate today() {
        return current.day();
    }

    /** Moves the clock to the instant and the study day, which it then answers until set again. */
    public void set(Instant next, LocalDate nextDay) {
        current = new Reading(next, nextDay);
    }

    private record Reading(Instant instant, LocalDate day) {

        Reading {
            Objects.requireNonNull(instant, "instant");
            Objects.requireNonNull(day, "day");
        }
    }
}
