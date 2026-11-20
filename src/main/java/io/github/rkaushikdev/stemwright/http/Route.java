package io.github.rkaushikdev.stemwright.http;

import java.util.Objects;

/**
 * One endpoint: a method, a path pattern and the handler that answers it.
 *
 * <p>A pattern is segments separated by slashes, and a segment beginning with a colon captures
 * whatever stands in its place, so {@code /decks/:key} matches {@code /decks/first-year} with {@code
 * key} set to {@code first-year}. There is no wildcard and no optional segment: both are ways of
 * writing two routes as one, and two routes read better.
 *
 * @param method the method the route answers
 * @param pattern the path pattern
 * @param handler what answers a matching request, which sees the captures in {@link
 *     HttpRequest#params()}
 */
public record Route(Method method, String pattern, Handler handler) {

    public Route {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(handler, "handler");
    }

    public static Route of(Method method, String pattern, Handler handler) {
        return new Route(method, pattern, handler);
    }
}
