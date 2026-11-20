package io.github.rkaushikdev.stemwright.app;

import io.github.rkaushikdev.stemwright.app.services.CourseService;
import io.github.rkaushikdev.stemwright.app.services.DeckService;
import io.github.rkaushikdev.stemwright.app.services.DrillService;
import io.github.rkaushikdev.stemwright.app.services.LexiconService;
import io.github.rkaushikdev.stemwright.app.services.ProfileService;
import io.github.rkaushikdev.stemwright.app.services.ReportService;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * One place that knows how the services fit together. Wiring is worth having in a type of its own
 * because the order is not arbitrary: the drill service is built first and handed to the services
 * that resolve a context or a deck through it, and every one of them takes the same clock and the
 * same bus.
 *
 * <p>Sharing the clock is the part that matters. Two clocks in one process is two ideas of what day
 * it is, and a queue built on one and marked against the other is a bug nobody would find from the
 * symptom.
 *
 * @param stores the stores every service reads and writes
 * @param bus the bus every service publishes to
 * @param clock the clock every service reads the time and the day from
 * @param lexicon the lexicon service
 * @param profiles the profile service
 * @param decks the deck service, resolving membership through {@code drill}
 * @param drill the drill service
 * @param courses the course service, loading contexts and decks through {@code drill}
 * @param reports the report service, resolving membership through {@code drill}
 */
public record Application(
        Stores stores,
        EventBus bus,
        Clock clock,
        LexiconService lexicon,
        ProfileService profiles,
        DeckService decks,
        DrillService drill,
        CourseService courses,
        ReportService reports) {

    public Application {
        Objects.requireNonNull(stores, "stores");
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(lexicon, "lexicon");
        Objects.requireNonNull(profiles, "profiles");
        Objects.requireNonNull(decks, "decks");
        Objects.requireNonNull(drill, "drill");
        Objects.requireNonNull(courses, "courses");
        Objects.requireNonNull(reports, "reports");
    }

    /** The services over the stores, sharing a fresh bus and the system clock. */
    public static Application create(Stores stores) {
        return create(stores, ApplicationOptions.DEFAULT);
    }

    /**
     * The services over the stores, sharing the bus and the clock the options give, and a fresh bus
     * or the system clock for whichever they leave out.
     */
    public static Application create(Stores stores, ApplicationOptions options) {
        EventBus bus = options.bus().orElseGet(EventBus::create);
        Clock clock = options.clock().orElseGet(Clock::system);

        DrillService drill = new DrillService(stores, bus, clock);

        return new Application(
                stores,
                bus,
                clock,
                new LexiconService(stores, bus, clock),
                new ProfileService(stores, bus, clock),
                new DeckService(stores, clock, drill),
                drill,
                new CourseService(stores, bus, clock, drill),
                new ReportService(stores, clock, drill));
    }

    /**
     * Subscribes the handful of listeners a running deployment wants, each of which writes one line
     * to the sink: when a cell is suspended, when a profile revision is published and when a session
     * closes. It is separate from the wiring so a test can build an application without a console
     * full of log lines, and so the list of things that happen on an event is readable in one place.
     *
     * @param write where each line goes, without a line ending
     * @return the subscription that detaches every one of the listeners at once
     */
    public Subscription attachLogging(Consumer<String> write) {
        Objects.requireNonNull(write, "write");
        List<Subscription> subscriptions = List.of(
                bus.on(
                        DomainEvent.CellSuspended.class,
                        event -> write.accept("suspended " + event.cellId() + " for " + event.learnerId() + " after "
                                + event.lapses() + " lapses")),
                bus.on(
                        DomainEvent.ProfilePublished.class,
                        event -> write.accept("published " + event.profileKey() + " revision " + event.revisionId()
                                + " from " + event.effectiveFrom())),
                bus.on(
                        DomainEvent.SessionClosed.class,
                        event -> write.accept(
                                "session " + event.sessionId() + " closed with " + event.answers() + " answers")));

        return () -> {
            for (Subscription subscription : subscriptions) {
                subscription.unsubscribe();
            }
        };
    }
}
