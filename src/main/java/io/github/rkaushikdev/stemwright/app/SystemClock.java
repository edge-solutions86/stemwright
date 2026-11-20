package io.github.rkaushikdev.stemwright.app;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The clock {@link Clock#system} returns, reading a {@link java.time.Clock}. The study day is the
 * date in the source's zone, which for the system clock is UTC.
 *
 * @param source the clock both readings are taken from
 */
record SystemClock(java.time.Clock source) implements Clock {

    SystemClock {
        Objects.requireNonNull(source, "source");
    }

    @Override
    public Instant now() {
        return source.instant();
    }

    @Override
    public LocalDate today() {
        return LocalDate.now(source);
    }
}
