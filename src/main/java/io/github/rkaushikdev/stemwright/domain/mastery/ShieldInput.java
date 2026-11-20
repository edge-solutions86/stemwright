package io.github.rkaushikdev.stemwright.domain.mastery;

import io.github.rkaushikdev.stemwright.domain.time.DayRange;
import java.util.Objects;
import java.util.Optional;

/**
 * A shield as a caller writes it, before {@link ShieldSet#of} checks the reason.
 *
 * @param window the days excused
 * @param reason why, defaulted when absent
 */
public record ShieldInput(DayRange window, Optional<String> reason) {

    public ShieldInput {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(reason, "reason");
    }

    /** A shield with no reason recorded. */
    public static ShieldInput of(DayRange window) {
        return new ShieldInput(window, Optional.empty());
    }

    public static ShieldInput of(DayRange window, String reason) {
        return new ShieldInput(window, Optional.of(reason));
    }
}
