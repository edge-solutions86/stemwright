package io.github.rkaushikdev.stemwright.http;

import io.github.rkaushikdev.stemwright.domain.text.Text;
import io.github.rkaushikdev.stemwright.infra.json.Json;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A router small enough to read in one sitting. It exists because the http surface here is a dozen
 * routes over a domain that does all the thinking, and a framework would be more code than the thing
 * it wraps.
 *
 * <p>Routes are tried in order, and the first match wins. A path that matches a pattern under a
 * different method produces 405 rather than 404, because the two mean different things to a client
 * and collapsing them hides a typo in a verb behind a missing endpoint. Patterns are described on
 * {@link Route}.
 *
 * <p>A router holds nothing that changes, so one instance serves every request thread at once.
 */
public final class Router implements Handler {

    private static final Handler DEFAULT_NOT_FOUND = request -> HttpResponse.json(
            404,
            Json.object()
                    .put("code", "NOT_FOUND")
                    .put("message", "no route for " + request.method() + " " + request.path())
                    .build());

    private static final Handler DEFAULT_METHOD_NOT_ALLOWED = request -> HttpResponse.json(
            405,
            Json.object()
                    .put("code", "METHOD_NOT_ALLOWED")
                    .put("message", request.method() + " is not allowed here")
                    .build());

    private final List<Route> routes;
    private final Handler notFound;
    private final Handler methodNotAllowed;

    private Router(List<Route> routes, Options options) {
        this.routes = List.copyOf(routes);
        this.notFound = options.notFound().orElse(DEFAULT_NOT_FOUND);
        this.methodNotAllowed = options.methodNotAllowed().orElse(DEFAULT_METHOD_NOT_ALLOWED);
    }

    /** A router over the routes, answering 404 and 405 with a {@code code} and a {@code message}. */
    public static Router of(List<Route> routes) {
        return new Router(routes, Options.DEFAULT);
    }

    public static Router of(List<Route> routes, Options options) {
        return new Router(routes, options);
    }

    /**
     * Hands the request, with the captures of the first route whose pattern and method both match,
     * to that route. With no such route it answers through the method not allowed handler when some
     * route's pattern matched under another method, and through the not found handler otherwise. A
     * failure a route throws passes through untouched.
     */
    @Override
    public HttpResponse handle(HttpRequest request) {
        boolean pathMatched = false;
        for (Route candidate : routes) {
            Optional<Map<String, String>> params = matchPath(candidate.pattern(), request.path());
            if (params.isEmpty()) {
                continue;
            }
            pathMatched = true;
            if (candidate.method() != request.method()) {
                continue;
            }
            return candidate.handler().handle(request.withParams(params.get()));
        }
        return pathMatched ? methodNotAllowed.handle(request) : notFound.handle(request);
    }

    /** The routes, in the order they are tried. */
    public List<Route> routes() {
        return routes;
    }

    /** The segments of a path, with the empty ones dropped however many slashes there are. */
    public static List<String> splitPath(String path) {
        return Text.split(path, "/").stream()
                .filter(segment -> !segment.isEmpty())
                .toList();
    }

    /**
     * Matches a path against a pattern and returns the captures, or nothing. A capture is decoded,
     * because a lemma in a path arrives percent encoded and every caller would otherwise decode it
     * again slightly differently.
     *
     * @throws IllegalArgumentException when a captured segment holds a broken percent escape
     */
    public static Optional<Map<String, String>> matchPath(String pattern, String path) {
        List<String> wanted = splitPath(pattern);
        List<String> given = splitPath(path);
        if (wanted.size() != given.size()) {
            return Optional.empty();
        }

        Map<String, String> params = new LinkedHashMap<>();
        for (int index = 0; index < wanted.size(); index += 1) {
            String segment = wanted.get(index);
            String actual = given.get(index);
            if (segment.startsWith(":")) {
                params.put(segment.substring(1), UriComponent.decode(actual));
                continue;
            }
            if (!segment.equals(actual)) {
                return Optional.empty();
            }
        }
        return Optional.of(Collections.unmodifiableMap(params));
    }

    /**
     * Reads a query string, with or without its leading question mark, into names and values. A name
     * with no equals sign has the empty string as its value, a name given twice keeps its last value,
     * and both halves are percent decoded, a plus sign in the value being read as a space first.
     *
     * @throws IllegalArgumentException when a name or value holds a broken percent escape
     */
    public static Map<String, String> parseQuery(String search) {
        Map<String, String> query = new LinkedHashMap<>();
        String trimmed = search.startsWith("?") ? search.substring(1) : search;
        if (trimmed.isEmpty()) {
            return Collections.unmodifiableMap(query);
        }
        for (String part : Text.split(trimmed, "&")) {
            if (part.isEmpty()) {
                continue;
            }
            int at = part.indexOf('=');
            String key = at < 0 ? part : part.substring(0, at);
            String value = at < 0 ? "" : part.substring(at + 1);
            query.put(UriComponent.decode(key), UriComponent.decode(value.replace('+', ' ')));
        }
        return Collections.unmodifiableMap(query);
    }

    /** Every method some route accepts for the path, in route order. */
    public static List<Method> methodsFor(List<Route> routes, String path) {
        return routes.stream()
                .filter(candidate -> matchPath(candidate.pattern(), path).isPresent())
                .map(Route::method)
                .toList();
    }

    /**
     * What a router answers when no route takes a request. Either handler left empty keeps the
     * default, which answers 404 or 405 with a {@code code} and a {@code message}.
     *
     * @param notFound answers a path no pattern matches
     * @param methodNotAllowed answers a path some pattern matches under another method
     */
    public record Options(Optional<Handler> notFound, Optional<Handler> methodNotAllowed) {

        /** Both defaults. */
        public static final Options DEFAULT = new Options(Optional.empty(), Optional.empty());

        public Options {
            Objects.requireNonNull(notFound, "notFound");
            Objects.requireNonNull(methodNotAllowed, "methodNotAllowed");
        }

        public Options withNotFound(Handler handler) {
            return new Options(Optional.of(handler), methodNotAllowed);
        }

        public Options withMethodNotAllowed(Handler handler) {
            return new Options(notFound, Optional.of(handler));
        }
    }
}
