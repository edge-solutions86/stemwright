package io.github.rkaushikdev.stemwright.http;

/**
 * Anything that answers a request: a route, the router over all of them, or a wrapper such as
 * {@link Problem#withProblems} or {@link RateLimit#withRateLimit} around another handler.
 *
 * <p>A handler answers on the thread that called it and reports failure by throwing. The router
 * lets a failure through untouched, so the outermost wrapper, normally {@link Problem#withProblems},
 * is the one place that turns it into a response.
 */
@FunctionalInterface
public interface Handler {

    HttpResponse handle(HttpRequest request);
}
