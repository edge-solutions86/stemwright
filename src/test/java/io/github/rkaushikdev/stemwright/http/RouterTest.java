package io.github.rkaushikdev.stemwright.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.infra.json.Json;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RouterTest {

    private static HttpRequest request(Method method, String path) {
        return HttpRequest.of(method, path);
    }

    private static Handler ok(String body) {
        return request -> HttpResponse.json(200, Json.object().put("body", body).build());
    }

    private static Json bodyOf(String body) {
        return Json.object().put("body", body).build();
    }

    private static final List<Route> ROUTES = List.of(
            Route.of(Method.GET, "/decks", ok("list")),
            Route.of(Method.POST, "/decks", ok("create")),
            Route.of(Method.GET, "/decks/:key", ok("one")));

    @Nested
    class SplitPath {

        @Test
        void dropsEmptySegmentsHoweverManySlashesThereAre() {
            assertThat(Router.splitPath("/a/b/")).containsExactly("a", "b");
            assertThat(Router.splitPath("//a//b")).containsExactly("a", "b");
            assertThat(Router.splitPath("/")).isEmpty();
        }
    }

    @Nested
    class MatchPath {

        @Test
        void matchesALiteralPath() {
            assertThat(Router.matchPath("/decks", "/decks")).contains(Map.of());
            assertThat(Router.matchPath("/decks", "/lexemes")).isEmpty();
        }

        @Test
        void capturesASegment() {
            assertThat(Router.matchPath("/decks/:key", "/decks/first-year")).contains(Map.of("key", "first-year"));
        }

        @Test
        void capturesSeveralSegments() {
            assertThat(Router.matchPath("/learners/:learnerId/decks/:key", "/learners/lrn_one/decks/all"))
                    .contains(Map.of("learnerId", "lrn_one", "key", "all"));
        }

        @Test
        void refusesAPathWithTheWrongNumberOfSegments() {
            assertThat(Router.matchPath("/decks/:key", "/decks")).isEmpty();
            assertThat(Router.matchPath("/decks/:key", "/decks/a/b")).isEmpty();
        }

        @Test
        void decodesACapturedSegment() {
            assertThat(Router.matchPath("/lexemes/:lemma", "/lexemes/puella%20mea"))
                    .contains(Map.of("lemma", "puella mea"));
        }

        @Test
        void ignoresTrailingSlashesOnEitherSide() {
            assertThat(Router.matchPath("/decks/", "/decks")).contains(Map.of());
        }
    }

    @Nested
    class ParseQuery {

        @Test
        void readsPairs() {
            assertThat(Router.parseQuery("?on=2025-03-03&deck=all"))
                    .isEqualTo(Map.of("on", "2025-03-03", "deck", "all"));
        }

        @Test
        void worksWithOrWithoutTheLeadingQuestionMark() {
            assertThat(Router.parseQuery("on=2025-03-03")).isEqualTo(Map.of("on", "2025-03-03"));
        }

        @Test
        void readsAKeyWithNoValueAsAnEmptyString() {
            assertThat(Router.parseQuery("?verbose")).isEqualTo(Map.of("verbose", ""));
        }

        @Test
        void decodesPercentEscapesAndPlusSigns() {
            assertThat(Router.parseQuery("?q=puella+mea&r=a%26b")).isEqualTo(Map.of("q", "puella mea", "r", "a&b"));
        }

        @Test
        void readsAnEmptyQueryAsNothing() {
            assertThat(Router.parseQuery("")).isEmpty();
            assertThat(Router.parseQuery("?")).isEmpty();
        }
    }

    @Nested
    class CreateRouter {

        private final Router handle = Router.of(ROUTES);

        @Test
        void dispatchesOnMethodAndPath() {
            assertThat(handle.handle(request(Method.GET, "/decks")).body()).isEqualTo(bodyOf("list"));
            assertThat(handle.handle(request(Method.POST, "/decks")).body()).isEqualTo(bodyOf("create"));
        }

        @Test
        void passesTheCapturesToTheHandler() {
            List<HttpRequest> seen = new ArrayList<>();
            Router single = Router.of(List.of(Route.of(Method.GET, "/decks/:key", incoming -> {
                seen.add(incoming);
                return HttpResponse.json(200, Json.NULL);
            })));
            single.handle(request(Method.GET, "/decks/first-year"));
            assertThat(seen).singleElement().extracting(HttpRequest::params).isEqualTo(Map.of("key", "first-year"));
        }

        @Test
        void answers404ForAPathNothingMatches() {
            HttpResponse response = handle.handle(request(Method.GET, "/nowhere"));
            assertThat(response.status()).isEqualTo(404);
            assertThat(response.body().get("code")).contains(Json.of("NOT_FOUND"));
        }

        @Test
        void answers405WhenThePathMatchesUnderAnotherMethod() {
            HttpResponse response = handle.handle(request(Method.DELETE, "/decks"));
            assertThat(response.status()).isEqualTo(405);
            assertThat(response.body().get("code")).contains(Json.of("METHOD_NOT_ALLOWED"));
        }

        @Test
        void takesTheFirstMatchingRouteWhenTwoPatternsOverlap() {
            Router overlapping = Router.of(List.of(
                    Route.of(Method.GET, "/decks/mine", ok("literal")),
                    Route.of(Method.GET, "/decks/:key", ok("capture"))));
            assertThat(overlapping.handle(request(Method.GET, "/decks/mine")).body())
                    .isEqualTo(bodyOf("literal"));
        }

        @Test
        void takesAnOverrideForTheTwoFailureHandlers() {
            Router custom = Router.of(
                    ROUTES,
                    Router.Options.DEFAULT
                            .withNotFound(request -> HttpResponse.json(
                                    410, Json.object().put("gone", true).build()))
                            .withMethodNotAllowed(request -> HttpResponse.json(
                                    418, Json.object().put("teapot", true).build())));
            assertThat(custom.handle(request(Method.GET, "/nowhere")).status()).isEqualTo(410);
            assertThat(custom.handle(request(Method.DELETE, "/decks")).status()).isEqualTo(418);
        }
    }

    @Nested
    class ResponseHelpers {

        @Test
        void buildsAJsonResponse() {
            assertThat(HttpResponse.json(201, Json.object().put("id", "a").build()))
                    .isEqualTo(
                            new HttpResponse(201, Json.object().put("id", "a").build(), Map.of()));
        }

        @Test
        void buildsAnEmptyResponse() {
            assertThat(HttpResponse.noContent()).isEqualTo(new HttpResponse(204, Json.NULL, Map.of()));
        }
    }

    @Nested
    class MethodsFor {

        @Test
        void namesEveryMethodAPathAccepts() {
            assertThat(Router.methodsFor(ROUTES, "/decks")).containsExactly(Method.GET, Method.POST);
            assertThat(Router.methodsFor(ROUTES, "/decks/all")).containsExactly(Method.GET);
        }

        @Test
        void namesNoneForAPathNothingMatches() {
            assertThat(Router.methodsFor(ROUTES, "/nowhere")).isEmpty();
        }
    }
}
