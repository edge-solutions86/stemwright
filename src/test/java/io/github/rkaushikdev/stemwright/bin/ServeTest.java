package io.github.rkaushikdev.stemwright.bin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.http.AccountDirectory.Account;
import io.github.rkaushikdev.stemwright.http.Server;
import io.github.rkaushikdev.stemwright.infra.json.Json;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The server the entry point wires, answering on a socket, and the account map it refuses. */
class ServeTest {

    private final HttpClient client =
            HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private Server server;

    @AfterEach
    void stopWhatWasStarted() {
        if (server != null) {
            server.close();
        }
        client.close();
    }

    private String listen(ServeConfig config) {
        server = Serve.buildServer(config).listen("127.0.0.1", 0);
        return "http://127.0.0.1:" + server.port();
    }

    private HttpResponse<String> get(String url, Optional<String> token) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).GET();
        token.ifPresent(value -> request.header("Authorization", "Bearer " + value));
        return client.send(request.build(), BodyHandlers.ofString());
    }

    @Test
    void servesHealthWithTheConfiguredVersion() throws IOException, InterruptedException {
        String base = listen(new ServeConfig(0, "9.9.9", Map.of()));
        HttpResponse<String> response = get(base + "/health", Optional.empty());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(Json.parse(response.body()))
                .isEqualTo(Json.object()
                        .put("status", "up")
                        .put("version", "9.9.9")
                        .build());
    }

    @Test
    void resolvesTheConfiguredAccountsAndNoOthers() throws IOException, InterruptedException {
        String base = listen(new ServeConfig(0, "dev", Map.of("tok", new Account("lrn_one", "learner"))));
        assertThat(get(base + "/languages/latin/lexemes", Optional.of("tok")).statusCode())
                .isEqualTo(200);
        assertThat(get(base + "/languages/latin/lexemes", Optional.of("tok-unknown"))
                        .statusCode())
                .isEqualTo(403);
    }

    @Test
    void refusesAnAccountMapOfAnotherShape() {
        assertThatThrownBy(() -> Serve.readConfig(Map.of("STEMWRIGHT_ACCOUNTS", "{\"tok\":{\"id\":\"lrn_one\"}}")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("STEMWRIGHT_ACCOUNTS.tok.role is not a string");
        assertThatThrownBy(() -> Serve.readConfig(Map.of("STEMWRIGHT_ACCOUNTS", "[]")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("STEMWRIGHT_ACCOUNTS is not an object");
    }
}
