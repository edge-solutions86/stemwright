package io.github.rkaushikdev.stemwright.http;

import io.github.rkaushikdev.stemwright.app.Application;
import io.github.rkaushikdev.stemwright.domain.course.CourseScope;
import io.github.rkaushikdev.stemwright.http.routes.BulkRoutes;
import io.github.rkaushikdev.stemwright.http.routes.DrillRouteDeps;
import io.github.rkaushikdev.stemwright.http.routes.DrillRoutes;
import io.github.rkaushikdev.stemwright.http.routes.LexiconRouteDeps;
import io.github.rkaushikdev.stemwright.http.routes.LexiconRoutes;
import io.github.rkaushikdev.stemwright.http.routes.ReportRouteDeps;
import io.github.rkaushikdev.stemwright.http.routes.ReportRoutes;
import io.github.rkaushikdev.stemwright.infra.json.Json;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * The whole http surface, assembled once.
 *
 * <p>Health and readiness are separate and they answer different questions. Health says the
 * process is up, and a load balancer uses it to decide whether to kill the container. Readiness says
 * the process can serve, and an orchestrator uses it to decide whether to send traffic. Wiring them
 * to the same check is the standard way to build a service that restarts itself forever under load.
 */
public final class Api {

    private Api() {}

    /**
     * {@code GET /health}, answering {@code {"status":"up","version":...}}, and {@code GET /ready},
     * answering 200 with {@code {"status":"ready"}} while the readiness check passes and 503 with
     * {@code {"status":"not ready",...}} while it fails. Neither needs a token.
     */
    public static List<Route> healthRoutes(ApiOptions options) {
        String version = options.version().orElse("unknown");
        BooleanSupplier isReady = options.readiness().orElse(() -> true);

        return List.of(
                Route.of(
                        Method.GET,
                        "/health",
                        request -> HttpResponse.json(
                                200,
                                Json.object()
                                        .put("status", "up")
                                        .put("version", version)
                                        .build())),
                Route.of(
                        Method.GET,
                        "/ready",
                        request -> isReady.getAsBoolean()
                                ? HttpResponse.json(
                                        200,
                                        Json.object().put("status", "ready").build())
                                : HttpResponse.json(
                                        503,
                                        Json.object()
                                                .put("status", "not ready")
                                                .put("message", "the store has not finished loading")
                                                .build())));
    }

    /**
     * Every route the api serves, in the order the router tries them: health and readiness, then the
     * drill, bulk answer, lexicon and report routes. Each route that checks a course resolves it
     * through the application's course service.
     */
    public static List<Route> apiRoutes(Application app, ApiOptions options) {
        Function<String, CourseScope> scopeOf = courseKey -> app.courses().scopeOf(courseKey);
        DrillRouteDeps drill = new DrillRouteDeps(options.directory(), app.drill(), scopeOf);

        return Stream.of(
                        healthRoutes(options),
                        DrillRoutes.routes(drill),
                        BulkRoutes.routes(drill),
                        LexiconRoutes.routes(new LexiconRouteDeps(options.directory(), app.lexicon())),
                        ReportRoutes.routes(new ReportRouteDeps(options.directory(), app.reports(), scopeOf)))
                .flatMap(List::stream)
                .toList();
    }

    /**
     * The handler a server runs: a router over {@link #apiRoutes}, behind {@link
     * Problem#withProblems}, so that whatever a route throws becomes a response. A failure the domain
     * did not raise is reported to {@link ApiOptions#onError()} as well.
     */
    public static Handler createApi(Application app, ApiOptions options) {
        return Problem.withProblems(
                Router.of(apiRoutes(app, options)), options.onError().orElse(error -> {}));
    }

    /** Every route as {@code METHOD /pattern}, in code unit order, for an operator to read. */
    public static List<String> routeTable(Application app, ApiOptions options) {
        return apiRoutes(app, options).stream()
                .map(entry -> entry.method().key() + " " + entry.pattern())
                .sorted()
                .toList();
    }
}
