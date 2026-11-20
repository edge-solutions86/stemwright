package io.github.rkaushikdev.stemwright.app;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

/**
 * The bus {@link EventBus#create} returns, delivering in process on the publishing thread.
 *
 * <p>Each kind has its own handler set from the start, so the map is never written after
 * construction; the sets are copy on write, which suits handlers that subscribe once at startup and
 * are read on every publication, and keeps them in subscription order.
 */
final class LocalEventBus implements EventBus {

    private final Map<EventKind, Set<Consumer<? super DomainEvent>>> byKind = new EnumMap<>(EventKind.class);
    private final Set<Consumer<? super DomainEvent>> anyHandlers = new CopyOnWriteArraySet<>();

    LocalEventBus() {
        for (EventKind kind : EventKind.values()) {
            byKind.put(kind, new CopyOnWriteArraySet<>());
        }
    }

    @Override
    public List<RuntimeException> publish(DomainEvent event) {
        List<Consumer<? super DomainEvent>> handlers = new ArrayList<>(byKind.get(event.kind()));
        handlers.addAll(anyHandlers);
        List<RuntimeException> failures = new ArrayList<>();
        for (Consumer<? super DomainEvent> handler : handlers) {
            try {
                handler.accept(event);
            } catch (RuntimeException failure) {
                failures.add(failure);
            } catch (Exception failure) {
                failures.add(new RuntimeException(failure.getMessage(), failure));
            }
        }
        return List.copyOf(failures);
    }

    @Override
    public <E extends DomainEvent> Subscription on(Class<E> type, Consumer<? super E> handler) {
        return subscribe(byKind.get(EventKind.of(type)), filedUnderItsKind(handler));
    }

    @Override
    public Subscription onAny(Consumer<? super DomainEvent> handler) {
        return subscribe(anyHandlers, handler);
    }

    private static Subscription subscribe(
            Set<Consumer<? super DomainEvent>> handlers, Consumer<? super DomainEvent> handler) {
        handlers.add(handler);
        return () -> handlers.remove(handler);
    }

    /**
     * The handler as the set of its kind holds it. It is filed under the kind of {@code E} and only
     * ever handed events of that kind, all of which are {@code E}, so the widened type is never used
     * to pass it anything else. The handler itself is kept, not a wrapper, so subscribing it twice
     * files it once.
     */
    @SuppressWarnings("unchecked")
    private static <E extends DomainEvent> Consumer<? super DomainEvent> filedUnderItsKind(
            Consumer<? super E> handler) {
        return (Consumer<? super DomainEvent>) handler;
    }
}
