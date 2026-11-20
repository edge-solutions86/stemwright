package io.github.rkaushikdev.stemwright.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.course.AccountRole;
import io.github.rkaushikdev.stemwright.domain.course.Actor;
import io.github.rkaushikdev.stemwright.domain.errors.ForbiddenException;
import io.github.rkaushikdev.stemwright.http.AccountDirectory.Account;
import io.github.rkaushikdev.stemwright.infra.json.Json;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AuthTest {

    private static final AccountDirectory DIRECTORY = AccountDirectory.inMemory(Map.of(
            "tok-student", new Account("lrn_one", "learner"),
            "tok-teacher", new Account("lrn_teacher", "instructor"),
            "tok-admin", new Account("lrn_admin", "admin")));

    private static final AuthedHandler ECHO_ACTOR = incoming -> HttpResponse.json(
            200,
            Json.object()
                    .put("id", incoming.actor().id())
                    .put("role", incoming.actor().role().key())
                    .build());

    private static HttpRequest request() {
        return request(Map.of());
    }

    private static HttpRequest request(Map<String, String> headers) {
        return HttpRequest.of(Method.GET, "/decks").withHeaders(headers);
    }

    private static Map<String, String> bearer(String token) {
        return Map.of("authorization", "Bearer " + token);
    }

    @Nested
    class BearerToken {

        @Test
        void readsATokenOutOfTheHeader() {
            assertThat(Auth.bearerToken(Map.of("authorization", "Bearer tok-student")))
                    .contains("tok-student");
        }

        @Test
        void ignoresTheCaseOfTheSchemeAndOfTheHeaderName() {
            assertThat(Auth.bearerToken(Map.of("authorization", "bearer tok"))).contains("tok");
            assertThat(Auth.bearerToken(Map.of("Authorization", "BEARER tok"))).contains("tok");
        }

        @Test
        void givesNothingForAMissingOrMalformedHeader() {
            assertThat(Auth.bearerToken(Map.of())).isEmpty();
            assertThat(Auth.bearerToken(Map.of("authorization", "tok"))).isEmpty();
            assertThat(Auth.bearerToken(Map.of("authorization", "Basic abc"))).isEmpty();
            assertThat(Auth.bearerToken(Map.of("authorization", "Bearer "))).isEmpty();
        }
    }

    @Nested
    class MemoryDirectory {

        @Test
        void resolvesATokenItHolds() {
            assertThat(DIRECTORY.resolve("tok-teacher")).contains(new Actor("lrn_teacher", AccountRole.INSTRUCTOR));
        }

        @Test
        void givesNothingForATokenItDoesNot() {
            assertThat(DIRECTORY.resolve("tok-nowhere")).isEmpty();
        }

        @Test
        void givesNothingForAnAccountWithARoleItDoesNotRecognise() {
            AccountDirectory broken = AccountDirectory.inMemory(Map.of("tok", new Account("lrn_one", "tutor")));
            assertThat(broken.resolve("tok")).isEmpty();
        }
    }

    @Nested
    class WithActor {

        private final Handler handler = Auth.withActor(DIRECTORY, ECHO_ACTOR);

        @Test
        void attachesTheActorTheTokenNames() {
            HttpResponse response = handler.handle(request(bearer("tok-student")));
            assertThat(response.body())
                    .isEqualTo(Json.object()
                            .put("id", "lrn_one")
                            .put("role", "learner")
                            .build());
        }

        @Test
        void refusesARequestWithNoToken() {
            assertThatThrownBy(() -> handler.handle(request())).isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> handler.handle(request())).hasMessageContaining("no bearer token was presented");
        }

        @Test
        void refusesATokenNobodyHolds() {
            assertThatThrownBy(() -> handler.handle(request(bearer("tok-nowhere"))))
                    .hasMessageContaining("does not name an account");
        }

        @Test
        void leavesTheRestOfTheRequestAlone() {
            AtomicReference<AuthedRequest> seen = new AtomicReference<>();
            Handler capture = Auth.withActor(DIRECTORY, incoming -> {
                seen.set(incoming);
                return HttpResponse.json(200, Json.NULL);
            });
            capture.handle(HttpRequest.of(Method.GET, "/reports").withHeaders(bearer("tok-admin")));
            assertThat(seen.get().path()).isEqualTo("/reports");
        }
    }

    @Nested
    class RequireRole {

        @Test
        void letsAPermittedRoleThrough() {
            Handler handler = Auth.withActor(
                    DIRECTORY, Auth.requireRole(List.of(AccountRole.INSTRUCTOR, AccountRole.ADMIN), ECHO_ACTOR));
            assertThat(handler.handle(request(bearer("tok-teacher"))).status()).isEqualTo(200);
        }

        @Test
        void refusesARoleThatIsNotListed() {
            Handler handler = Auth.withActor(DIRECTORY, Auth.requireRole(List.of(AccountRole.INSTRUCTOR), ECHO_ACTOR));
            assertThatThrownBy(() -> handler.handle(request(bearer("tok-student"))))
                    .hasMessageContaining("lrn_one may not reach");
        }

        @Test
        void refusesEverybodyWhenTheListIsEmpty() {
            Handler handler = Auth.withActor(DIRECTORY, Auth.requireRole(List.of(), ECHO_ACTOR));
            assertThatThrownBy(() -> handler.handle(request(bearer("tok-admin"))))
                    .isInstanceOf(ForbiddenException.class);
        }
    }
}
