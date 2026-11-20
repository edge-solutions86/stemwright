package io.github.rkaushikdev.stemwright.http;

import io.github.rkaushikdev.stemwright.infra.json.Json;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * What a handler answers: a status, a json body, and any headers beyond the content type, which the
 * server adapter always sets itself.
 *
 * @param status the http status
 * @param body the body, which the adapter writes as compact json, or leaves out for a 204
 * @param headers extra response headers, unmodifiable and in the order given, empty when there are
 *     none
 */
public record HttpResponse(int status, Json body, Map<String, String> headers) {

    public HttpResponse {
        Objects.requireNonNull(body, "body");
        headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }

    /** A json response with no extra headers. */
    public static HttpResponse json(int status, Json body) {
        return new HttpResponse(status, body, Map.of());
    }

    /** A 204 with a {@code null} body, which the adapter does not write. */
    public static HttpResponse noContent() {
        return json(204, Json.NULL);
    }

    public HttpResponse withHeaders(Map<String, String> headers) {
        return new HttpResponse(status, body, headers);
    }
}
