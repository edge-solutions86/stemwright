package io.github.rkaushikdev.stemwright.http;

import io.github.rkaushikdev.stemwright.domain.errors.ErrorCode;
import io.github.rkaushikdev.stemwright.domain.errors.ErrorDetail;
import io.github.rkaushikdev.stemwright.domain.errors.StemwrightException;
import io.github.rkaushikdev.stemwright.infra.json.Json;
import io.github.rkaushikdev.stemwright.infra.json.JsonObject;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A failure as a client sees it: a code, a message, and the field level details when there are
 * any.
 *
 * <p>Two rules, and the second is the one that matters. Every error the domain raises maps to a
 * status and keeps its message, because the caller sent something wrong and deserves to know what.
 * Everything else becomes a bare 500 with a fixed message, because an unexpected error's message is
 * a stack trace, a file path or a connection string, and none of those belong on a wire that a
 * learner is on the other end of.
 *
 * @param code the domain's {@link ErrorCode} name, or {@code INTERNAL} for anything else
 * @param message what went wrong, safe to show the client
 * @param details the field level problems, empty when there are none
 */
public record Problem(String code, String message, List<ErrorDetail> details) {

    /** The only message a client sees for a failure the domain did not raise. */
    public static final String INTERNAL_MESSAGE = "the request could not be completed";

    public Problem {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        details = List.copyOf(details);
    }

    /** A problem with no details. */
    public static Problem of(String code, String message) {
        return new Problem(code, message, List.of());
    }

    public static int statusFor(ErrorCode code) {
        return switch (code) {
            case VALIDATION -> 400;
            case FORBIDDEN -> 403;
            case NOT_FOUND -> 404;
            case CONFLICT -> 409;
            case POLICY -> 422;
            case INTEGRITY -> 500;
            case UNSUPPORTED -> 501;
        };
    }

    /**
     * The status and the problem for a failure. An integrity failure is a bug in stored state, so it
     * keeps its code and loses its message; anything that is not a domain failure loses both.
     */
    public static Outcome forError(Throwable error) {
        if (!(error instanceof StemwrightException domain)) {
            return new Outcome(500, of("INTERNAL", INTERNAL_MESSAGE));
        }
        if (domain.code() == ErrorCode.INTEGRITY) {
            return new Outcome(500, of("INTEGRITY", INTERNAL_MESSAGE));
        }
        return new Outcome(
                statusFor(domain.code()), new Problem(domain.code().name(), domain.getMessage(), domain.details()));
    }

    /** The response for a failure: {@link #forError}'s status, with the problem as its body. */
    public static HttpResponse responseFor(Throwable error) {
        Outcome outcome = forError(error);
        return HttpResponse.json(outcome.status(), outcome.problem().toJson());
    }

    /** {@link #withProblems(Handler, Consumer)} reporting nothing. */
    public static Handler withProblems(Handler handler) {
        return withProblems(handler, error -> {});
    }

    /**
     * Wraps a handler so no exception it throws gets past. The reporter is called with the original
     * exception when it is not a domain failure, so an operator still sees the stack that the client
     * does not. An {@link Error}, such as running out of memory, is not something a response can
     * answer, and still propagates.
     */
    public static Handler withProblems(Handler handler, Consumer<? super RuntimeException> report) {
        return request -> {
            try {
                return handler.handle(request);
            } catch (RuntimeException error) {
                if (!StemwrightException.isStemwrightError(error)) {
                    report.accept(error);
                }
                return responseFor(error);
            }
        };
    }

    public static boolean isServerProblem(int status) {
        return status >= 500;
    }

    public static boolean isClientProblem(int status) {
        return status >= 400 && status < 500;
    }

    /** One line for a log, such as {@code 400 VALIDATION: two problems (2 details)}. */
    public String describe(int status) {
        String tail = details.isEmpty() ? "" : " (" + details.size() + " details)";
        return status + " " + code + ": " + message + tail;
    }

    /** The body a client receives, with {@code details} left out entirely when there are none. */
    public JsonObject toJson() {
        JsonObject.Builder body = Json.object().put("code", code).put("message", message);
        if (!details.isEmpty()) {
            body.put(
                    "details",
                    Json.array(details.stream()
                            .map(detail -> Json.object()
                                    .put("path", detail.path())
                                    .put("message", detail.message())
                                    .build())
                            .toList()));
        }
        return body.build();
    }

    /**
     * A problem and the status it travels under.
     *
     * @param status the http status
     * @param problem the body
     */
    public record Outcome(int status, Problem problem) {}
}
