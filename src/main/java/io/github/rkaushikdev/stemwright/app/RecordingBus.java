package io.github.rkaushikdev.stemwright.app;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A bus that keeps everything it saw, which is what the service tests assert on. It records an
 * event before delivering it, and otherwise behaves exactly as {@link EventBus#create()} does.
 */
public final class RecordingBus implements EventBus {

    private final EventBus inner = EventBus.create();
    private final List<DomainEvent> seen = new ArrayList<>();

    private RecordingBus() {}

    /** A recording bus that has seen nothing and has no subscribers. */
    public static RecordingBus create() {
        return new RecordingBus();
    }

    @Override
    public List<RuntimeException> publish(DomainEvent event) {
        synchronized (seen) {
            seen.add(event);
        }
        return inner.publish(event);
    }

    @Override
    public <E extends DomainEvent> Subscription on(Class<E> type, Consumer<? super E> handler) {
        return inner.on(type, handler);
    }

    @Override
    public Subscription onAny(Consumer<? super DomainEvent> handler) {
        return inner.onAny(handler);
    }

    /** Every event published so far, in the order it was published. */
    public List<DomainEvent> events() {
        synchronized (seen) {
            return List.copyOf(seen);
        }
    }

    /**
     * The events of one kind published so far, in order, as the record that kind is carried in.
     *
     * @throws IllegalArgumentException for a type that is not one of the six event records
     */
    public <E extends DomainEvent> List<E> ofKind(Class<E> type) {
        EventKind kind = EventKind.of(type);
        return events().stream()
                .filter(event -> event.kind() == kind)
                .map(type::cast)
                .toList();
    }

    /** Forgets everything published so far. Subscribers stay subscribed. */
    public void clear() {
        synchronized (seen) {
            seen.clear();
        }
    }
}
