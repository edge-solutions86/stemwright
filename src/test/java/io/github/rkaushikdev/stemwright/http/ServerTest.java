package io.github.rkaushikdev.stemwright.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.errors.ForbiddenException;
import io.github.rkaushikdev.stemwright.infra.json.Json;
import io.github.rkaushikdev.stemwright.infra.json.JsonObject;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ServerTest {

    private static final int READ_TIMEOUT_MS = 10_000;

    private static final List<Route> ROUTES = List.of(
            Route.of(
                    Method.GET,
                    "/ping",
                    request -> HttpResponse.json(
                            200, Json.object().put("pong", true).build())),
            Route.of(
                    Method.GET,
                    "/echo/:name",
                    request -> HttpResponse.json(
                            200,
                            Json.object()
                                    .put("name", textOrNull(request.params().get("name")))
                                    .put("query", objectOf(request.query()))
                                    .build())),
            Route.of(
                    Method.POST,
                    "/echo",
                    request -> HttpResponse.json(
                            200,
                            Json.object()
                                    .put("body", request.body().orElse(Json.NULL))
                                    .build())),
            Route.of(Method.DELETE, "/gone", request -> new HttpResponse(204, Json.NULL, Map.of())),
            Route.of(Method.GET, "/forbidden", request -> {
                throw new ForbiddenException("no");
            }),
            Route.of(Method.GET, "/broken", request -> {
                throw new IllegalStateException("boom");
            }));

    private final List<Server> servers = new ArrayList<>();
    private HttpClient client;

    private static Json textOrNull(String value) {
        return value == null ? Json.NULL : Json.of(value);
    }

    private static JsonObject objectOf(Map<String, String> values) {
        JsonObject.Builder object = Json.object();
        values.forEach(object::put);
        return object.build();
    }

    private String listen(Server server) {
        servers.add(server);
        server.listen("127.0.0.1", 0);
        return "http://127.0.0.1:" + server.port();
    }

    @AfterEach
    void stopEverythingStarted() {
        while (!servers.isEmpty()) {
            servers.remove(servers.size() - 1).close();
        }
        if (client != null) {
            client.close();
        }
    }

    /** What a client received: the status, the content type it was told, and the body as text. */
    private record Received(int status, Optional<String> contentType, String text) {

        Json json() {
            return Json.parse(text);
        }
    }

    private Received fetch(String url) throws IOException, InterruptedException {
        return fetch("GET", url, Optional.empty());
    }

    private Received fetch(String method, String url, Optional<String> body) throws IOException, InterruptedException {
        if (client == null) {
            client =
                    HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        }
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(URI.create(url))
                .method(method, body.map(BodyPublishers::ofString).orElseGet(BodyPublishers::noBody))
                .build();
        java.net.http.HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        return new Received(response.statusCode(), response.headers().firstValue("content-type"), response.body());
    }

    /**
     * Writes one request on a socket of its own and reads the status line of what comes back, or
     * nothing when the server ended the connection without answering.
     */
    private static Optional<Integer> rawStatus(int port, String request) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(READ_TIMEOUT_MS);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            String answer = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!answer.startsWith("HTTP/1.1 ")) {
                return Optional.empty();
            }
            return Optional.of(Integer.parseInt(answer.substring("HTTP/1.1 ".length(), "HTTP/1.1 ".length() + 3)));
        } catch (SocketException reset) {
            return Optional.empty();
        }
    }

    @Nested
    class IsMethod {

        @Test
        void acceptsTheFourMethodsAndNothingElse() {
            assertThat(Server.isMethod("GET")).isTrue();
            assertThat(Server.isMethod("PATCH")).isFalse();
        }
    }

    @Nested
    class ParseBody {

        @Test
        void readsAnEmptyBodyAsNothing() {
            assertThat(Server.parseBody("")).isEmpty();
            assertThat(Server.parseBody("   ")).isEmpty();
        }

        @Test
        void parsesJson() {
            assertThat(Server.parseBody("{\"a\":1}"))
                    .contains(Json.object().put("a", 1).build());
        }

        @Test
        void refusesTextThatIsNotJson() {
            assertThatThrownBy(() -> Server.parseBody("{")).hasMessageContaining("not valid json");
        }
    }

    @Nested
    class TheServer {

        private String start() {
            return listen(Server.create(Router.of(ROUTES)));
        }

        private String start(List<Exception> seen) {
            return listen(Server.create(Router.of(ROUTES), Server.Options.DEFAULT.withOnError(seen::add)));
        }

        @Test
        void answersAGet() throws Exception {
            String base = start();
            Received response = fetch(base + "/ping");
            assertThat(response.status()).isEqualTo(200);
            assertThat(response.contentType())
                    .hasValueSatisfying(type -> assertThat(type).contains("application/json"));
            assertThat(response.json())
                    .isEqualTo(Json.object().put("pong", true).build());
        }

        @Test
        void passesThePathCapturesAndTheQueryThrough() throws Exception {
            String base = start();
            Received response = fetch(base + "/echo/puella%20mea?on=2025-03-03");
            assertThat(response.json())
                    .isEqualTo(Json.object()
                            .put("name", "puella mea")
                            .put("query", Json.object().put("on", "2025-03-03").build())
                            .build());
        }

        @Test
        void readsAJsonBody() throws Exception {
            String base = start();
            Received response = fetch(
                    "POST",
                    base + "/echo",
                    Optional.of(Json.stringify(Json.object().put("a", 1).build())));
            assertThat(response.json())
                    .isEqualTo(Json.object()
                            .put("body", Json.object().put("a", 1).build())
                            .build());
        }

        @Test
        void answersAnEmptyBodyFor204() throws Exception {
            String base = start();
            Received response = fetch("DELETE", base + "/gone", Optional.empty());
            assertThat(response.status()).isEqualTo(204);
            assertThat(response.text()).isEqualTo("");
        }

        @Test
        void answers404ForAPathNothingMatches() throws Exception {
            String base = start();
            Received response = fetch(base + "/nowhere");
            assertThat(response.status()).isEqualTo(404);
            assertThat(response.json().get("code")).contains(Json.of("NOT_FOUND"));
        }

        @Test
        void mapsADomainFailureToItsStatus() throws Exception {
            String base = start();
            Received response = fetch(base + "/forbidden");
            assertThat(response.status()).isEqualTo(403);
            assertThat(response.json().get("code")).contains(Json.of("FORBIDDEN"));
        }

        @Test
        void hidesAnUnexpectedFailureBehindABare500() throws Exception {
            List<Exception> seen = new CopyOnWriteArrayList<>();
            String base = start(seen);
            Received response = fetch(base + "/broken");
            assertThat(response.status()).isEqualTo(500);
            assertThat(response.json())
                    .isEqualTo(Json.object()
                            .put("code", "INTERNAL")
                            .put("message", "the request could not be completed")
                            .build());
            assertThat(seen).hasSize(1);
        }

        @Test
        void refusesABodyThatIsNotJson() throws Exception {
            String base = start();
            Received response = fetch("POST", base + "/echo", Optional.of("{"));
            assertThat(response.status()).isEqualTo(400);
            assertThat(response.json().get("code")).contains(Json.of("VALIDATION"));
        }

        @Test
        void refusesABodyOverTheLimit() throws Exception {
            Server server = Server.create(Router.of(ROUTES), Server.Options.DEFAULT.withBodyLimit(16));
            listen(server);
            String body =
                    Json.stringify(Json.object().put("padding", "x".repeat(200)).build());
            Optional<Integer> status = rawStatus(
                    server.port(),
                    "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: " + body.length()
                            + "\r\nConnection: close\r\n\r\n" + body);
            assertThat(status.isEmpty() || status.get() == 400)
                    .as("no answer at all, or a 400: %s", status)
                    .isTrue();
        }

        @Test
        void reportsEveryRequestToTheLogHook() throws Exception {
            List<String> lines = new CopyOnWriteArrayList<>();
            Server server = Server.create(Router.of(ROUTES), Server.Options.DEFAULT.withOnRequest(lines::add));
            String base = listen(server);
            fetch(base + "/ping");
            assertThat(lines).hasSize(1);
            assertThat(lines.get(0)).contains("GET /ping 200");
        }

        @Test
        void answers405ForTheWrongMethodOnAKnownPath() throws Exception {
            String base = start();
            Received response = fetch("DELETE", base + "/ping", Optional.empty());
            assertThat(response.status()).isEqualTo(405);
        }
    }
}
