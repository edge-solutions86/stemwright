package io.github.rkaushikdev.stemwright.http.routes;

import static io.github.rkaushikdev.stemwright.support.App.seededStores;
import static io.github.rkaushikdev.stemwright.support.App.testHarness;
import static io.github.rkaushikdev.stemwright.support.Drill.PUELLA;
import static io.github.rkaushikdev.stemwright.support.Http.DIRECTORY;
import static io.github.rkaushikdev.stemwright.support.Http.items;
import static io.github.rkaushikdev.stemwright.support.Http.makeRequest;
import static io.github.rkaushikdev.stemwright.support.Http.textAt;
import static io.github.rkaushikdev.stemwright.support.Http.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.app.Stores;
import io.github.rkaushikdev.stemwright.app.services.LexiconService;
import io.github.rkaushikdev.stemwright.domain.errors.ForbiddenException;
import io.github.rkaushikdev.stemwright.domain.errors.NotFoundException;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.http.HttpRequest;
import io.github.rkaushikdev.stemwright.http.HttpResponse;
import io.github.rkaushikdev.stemwright.http.Method;
import io.github.rkaushikdev.stemwright.http.Router;
import io.github.rkaushikdev.stemwright.infra.json.Json;
import io.github.rkaushikdev.stemwright.infra.json.JsonArray;
import io.github.rkaushikdev.stemwright.infra.json.JsonObject;
import io.github.rkaushikdev.stemwright.support.App.Harness;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LexiconRoutesTest {

    private static final JsonObject NAUTA = Json.object()
            .put("lemma", "nauta")
            .put("classKey", "first-declension")
            .put(
                    "stems",
                    Json.array(Json.object()
                            .put("role", "base")
                            .put("form", "naut")
                            .build()))
            .put("glosses", Json.strings(List.of("sailor")))
            .put("tags", Json.strings(List.of("core")))
            .build();

    /** The seeded stores and a router over the lexicon routes. */
    private record Built(Stores stores, Router handle) {}

    private static Built build() {
        Stores stores = seededStores();
        Harness harness = testHarness();
        LexiconService lexicon = new LexiconService(stores, harness.bus(), harness.clock());
        return new Built(stores, Router.of(LexiconRoutes.routes(new LexiconRouteDeps(DIRECTORY, lexicon))));
    }

    private static HttpRequest create(String token, Json body) {
        return makeRequest(Method.POST, "/languages/latin/lexemes", token(token).withBody(body));
    }

    @Nested
    class ListingLexemes {

        @Test
        void listsEverythingInALanguage() {
            Router handle = build().handle();
            HttpResponse response =
                    handle.handle(makeRequest(Method.GET, "/languages/latin/lexemes", token("tok-student")));
            assertThat(response.status()).isEqualTo(200);
            assertThat(items(response.body())).hasSize(4);
        }

        @Test
        void searchesWhenATermIsGiven() {
            Router handle = build().handle();
            HttpResponse response = handle.handle(makeRequest(
                    Method.GET, "/languages/latin/lexemes", token("tok-student").withQuery(Map.of("q", "field"))));
            assertThat(items(response.body())).hasSize(1);
        }

        @Test
        void letsALearnerRead() {
            Router handle = build().handle();
            HttpResponse response =
                    handle.handle(makeRequest(Method.GET, "/lexemes/" + PUELLA.id(), token("tok-student")));
            assertThat(textAt(response.body(), "lemma")).isEqualTo("puella");
            assertThat(textAt(response.body(), "citation")).isEqualTo("puella");
        }

        @Test
        void refusesAnIdNobodyStored() {
            Router handle = build().handle();
            HttpRequest request = makeRequest(Method.GET, "/lexemes/lex_zzzzzz", token("tok-student"));
            assertThatThrownBy(() -> handle.handle(request)).isInstanceOf(NotFoundException.class);
        }

        @Test
        void refusesARequestWithNoToken() {
            Router handle = build().handle();
            assertThatThrownBy(() -> handle.handle(makeRequest(Method.GET, "/languages/latin/lexemes")))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Nested
    class CreatingALexeme {

        @Test
        void storesOneAndAnswers201() {
            Router handle = build().handle();
            HttpResponse response = handle.handle(create("tok-teacher", NAUTA));
            assertThat(response.status()).isEqualTo(201);
            assertThat(textAt(response.body(), "lemma")).isEqualTo("nauta");
            assertThat(textAt(response.body(), "classKey")).isEqualTo("first-declension");
        }

        @Test
        void readsTheInherentFeaturesOffTheBody() {
            Router handle = build().handle();
            HttpResponse response = handle.handle(create("tok-teacher", NAUTA.with("inherent", JsonObject.EMPTY)));
            assertThat(textAt(response.body(), "inherent")).isEqualTo("");
        }

        @Test
        void refusesALearnerAtTheRouteBeforeTheServiceIsReached() {
            Built built = build();
            HttpRequest request = create("tok-student", NAUTA);
            assertThatThrownBy(() -> built.handle().handle(request)).isInstanceOf(ForbiddenException.class);
            assertThat(built.stores().lexemes().listByLanguage("latin")).hasSize(4);
        }

        @Test
        void refusesABodyWithNoStems() {
            Router handle = build().handle();
            HttpRequest request = create("tok-teacher", NAUTA.with("stems", JsonArray.EMPTY));
            assertThatThrownBy(() -> handle.handle(request))
                    .hasMessageContaining("body.stems must hold at least one stem");
        }

        @Test
        void namesTheBadStemRow() {
            Router handle = build().handle();
            HttpRequest request = create(
                    "tok-teacher",
                    NAUTA.with(
                            "stems",
                            Json.array(Json.object().put("role", "base").build())));
            assertThatThrownBy(() -> handle.handle(request)).hasMessageContaining("body.stems.0.form is not a string");
        }

        @Test
        void refusesAnInherentValueThatIsNotAString() {
            Router handle = build().handle();
            HttpRequest request = create(
                    "tok-teacher",
                    NAUTA.with("inherent", Json.object().put("gender", 7).build()));
            assertThatThrownBy(() -> handle.handle(request))
                    .hasMessageContaining("body.inherent.gender is not a string");
        }

        @Test
        void refusesABodyThatIsNotAnObject() {
            Router handle = build().handle();
            HttpRequest request = create("tok-teacher", Json.of("nope"));
            assertThatThrownBy(() -> handle.handle(request)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class RemovingALexeme {

        @Test
        void removesOneAndAnswers204() {
            Built built = build();
            HttpResponse response =
                    built.handle().handle(makeRequest(Method.DELETE, "/lexemes/" + PUELLA.id(), token("tok-teacher")));
            assertThat(response.status()).isEqualTo(204);
            assertThat(built.stores().lexemes().listByLanguage("latin")).hasSize(3);
        }

        @Test
        void refusesALearner() {
            Router handle = build().handle();
            HttpRequest request = makeRequest(Method.DELETE, "/lexemes/" + PUELLA.id(), token("tok-student"));
            assertThatThrownBy(() -> handle.handle(request)).isInstanceOf(ForbiddenException.class);
        }

        @Test
        void refusesAnIdNobodyStored() {
            Router handle = build().handle();
            HttpRequest request = makeRequest(Method.DELETE, "/lexemes/lex_zzzzzz", token("tok-teacher"));
            assertThatThrownBy(() -> handle.handle(request)).isInstanceOf(NotFoundException.class);
        }
    }
}
