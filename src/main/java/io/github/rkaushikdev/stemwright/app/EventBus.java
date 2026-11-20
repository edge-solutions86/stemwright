package io.github.rkaushikdev.stemwright.app;

import java.util.List;
import java.util.function.Consumer;

/**
 * Where the application services announce what they have done, and where the audit log, the
 * logging and the tests listen.
 *
 * <p>Publication is best effort. Every handler is called in turn on the publishing thread, and a
 * handler that throws neither stops the handlers after it nor reaches the publisher: its failure is
 * collected and handed back from {@link #publish}, because the write the event reports has already
 * landed and a broken subscriber must not be able to undo it.
 *
 * <p>A handler is filed once however many times it subscribes, so one {@link
 * Subscription#unsubscribe()} removes it.
 */
public interface EventBus {

    /**
     * Hands the event to every handler of its kind, in the order they subscribed, and then to every
     * handler of all events, likewise in order. The handlers are those subscribed when publishing
     * began; one that subscribes or unsubscribes from inside a handler changes the next publication,
     * not this one.
     *
     * <p>An unchecked exception a handler throws is collected as it is. A checked one can only
     * arrive thrown past the compiler, from another JVM language or a sneaky throw, and is collected
     * wrapped in a {@link RuntimeException} carrying its message. An {@link Error} is not caught.
     *
     * @return what the handlers threw, in the order they threw it, empty when none did
     */
    List<RuntimeException> publish(DomainEvent event);

    /**
     * Subscribes a handler to the events of one kind, named by the record that carries it, such as
     * {@code DomainEvent.CellSuspended.class}.
     *
     * @throws IllegalArgumentException for a type that is not one of the six event records
     */
    <E extends DomainEvent> Subscription on(Class<E> type, Consumer<? super E> handler);

    /** Subscribes a handler to every event, whatever its kind. */
    Subscription onAny(Consumer<? super DomainEvent> handler);

    /** A bus with no subscribers, safe to publish to and subscribe on from several threads at once. */
    static EventBus create() {
        return new LocalEventBus();
    }
}
