package io.github.rkaushikdev.stemwright.app;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The clock {@link Clock#fixed} returns. Its two components are the two readings, so the record
 * accessors are the clock.
 *
 * @param now the instant it always answers
 * @param today the study day it always answers
 */
record FixedClock(Instant now, LocalDate today) implements Clock {

    FixedClock {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(today, "today");
    }
}
