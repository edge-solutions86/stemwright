package io.github.rkaushikdev.stemwright.app;

import java.util.Objects;
import java.util.Optional;

/**
 * What a caller may hand {@link Application#create} rather than letting it build its own: the bus
 * every service publishes to and the clock every service reads the day from.
 *
 * @param bus the bus the services share, a fresh one when absent
 * @param clock the clock the services share, {@link Clock#system()} when absent
 */
public record ApplicationOptions(Optional<EventBus> bus, Optional<Clock> clock) {

    /** Neither given: a fresh bus and the system clock. */
    public static final ApplicationOptions DEFAULT = new ApplicationOptions(Optional.empty(), Optional.empty());

    public ApplicationOptions {
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(clock, "clock");
    }

    /** Both given, which is how a test or a simulation wires an application it can watch and steer. */
    public static ApplicationOptions of(EventBus bus, Clock clock) {
        return new ApplicationOptions(Optional.of(bus), Optional.of(clock));
    }

    /** These options with the bus in place of any given before. */
    public ApplicationOptions withBus(EventBus next) {
        return new ApplicationOptions(Optional.of(next), clock);
    }

    /** These options with the clock in place of any given before. */
    public ApplicationOptions withClock(Clock next) {
        return new ApplicationOptions(bus, Optional.of(next));
    }
}
