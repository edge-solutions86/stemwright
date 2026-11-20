package io.github.rkaushikdev.stemwright.app;

/** A handler's place on an {@link EventBus}, and the way to give it up. */
@FunctionalInterface
public interface Subscription {

    /** Stops delivery to the handler. Unsubscribing a second time does nothing. */
    void unsubscribe();
}
