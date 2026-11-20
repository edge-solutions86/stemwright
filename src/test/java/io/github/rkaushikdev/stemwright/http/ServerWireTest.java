package io.github.rkaushikdev.stemwright.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.text.Text;
import io.github.rkaushikdev.stemwright.infra.json.Json;
import io.github.rkaushikdev.stemwright.infra.json.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * What the adapter does on the wire, below the routes: how header names reach a route, which
 * methods are refused before routing, which headers and bodies go out, what the log line holds,
 * and how a server binds and stops.
 */
class ServerWireTest {

    private static final int READ_TIMEOUT_MS = 10_000;

    private static final List<Route> ROUTES = List.of(
            Route.of(
                    Method.GET,
                    "/ping",
                    request -> HttpResponse.json(
                            200, Json.object().put("pong", true).build())),
            Route.of(Method.GET, "/headers", request -> HttpResponse.json(200, objectOf(request.headers()))),
            Route.of(Method.GET, "/typed", request -> HttpResponse.json(200, Json.of("plain"))
                    .withHeaders(Map.of("content-type", "text/plain", "x-trace", "abc"))),
            Route.of(Method.GET, "/split", request -> HttpResponse.json(200, Json.of("split"))
                    .withHeaders(Map.of("x-split", "a\nb"))));

    private final List<Server> servers = new ArrayList<>();

    @AfterEach
    void stopEverythingStarted() {
        servers.forEach(Server::close);
    }

    private static JsonObject objectOf(Map<String, String> values) {
        JsonObject.Builder object = Json.object();
        values.forEach(object::put);
        return object.build();
    }

    private Server started(Server.Options options) {
        Server server = Server.create(Router.of(ROUTES), options);
        servers.add(server);
        return server.listen("127.0.0.1", 0);
    }

    /** {@link #exchange(Server, String, String)} with no body. */
    private static Exchanged exchange(Server server, String head) throws IOException {
        return exchange(server, head, "");
    }

    /**
     * One request on a socket of its own, which the server closes once it has answered.
     *
     * @param head the request line and any header lines of the request's own, each ending in a line
     *     break; the host, the connection and the length of the body are added
     */
    private static Exchanged exchange(Server server, String head, String body) throws IOException {
        String request = head + "Host: 127.0.0.1\r\nConnection: close\r\nContent-Length: "
                + body.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + body;
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(READ_TIMEOUT_MS);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            return Exchanged.of(new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /**
     * A response read off a socket.
     *
     * @param status the status of the status line
     * @param head the header lines, by lower cased name
     * @param body what followed the blank line
     */
    private record Exchanged(int status, Map<String, String> head, String body) {

        private static final String VERSION = "HTTP/1.1 ";

        static Exchanged of(String text) {
            int end = text.indexOf("\r\n\r\n");
            List<String> lines = Text.split(text.substring(0, end), "\r\n");
            Map<String, String> head = new LinkedHashMap<>();
            for (String line : lines.subList(1, lines.size())) {
                int colon = line.indexOf(':');
                head.put(Text.lower(line.substring(0, colon)), Text.trim(line.substring(colon + 1)));
            }
            int status = Integer.parseInt(lines.get(0).substring(VERSION.length(), VERSION.length() + 3));
            return new Exchanged(status, head, text.substring(end + 4));
        }

        Json json() {
            return Json.parse(body);
        }
    }

    private static Json problem(String code, String message) {
        return Json.object().put("code", code).put("message", message).build();
    }

    @Test
    void lowerCasesHeaderNamesBeforeARouteSeesThem() throws IOException {
        Server server = started(Server.Options.DEFAULT);
        Json seen = exchange(server, "GET /headers HTTP/1.1\r\nX-Probe: yes\r\nAuthorization: Bearer tok\r\n")
                .json();
        assertThat(seen.get("x-probe")).contains(Json.of("yes"));
        assertThat(seen.get("authorization")).contains(Json.of("Bearer tok"));
        assertThat(seen.get("X-Probe")).isEmpty();
    }

    @Test
    void refusesAMethodTheApiDoesNotAnswerBeforeRouting() throws IOException {
        Server server = started(Server.Options.DEFAULT);
        Exchanged answer = exchange(server, "PATCH /ping HTTP/1.1\r\n");
        assertThat(answer.status()).isEqualTo(400);
        assertThat(answer.head()).containsEntry("content-type", "application/json; charset=utf-8");
        assertThat(answer.json()).isEqualTo(problem("VALIDATION", "the method PATCH is not supported"));
    }

    @Test
    void sendsNoBodyInAnswerToAHeadRequest() throws IOException {
        Server server = started(Server.Options.DEFAULT);
        Exchanged answer = exchange(server, "HEAD /ping HTTP/1.1\r\n");
        assertThat(answer.status()).isEqualTo(400);
        assertThat(answer.body()).isEmpty();
    }

    @Test
    void refusesABodyOverTheLimitWithAProblem() throws IOException {
        Server server = started(Server.Options.DEFAULT.withBodyLimit(16));
        Exchanged answer = exchange(server, "POST /ping HTTP/1.1\r\n", "{\"padding\":\"" + "x".repeat(200) + "\"}");
        assertThat(answer.status()).isEqualTo(400);
        assertThat(answer.json()).isEqualTo(problem("VALIDATION", "the request body is larger than 16 bytes"));
    }

    @Test
    void writesTheExtraHeadersOfAResponseOverItsContentType() throws IOException {
        Server server = started(Server.Options.DEFAULT);
        Exchanged answer = exchange(server, "GET /typed HTTP/1.1\r\n");
        assertThat(answer.head()).containsEntry("content-type", "text/plain").containsEntry("x-trace", "abc");
        assertThat(answer.body()).isEqualTo("\"plain\"");
    }

    @Test
    void answersAHeaderTheResponseCannotCarryAsAnUnexpectedFailure() throws IOException {
        List<Exception> seen = new CopyOnWriteArrayList<>();
        Server server = started(Server.Options.DEFAULT.withOnError(seen::add));
        Exchanged answer = exchange(server, "GET /split HTTP/1.1\r\n");
        assertThat(answer.status()).isEqualTo(500);
        assertThat(answer.head()).doesNotContainKey("x-split");
        assertThat(answer.json()).isEqualTo(problem("INTERNAL", "the request could not be completed"));
        assertThat(seen).singleElement().isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void answersABrokenEscapeInTheQueryAsAnUnexpectedFailure() throws IOException {
        List<Exception> seen = new CopyOnWriteArrayList<>();
        Server server = started(Server.Options.DEFAULT.withOnError(seen::add));
        Exchanged answer = exchange(server, "GET /ping?q=%FF HTTP/1.1\r\n");
        assertThat(answer.status()).isEqualTo(500);
        assertThat(answer.json()).isEqualTo(problem("INTERNAL", "the request could not be completed"));
        assertThat(seen).singleElement().isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void logsTheMethodAndTargetAsSentWithTheStatusAndTheMilliseconds() throws IOException {
        List<String> lines = new CopyOnWriteArrayList<>();
        Server server = started(Server.Options.DEFAULT.withOnRequest(lines::add));
        exchange(server, "get /ping?x=1 HTTP/1.1\r\n");
        exchange(server, "PATCH /ping HTTP/1.1\r\n");
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).matches("get /ping\\?x=1 200 \\d+ms");
        assertThat(lines.get(1)).matches("PATCH /ping 400 \\d+ms");
    }

    @Test
    void bindsAnEphemeralPortAndStopsCleanly() throws IOException {
        Server server = Server.create(Router.of(ROUTES));
        servers.add(server);
        assertThat(server.isListening()).isFalse();
        assertThatThrownBy(server::port).isInstanceOf(IllegalStateException.class);

        server.listen("127.0.0.1", 0);
        assertThat(server.isListening()).isTrue();
        assertThat(server.port()).isPositive();
        assertThatThrownBy(() -> server.listen("127.0.0.1", 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already listening");
        assertThat(exchange(server, "GET /ping HTTP/1.1\r\n").status()).isEqualTo(200);

        server.close();
        server.close();
        assertThat(server.isListening()).isFalse();
        assertThatThrownBy(server::port).isInstanceOf(IllegalStateException.class);

        server.listen(0);
        assertThat(exchange(server, "GET /ping HTTP/1.1\r\n").status()).isEqualTo(200);
    }

    @Test
    void readsTheTargetMethodAndHeadersIntoTheRequestARouteSees() {
        HttpRequest request = Server.toHttpRequest(
                "get",
                "/echo/puella%20mea?on=2025-03-03&q=x+y",
                Map.of("X-One", List.of("1", "2")),
                Optional.of(Json.of(true)));
        assertThat(request.method()).isEqualTo(Method.GET);
        assertThat(request.path()).isEqualTo("/echo/puella%20mea");
        assertThat(request.query()).isEqualTo(Map.of("on", "2025-03-03", "q", "x y"));
        assertThat(request.headers()).isEqualTo(Map.of("x-one", "1, 2"));
        assertThat(request.params()).isEmpty();
        assertThat(request.body()).contains(Json.of(true));
        assertThat(Server.toHttpRequest("GET", "/ping", Map.of(), Optional.empty())
                        .query())
                .isEmpty();
        assertThatThrownBy(() -> Server.toHttpRequest("patch", "/ping", Map.of(), Optional.empty()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("the method PATCH is not supported");
    }

    @Test
    void stopsReadingABodyAsSoonAsItPassesTheLimit() throws IOException {
        assertThat(Server.readBody(new ByteArrayInputStream("0123456789abcdef".getBytes(StandardCharsets.UTF_8)), 16))
                .isEqualTo("0123456789abcdef");
        assertThatThrownBy(() -> Server.readBody(
                        new ByteArrayInputStream("0123456789abcdefg".getBytes(StandardCharsets.UTF_8)), 16))
                .isInstanceOf(ValidationException.class)
                .hasMessage("the request body is larger than 16 bytes");

        CountingStream endless = new CountingStream(Server.DEFAULT_BODY_LIMIT * 4L);
        assertThatThrownBy(() -> Server.readBody(endless)).isInstanceOf(ValidationException.class);
        assertThat(endless.served).isLessThan(Server.DEFAULT_BODY_LIMIT * 2L);
    }

    /** A stream of the given number of zero bytes that counts how many were read. */
    private static final class CountingStream extends InputStream {

        private final long size;
        private long served;

        CountingStream(long size) {
            this.size = size;
        }

        @Override
        public int read() {
            if (served >= size) {
                return -1;
            }
            served += 1;
            return 0;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (served >= size) {
                return -1;
            }
            int count = (int) Math.min(length, size - served);
            served += count;
            return count;
        }
    }
}
