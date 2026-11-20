package io.github.rkaushikdev.stemwright.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.errors.StemwrightException;
import io.github.rkaushikdev.stemwright.infra.json.Json;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pins the percent decoding to {@code decodeURIComponent}, which the original decoded every path
 * capture and query string with, and which {@link java.net.URLDecoder} is not.
 */
class UriComponentTest {

    @Nested
    class Decode {

        @Test
        void decodesEveryEscapeTheReservedCharactersIncluded() {
            assertThat(UriComponent.decode("a%2Fb%26c%3Dd%25e%20f")).isEqualTo("a/b&c=d%e f");
            assertThat(UriComponent.decode("%2f%2F")).isEqualTo("//");
        }

        @Test
        void leavesAPlusSignAsAPlusSign() {
            assertThat(UriComponent.decode("puella+mea")).isEqualTo("puella+mea");
        }

        @Test
        void decodesUtf8SequencesOfEveryLength() {
            assertThat(UriComponent.decode("%C3%A9")).isEqualTo("é");
            assertThat(UriComponent.decode("%CE%BB%CF%8D%CF%89")).isEqualTo("λύω");
            assertThat(UriComponent.decode("%E1%BD%BB")).isEqualTo("ύ");
            assertThat(UriComponent.decode("%F0%9F%98%80")).isEqualTo("😀");
        }

        @Test
        void passesEverythingThatIsNotAnEscapeThroughUntouched() {
            assertThat(UriComponent.decode("λύω\ud800")).isEqualTo("λύω\ud800");
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "%",
                    "%2",
                    "a%zz",
                    "%G0",
                    "%C3",
                    "%C3%2",
                    "%C3A9",
                    "%80",
                    "%C3%28",
                    "%C0%AF",
                    "%E0%80%80",
                    "%ED%A0%80",
                    "%F4%90%80%80",
                    "%F8%80%80%80%80",
                    "%FF"
                })
        void refusesABrokenEscapeOrMalformedUtf8(String encoded) {
            assertThatThrownBy(() -> UriComponent.decode(encoded))
                    .isInstanceOf(IllegalArgumentException.class)
                    .isNotInstanceOf(StemwrightException.class)
                    .hasMessageContaining("malformed percent encoding");
        }
    }

    @Nested
    class InTheRouter {

        @Test
        void readsAPlusSignAsASpaceOnlyInAQueryValue() {
            assertThat(Router.parseQuery("?a+b=c+d&e=%2B")).isEqualTo(Map.of("a+b", "c d", "e", "+"));
        }

        @Test
        void keepsTheLastValueOfANameGivenTwiceInItsFirstPlace() {
            assertThat(Router.parseQuery("?a=1&b=2&a=3&&c").keySet()).containsExactly("a", "b", "c");
            assertThat(Router.parseQuery("?a=1&b=2&a=3")).containsEntry("a", "3");
        }

        @Test
        void answersABrokenEscapeInAPathAsAnUnexpectedError() {
            List<RuntimeException> reported = new ArrayList<>();
            Handler handler = Problem.withProblems(
                    Router.of(List.of(
                            Route.of(Method.GET, "/lexemes/:lemma", request -> HttpResponse.json(200, Json.NULL)))),
                    reported::add);
            HttpResponse response = handler.handle(HttpRequest.of(Method.GET, "/lexemes/%E0%A4%A"));
            assertThat(response.status()).isEqualTo(500);
            assertThat(response.body().get("code")).contains(Json.of("INTERNAL"));
            assertThat(reported).singleElement().isInstanceOf(IllegalArgumentException.class);
        }
    }
}
