package io.github.rkaushikdev.stemwright.http;

import io.github.rkaushikdev.stemwright.infra.json.Json;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A request as the router and every route see it, already read off the socket: the body is parsed
 * json and the query string is split into names and values, so nothing past the server adapter
 * touches raw bytes.
 *
 * <p>The maps are unmodifiable and keep the order they were built in. Header names are lower case,
 * the way the server adapter delivers them. A request is a value: a route that needs a variant, as
 * the router does when it fills in the captures, takes a copy with one of the {@code with} methods.
 *
 * @param method the request method
 * @param path the path, without the query string, still percent encoded
 * @param params the captures of the route that matched, decoded, which the router fills in
 * @param query the query string's names and values, decoded
 * @param headers the request headers, by lower case name
 * @param body the parsed body, or nothing when the request had none
 */
public record HttpRequest(
        Method method,
        String path,
        Map<String, String> params,
        Map<String, String> query,
        Map<String, String> headers,
        Optional<Json> body) {

    public HttpRequest {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(path, "path");
        params = ordered(params);
        query = ordered(query);
        headers = ordered(headers);
        Objects.requireNonNull(body, "body");
    }

    /** A request with no captures, no query, no headers and no body. */
    public static HttpRequest of(Method method, String path) {
        return new HttpRequest(method, path, Map.of(), Map.of(), Map.of(), Optional.empty());
    }

    public HttpRequest withParams(Map<String, String> params) {
        return new HttpRequest(method, path, params, query, headers, body);
    }

    public HttpRequest withQuery(Map<String, String> query) {
        return new HttpRequest(method, path, params, query, headers, body);
    }

    public HttpRequest withHeaders(Map<String, String> headers) {
        return new HttpRequest(method, path, params, query, headers, body);
    }

    public HttpRequest withBody(Json body) {
        return new HttpRequest(method, path, params, query, headers, Optional.of(body));
    }

    private static Map<String, String> ordered(Map<String, String> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
