package io.github.rkaushikdev.stemwright.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.domain.course.AccountRole;
import io.github.rkaushikdev.stemwright.domain.course.Actor;
import io.github.rkaushikdev.stemwright.infra.json.Json;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * An authenticated request holds the request it carries rather than extending it, and these check
 * that every part of it is still readable straight off the wrapper.
 */
class AuthedRequestTest {

    @Test
    void readsTheRequestsPartsStraightOffIt() {
        HttpRequest request = HttpRequest.of(Method.POST, "/decks/core")
                .withParams(Map.of("key", "core"))
                .withQuery(Map.of("limit", "5"))
                .withHeaders(Map.of("authorization", "Bearer tok"))
                .withBody(Json.of("body"));
        AuthedRequest authed = new AuthedRequest(request, new Actor("lrn_teacher", AccountRole.INSTRUCTOR));

        assertThat(authed.method()).isEqualTo(Method.POST);
        assertThat(authed.path()).isEqualTo("/decks/core");
        assertThat(authed.params()).containsExactly(Map.entry("key", "core"));
        assertThat(authed.query()).containsExactly(Map.entry("limit", "5"));
        assertThat(authed.headers()).containsExactly(Map.entry("authorization", "Bearer tok"));
        assertThat(authed.body()).contains(Json.of("body"));
    }
}
