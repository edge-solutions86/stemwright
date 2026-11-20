package io.github.rkaushikdev.stemwright.http;

import static io.github.rkaushikdev.stemwright.support.Http.DIRECTORY;
import static io.github.rkaushikdev.stemwright.support.Http.httpHarness;
import static io.github.rkaushikdev.stemwright.support.Http.makeRequest;
import static io.github.rkaushikdev.stemwright.support.Http.textAt;
import static io.github.rkaushikdev.stemwright.support.Http.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.app.Application;
import io.github.rkaushikdev.stemwright.app.ApplicationOptions;
import io.github.rkaushikdev.stemwright.bin.Serve;
import io.github.rkaushikdev.stemwright.bin.ServeConfig;
import io.github.rkaushikdev.stemwright.http.AccountDirectory.Account;
import io.github.rkaushikdev.stemwright.infra.json.Json;
import io.github.rkaushikdev.stemwright.support.Http;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ApiTest {

    private static final String QUEUE = "/learners/lrn_one/languages/latin/decks/all/queue";

    /** The application, the options the api was built with, and the assembled api over both. */
    private record Built(Application app, ApiOptions apiOptions, Handler handle) {}

    private static Built build() {
        return build(Optional.empty());
    }

    private static Built build(BooleanSupplier isReady) {
        return build(Optional.of(isReady));
    }

    private static Built build(Optional<BooleanSupplier> isReady) {
        Http.Harness harness = httpHarness();
        Application app = Application.create(harness.stores(), ApplicationOptions.of(harness.bus(), harness.clock()));

        ApiOptions.Builder options = ApiOptions.builder(DIRECTORY).version("1.2.3");
        isReady.ifPresent(options::readiness);
        ApiOptions apiOptions = options.build();
        return new Built(app, apiOptions, Api.createApi(app, apiOptions));
    }

    @Nested
    class HealthAndReadiness {

        @Test
        void answersHealthWithTheVersion() {
            Handler handle = build().handle();
            HttpResponse response = handle.handle(makeRequest(Method.GET, "/health"));
            assertThat(response.status()).isEqualTo(200);
            assertThat(response.body())
                    .isEqualTo(Json.object()
                            .put("status", "up")
                            .put("version", "1.2.3")
                            .build());
        }

        @Test
        void answersReadinessWhenTheCheckPasses() {
            Handler handle = build(() -> true).handle();
            assertThat(handle.handle(makeRequest(Method.GET, "/ready")).status())
                    .isEqualTo(200);
        }

        @Test
        void answers503WhenTheReadinessCheckFails() {
            Handler handle = build(() -> false).handle();
            HttpResponse response = handle.handle(makeRequest(Method.GET, "/ready"));
            assertThat(response.status()).isEqualTo(503);
            assertThat(textAt(response.body(), "status")).isEqualTo("not ready");
        }

        @Test
        void staysUpEvenWhenItIsNotReady() {
            Handler handle = build(() -> false).handle();
            assertThat(handle.handle(makeRequest(Method.GET, "/health")).status())
                    .isEqualTo(200);
        }

        @Test
        void needsNoToken() {
            ApiOptions apiOptions = build().apiOptions();
            Router handle = Router.of(Api.healthRoutes(apiOptions));
            assertThat(handle.handle(makeRequest(Method.GET, "/health")).status())
                    .isEqualTo(200);
        }

        @Test
        void reportsAnUnknownVersionWhenNoneIsConfigured() {
            Application app = build().app();
            Handler handle = Api.createApi(app, ApiOptions.of(DIRECTORY));
            assertThat(textAt(handle.handle(makeRequest(Method.GET, "/health")).body(), "version"))
                    .isEqualTo("unknown");
        }
    }

    @Nested
    class TheAssembledApi {

        @Test
        void carriesEveryRouteFamily() {
            Built built = build();
            List<String> table = Api.routeTable(built.app(), built.apiOptions());
            assertThat(table).contains("GET /health");
            assertThat(table.stream().anyMatch(line -> line.contains("/queue"))).isTrue();
            assertThat(table.stream().anyMatch(line -> line.contains("/lexemes")))
                    .isTrue();
            assertThat(table.stream().anyMatch(line -> line.contains("/coverage")))
                    .isTrue();
        }

        @Test
        void turnsAThrownDomainFailureIntoAResponseRatherThanAThrow() {
            Handler handle = build().handle();
            HttpResponse response = handle.handle(makeRequest(Method.GET, QUEUE, token("tok-student")));
            assertThat(response.status()).isEqualTo(400);
            assertThat(textAt(response.body(), "code")).isEqualTo("VALIDATION");
        }

        @Test
        void answers403ForARequestWithNoTokenRatherThanThrowing() {
            Handler handle = build().handle();
            HttpResponse response = handle.handle(makeRequest(Method.GET, "/languages/latin/lexemes"));
            assertThat(response.status()).isEqualTo(403);
        }

        @Test
        void answers404ForAPathNothingMatches() {
            Handler handle = build().handle();
            assertThat(handle.handle(makeRequest(Method.GET, "/nowhere")).status())
                    .isEqualTo(404);
        }

        @Test
        void servesADrillQueueEndToEnd() {
            Handler handle = build().handle();
            HttpResponse response = handle.handle(
                    makeRequest(Method.GET, QUEUE, token("tok-student").withQuery(Map.of("course", "latin-1a"))));
            assertThat(response.status()).isEqualTo(200);
        }
    }

    @Nested
    class ReadConfig {

        @Test
        void defaultsThePortAndTheVersion() {
            ServeConfig config = Serve.readConfig(Map.of());
            assertThat(config.port()).isEqualTo(8080);
            assertThat(config.version()).isEqualTo("dev");
            assertThat(config.accounts()).isEmpty();
        }

        @Test
        void readsThePortFromTheEnvironment() {
            assertThat(Serve.readConfig(Map.of("STEMWRIGHT_PORT", "9000")).port())
                    .isEqualTo(9000);
        }

        @Test
        void refusesAPortThatIsNotAPort() {
            assertThatThrownBy(() -> Serve.readConfig(Map.of("STEMWRIGHT_PORT", "nope")))
                    .hasMessageContaining("not a port number");
            assertThatThrownBy(() -> Serve.readConfig(Map.of("STEMWRIGHT_PORT", "70000")))
                    .hasMessageContaining("not a port number");
        }

        @Test
        void readsTheAccountMap() {
            ServeConfig config = Serve.readConfig(Map.of(
                    "STEMWRIGHT_ACCOUNTS",
                    Json.stringify(Json.object()
                            .put(
                                    "tok",
                                    Json.object()
                                            .put("id", "lrn_one")
                                            .put("role", "learner")
                                            .build())
                            .build())));
            assertThat(config.accounts().get("tok")).isEqualTo(new Account("lrn_one", "learner"));
        }
    }
}
