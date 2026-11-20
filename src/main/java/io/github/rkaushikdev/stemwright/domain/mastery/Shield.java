package io.github.rkaushikdev.stemwright.domain.mastery;

import io.github.rkaushikdev.stemwright.domain.time.DayRange;
import java.util.Objects;

/**
 * One stretch of days a learner is excused from, and why.
 *
 * @param window the days excused, both ends included
 * @param reason why, as it was recorded
 */
public record Shield(DayRange window, String reason) {

    public Shield {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(reason, "reason");
    }
}
