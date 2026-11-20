package io.github.rkaushikdev.stemwright.http;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.text.Text;
import io.github.rkaushikdev.stemwright.infra.json.Json;
import io.github.rkaushikdev.stemwright.infra.json.JsonSyntaxException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The adapter over the JDK's {@code com.sun.net.httpserver}. It does four things and nothing else:
 * read the body, build the request the router understands, write the response, and make sure no
 * failure escapes as a hanging socket.
 *
 * <p>A body larger than the cap is refused while it is still arriving rather than after it has all
 * been buffered, because the point of a cap that is enforced after the fact is difficult to explain.
 *
 * <p>Every exchange runs on a virtual thread of its own. A request here spends its life blocked on
 * a socket or a store rather than computing, which is what virtual threads are cheap at, and a
 * thread per exchange leaves no pool size to get wrong: a slow client ties up a virtual thread, not
 * one of a handful of platform threads every other learner is queueing behind. What bounds the work
 * one client can cause is the body limit here and the rate limit in front of the routes, not the
 * executor. The handler is therefore called from many threads at once, as every {@link Handler} is
 * documented to allow.
 *
 * <p>A server is created stopped. {@link #listen(String, int)} binds it, port 0 asking the system
 * for a free port, which {@link #port()} then reports; {@link #close()} stops it again.
 */
public final class Server implements AutoCloseable {

    /** The largest body read when the options name no limit of their own, in bytes. */
    public static final int DEFAULT_BODY_LIMIT = 1_000_000;

    private static final String CONTENT_TYPE = "application/json; charset=utf-8";
    private static final int CONTINUE = 100;
    private static final int OK = 200;
    private static final int NO_CONTENT = 204;
    private static final int NOT_MODIFIED = 304;
    private static final int BAD_REQUEST = 400;
    private static final int INTERNAL_ERROR = 500;
    private static final int READ_CHUNK = 8_192;
    private static final long NANOS_PER_MILLI = 1_000_000L;

    /** How long a stop waits for the exchanges in flight before it closes their connections. */
    private static final int STOP_GRACE_SECONDS = 1;

    private final Handler guarded;
    private final Options options;
    private HttpServer bound;
    private ExecutorService executor;

    private Server(Handler handler, Options options) {
        this.options = Objects.requireNonNull(options, "options");
        this.guarded = Problem.withProblems(Objects.requireNonNull(handler, "handler"), options.onError());
    }

    /** A stopped server over the handler, with the default options. */
    public static Server create(Handler handler) {
        return create(handler, Options.DEFAULT);
    }

    /**
     * A stopped server over the handler. Whatever the handler throws is answered through {@link
     * Problem#withProblems}, which reports to {@link Options#onError()} what the domain did not raise.
     */
    public static Server create(Handler handler, Options options) {
        return new Server(handler, options);
    }

    /** True for the four methods the api answers, compared as written. */
    public static boolean isMethod(String value) {
        return Method.fromKey(value).isPresent();
    }

    /** {@link #readBody(InputStream, int)} under {@link #DEFAULT_BODY_LIMIT}. */
    public static String readBody(InputStream body) throws IOException {
        return readBody(body, DEFAULT_BODY_LIMIT);
    }

    /**
     * Reads the body to its end as utf-8, counting as it goes, and stops reading as soon as the count
     * passes the limit.
     *
     * @throws ValidationException {@code the request body is larger than <limit> bytes}, as soon as
     *     the body is known to be
     * @throws IOException when the body cannot be read, such as when the client went away
     */
    public static String readBody(InputStream body, int limit) throws IOException {
        ByteArrayOutputStream chunks = new ByteArrayOutputStream();
        byte[] chunk = new byte[READ_CHUNK];
        long size = 0;
        for (int read = body.read(chunk); read >= 0; read = body.read(chunk)) {
            size += read;
            if (size > limit) {
                throw new ValidationException("the request body is larger than " + limit + " bytes");
            }
            chunks.write(chunk, 0, read);
        }
        return chunks.toString(StandardCharsets.UTF_8);
    }

    /**
     * The body as json, or nothing for a body that is empty or only spacing.
     *
     * @throws ValidationException {@code the request body is not valid json} for anything else that
     *     does not parse
     */
    public static Optional<Json> parseBody(String text) {
        if (Text.trim(text).isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Json.parse(text));
        } catch (JsonSyntaxException error) {
            throw new ValidationException("the request body is not valid json", List.of(), error);
        }
    }

    /**
     * The request the router sees. The method is upper cased and must be one of the four the api
     * answers; the target is split at its first question mark into the path, kept percent encoded,
     * and the query, decoded by {@link Router#parseQuery}. Header names are lower cased, and a header
     * sent more than once reads as its values joined by a comma and a space.
     *
     * @param method the method as the request line wrote it
     * @param target the request target as the request line wrote it, such as {@code /ping?on=x}
     * @param headers the headers by name, each with the values it was sent with
     * @param body the parsed body
     * @throws ValidationException {@code the method <METHOD> is not supported} for any other method
     * @throws IllegalArgumentException when the query holds a broken percent escape
     */
    public static HttpRequest toHttpRequest(
            String method, String target, Map<String, List<String>> headers, Optional<Json> body) {
        String upper = Text.upper(method);
        Method parsed = Method.fromKey(upper)
                .orElseThrow(() -> new ValidationException("the method " + upper + " is not supported"));
        int at = target.indexOf('?');
        Map<String, String> named = new LinkedHashMap<>();
        headers.forEach((name, values) -> named.merge(Text.lower(name), String.join(", ", values), Server::joined));
        return new HttpRequest(
                parsed,
                at < 0 ? target : target.substring(0, at),
                Map.of(),
                at < 0 ? Map.of() : Router.parseQuery(target.substring(at)),
                named,
                body);
    }

    /**
     * Binds every local address at the port and starts answering, a port of 0 asking the system for
     * a free one.
     *
     * @return this server, now listening
     * @throws IllegalStateException when it is already listening
     * @throws UncheckedIOException when the port cannot be bound
     */
    public Server listen(int port) {
        return listen(new InetSocketAddress(port));
    }

    /**
     * Binds the host at the port and starts answering, a port of 0 asking the system for a free one.
     *
     * @return this server, now listening
     * @throws IllegalStateException when it is already listening
     * @throws UncheckedIOException when the address cannot be bound
     */
    public Server listen(String host, int port) {
        return listen(new InetSocketAddress(host, port));
    }

    private synchronized Server listen(InetSocketAddress address) {
        if (bound != null) {
            throw new IllegalStateException("the server is already listening on port " + port());
        }
        HttpServer server;
        try {
            server = HttpServer.create(address, 0);
        } catch (IOException error) {
            throw new UncheckedIOException("could not listen on " + address, error);
        }
        ExecutorService threads = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("stemwright-http-", 0).factory());
        server.createContext("/", this::exchange);
        server.setExecutor(threads);
        server.start();
        this.bound = server;
        this.executor = threads;
        return this;
    }

    /** Whether the server is bound and answering. */
    public synchronized boolean isListening() {
        return bound != null;
    }

    /**
     * The port the server is bound to, which is the one the system chose when it was asked for port
     * 0.
     *
     * @throws IllegalStateException when the server is not listening
     */
    public synchronized int port() {
        if (bound == null) {
            throw new IllegalStateException("the server is not listening");
        }
        return bound.getAddress().getPort();
    }

    /**
     * Stops the server: it stops accepting, gives the exchanges in flight a second to finish, closes
     * every connection, and waits for the threads that were answering. Closing a server that is not
     * listening does nothing, and a closed server may listen again.
     */
    @Override
    public synchronized void close() {
        if (bound == null) {
            return;
        }
        bound.stop(STOP_GRACE_SECONDS);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(STOP_GRACE_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            bound = null;
            executor = null;
        }
    }

    /**
     * Answers one exchange. The log line is written once the reply is settled and before it is sent,
     * so a client that has its response can count on the line being there. A log hook that throws
     * still lets the response go out. Its failure, like a response that cannot be sent because the
     * client went away, is left to the JDK server, which notes it in its own log and closes the
     * connection unless the response was complete.
     */
    private void exchange(HttpExchange exchange) throws IOException {
        long started = System.nanoTime();
        String target = exchange.getRequestURI().toString();
        try (exchange) {
            Reply reply;
            try {
                reply = replyTo(exchange, target);
            } catch (IOException | RuntimeException error) {
                reply = failureOf(error);
            }
            try {
                options.onRequest()
                        .accept(exchange.getRequestMethod() + " " + target + " " + reply.status() + " "
                                + (System.nanoTime() - started) / NANOS_PER_MILLI + "ms");
            } finally {
                send(exchange, reply);
            }
        }
    }

    /**
     * Reads, routes and renders, without writing anything, so a failure anywhere up to the last
     * header still has a clean response to fall back to. The headers are checked here, as they are
     * set, rather than when they are written.
     */
    private Reply replyTo(HttpExchange exchange, String target) throws IOException {
        String text = readBody(exchange.getRequestBody(), options.bodyLimit());
        HttpRequest request =
                toHttpRequest(exchange.getRequestMethod(), target, exchange.getRequestHeaders(), parseBody(text));
        HttpResponse response = guarded.handle(request);
        Headers headers = jsonHeaders();
        response.headers().forEach(headers::set);
        String payload = response.status() == NO_CONTENT ? "" : Json.stringify(response.body());
        return new Reply(response.status(), headers, payload);
    }

    /**
     * What the client sees when the adapter itself failed: a validation failure keeps its code and
     * message, and anything else is reported and hidden behind a bare 500.
     */
    private Reply failureOf(Exception error) {
        if (error instanceof ValidationException refused) {
            return new Reply(BAD_REQUEST, jsonHeaders(), problemText("VALIDATION", refused.getMessage()));
        }
        options.onError().accept(error);
        return new Reply(INTERNAL_ERROR, jsonHeaders(), problemText("INTERNAL", Problem.INTERNAL_MESSAGE));
    }

    private static String problemText(String code, String message) {
        return Json.stringify(
                Json.object().put("code", code).put("message", message).build());
    }

    private static Headers jsonHeaders() {
        Headers headers = new Headers();
        headers.set("content-type", CONTENT_TYPE);
        return headers;
    }

    /**
     * Writes the reply. A response that may not carry a body, which is any response to a {@code HEAD}
     * and any 1xx, 204 or 304, is sent without one, as RFC 9110 requires.
     */
    private static void send(HttpExchange exchange, Reply reply) throws IOException {
        exchange.getResponseHeaders().putAll(reply.headers());
        byte[] payload = reply.payload().getBytes(StandardCharsets.UTF_8);
        if (payload.length == 0 || !mayCarryBody(exchange.getRequestMethod(), reply.status())) {
            exchange.sendResponseHeaders(reply.status(), -1);
            return;
        }
        exchange.sendResponseHeaders(reply.status(), payload.length);
        exchange.getResponseBody().write(payload);
    }

    private static boolean mayCarryBody(String method, int status) {
        boolean informational = status >= CONTINUE && status < OK;
        return !method.equals("HEAD") && !informational && status != NO_CONTENT && status != NOT_MODIFIED;
    }

    private static String joined(String first, String second) {
        return first + ", " + second;
    }

    /**
     * A response settled and not yet written.
     *
     * @param status the status
     * @param headers every response header, the content type among them
     * @param payload the body text, empty when there is no body
     */
    private record Reply(int status, Headers headers, String payload) {}

    /**
     * How a server reads and reports.
     *
     * @param bodyLimit the largest body read, in bytes
     * @param onError told of every failure the client is only shown a bare 500 for: what a handler
     *     threw that the domain did not raise, and what went wrong reading a request
     * @param onRequest told of every request once its response is settled, as {@code GET /ping 200
     *     3ms}: the method and target as they were sent, the status and the milliseconds taken
     */
    public record Options(int bodyLimit, Consumer<? super Exception> onError, Consumer<String> onRequest) {

        /** The default body limit, reporting nothing. */
        public static final Options DEFAULT = new Options(DEFAULT_BODY_LIMIT, error -> {}, line -> {});

        public Options {
            Objects.requireNonNull(onError, "onError");
            Objects.requireNonNull(onRequest, "onRequest");
        }

        public Options withBodyLimit(int limit) {
            return new Options(limit, onError, onRequest);
        }

        public Options withOnError(Consumer<? super Exception> reporter) {
            return new Options(bodyLimit, reporter, onRequest);
        }

        public Options withOnRequest(Consumer<String> logger) {
            return new Options(bodyLimit, onError, logger);
        }
    }
}
