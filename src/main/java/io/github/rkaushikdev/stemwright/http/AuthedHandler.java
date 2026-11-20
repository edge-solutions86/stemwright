package io.github.rkaushikdev.stemwright.http;

/**
 * A handler that knows who is asking: a route behind {@link Auth#withActor}, or a wrapper such as
 * {@link Auth#requireRole} around one. Like a {@link Handler}, it answers on the calling thread and
 * reports failure by throwing.
 */
@FunctionalInterface
public interface AuthedHandler {

    HttpResponse handle(AuthedRequest request);
}
