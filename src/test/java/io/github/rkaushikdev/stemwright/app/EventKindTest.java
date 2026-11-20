package io.github.rkaushikdev.stemwright.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.app.DomainEvent.LexemeAdded;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * The kinds, and the parts of the bus's contract the behaviour tests leave implicit: a handler is
 * filed once, handlers of a kind hear an event before handlers of every event, and a handler added
 * while an event is being delivered waits for the next one.
 */
class EventKindTest {

    private static final LexemeAdded ADDED =
            new LexemeAdded(Instant.parse("2025-03-03T09:00:00Z"), "lrn_teacher", "lex_aaaaaa", "latin");

    @Test
    void readsEachKindBackFromItsKey() {
        for (EventKind kind : EventKind.values()) {
            assertThat(EventKind.fromKey(kind.key())).contains(kind);
        }
        assertThat(EventKind.fromKey("lexeme.removed")).isEmpty();
        assertThat(ADDED.kind().key()).isEqualTo("lexeme.added");
    }

    @Test
    void findsTheKindOfEachRecordAndRefusesAnythingElse() {
        for (EventKind kind : EventKind.values()) {
            assertThat(EventKind.of(kind.type())).isSameAs(kind);
        }
        assertThatThrownBy(() -> EventKind.of(DomainEvent.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a kind of domain event");
        assertThatThrownBy(() -> EventBus.create().on(DomainEvent.class, event -> {}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void filesAHandlerOnceHoweverOftenItSubscribes() {
        EventBus bus = EventBus.create();
        List<DomainEvent> calls = new ArrayList<>();
        Consumer<DomainEvent> handler = calls::add;
        Subscription first = bus.on(LexemeAdded.class, handler);
        bus.on(LexemeAdded.class, handler);
        bus.publish(ADDED);
        assertThat(calls).hasSize(1);
        first.unsubscribe();
        bus.publish(ADDED);
        assertThat(calls).hasSize(1);
    }

    @Test
    void deliversToHandlersOfTheKindBeforeHandlersOfEveryEvent() {
        EventBus bus = EventBus.create();
        List<String> heard = new ArrayList<>();
        bus.onAny(event -> heard.add("any"));
        bus.on(LexemeAdded.class, event -> heard.add("kind"));
        bus.publish(ADDED);
        assertThat(heard).containsExactly("kind", "any");
    }

    @Test
    void holdsAHandlerAddedDuringDeliveryForTheNextEvent() {
        RecordingBus bus = RecordingBus.create();
        List<DomainEvent> late = new ArrayList<>();
        Consumer<DomainEvent> lateHandler = late::add;
        bus.onAny(event -> bus.onAny(lateHandler));
        bus.publish(ADDED);
        assertThat(late).isEmpty();
        bus.publish(ADDED);
        assertThat(late).hasSize(1);
        assertThat(bus.ofKind(LexemeAdded.class)).containsExactly(ADDED, ADDED);
    }
}
