package io.github.rkaushikdev.stemwright.http;

import io.github.rkaushikdev.stemwright.domain.course.Actor;
import io.github.rkaushikdev.stemwright.infra.json.Json;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A request together with the actor its bearer token named, as {@link Auth#withActor} hands it to
 * an {@link AuthedHandler}. The request's own parts are readable straight off it, so a route reads
 * an authenticated request the way it reads any other.
 *
 * @param request the request as it arrived
 * @param actor who is asking
 */
public record AuthedRequest(HttpRequest request, Actor actor) {

    public AuthedRequest {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(actor, "actor");
    }

    public Method method() {
        return request.method();
    }

    public String path() {
        return request.path();
    }

    public Map<String, String> params() {
        return request.params();
    }

    public Map<String, String> query() {
        return request.query();
    }

    public Map<String, String> headers() {
        return request.headers();
    }

    public Optional<Json> body() {
        return request.body();
    }
}
