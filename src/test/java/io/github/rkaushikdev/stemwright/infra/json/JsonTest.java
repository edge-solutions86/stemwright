package io.github.rkaushikdev.stemwright.infra.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.github.rkaushikdev.stemwright.domain.errors.StemwrightException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pins the json model to RFC 8259 reading and one canonical way of writing, because the journal
 * and the http bodies are stored and compared as text and have to read back byte for byte.
 */
class JsonTest {

    private static final JsonObject LEXEME = Json.object()
            .put("lemma", "puella")
            .put("homograph", 1)
            .put("tags", Json.strings(List.of("core", "chapter-one")))
            .put(
                    "stems",
                    Json.array(Json.object()
                            .put("role", "base")
                            .put("form", "puell")
                            .build()))
            .put("gloss", Json.NULL)
            .put("ready", true)
            .put("empty", JsonArray.EMPTY)
            .put("nothing", JsonObject.EMPTY)
            .build();

    @Nested
    class Parse {

        @Test
        void readsEveryKindOfValue() {
            assertThat(Json.parse("\"puella\"")).isEqualTo(Json.of("puella"));
            assertThat(Json.parse("3")).isEqualTo(Json.of(3));
            assertThat(Json.parse("-2.5e3")).isEqualTo(Json.of(-2500.0));
            assertThat(Json.parse("true")).isEqualTo(JsonBoolean.TRUE);
            assertThat(Json.parse("false")).isEqualTo(JsonBoolean.FALSE);
            assertThat(Json.parse("null")).isSameAs(Json.NULL);
            assertThat(Json.parse("[]")).isEqualTo(JsonArray.EMPTY);
            assertThat(Json.parse("{}")).isEqualTo(JsonObject.EMPTY);
        }

        @Test
        void readsNumbersAsDoubles() {
            assertThat(Json.parse("0")).isEqualTo(Json.of(0.0));
            assertThat(Json.parse("-0")).isEqualTo(Json.of(-0.0));
            assertThat(Json.parse("0.1")).isEqualTo(Json.of(0.1));
            assertThat(Json.parse("1E2")).isEqualTo(Json.of(100));
            assertThat(Json.parse("1e-2")).isEqualTo(Json.of(0.01));
            assertThat(Json.parse("12345678901234567890")).isEqualTo(Json.of(1.2345678901234567e19));
            assertThat(Json.parse("1e400")).isEqualTo(Json.of(Double.POSITIVE_INFINITY));
        }

        @Test
        void readsNestedValuesWithSpacingAroundThem() {
            String text = " \t\r\n{ \"a\" : [ 1 , { \"b\" : null } ] , \"c\" : \"d\" } \n";
            assertThat(Json.parse(text))
                    .isEqualTo(Json.object()
                            .put(
                                    "a",
                                    Json.array(
                                            Json.of(1),
                                            Json.object().put("b", Json.NULL).build()))
                            .put("c", "d")
                            .build());
        }

        @Test
        void decodesEveryEscape() {
            assertThat(Json.parse("\"\\\" \\\\ \\/ \\b \\f \\n \\r \\t\""))
                    .isEqualTo(Json.of("\" \\ / \b \f \n \r \t"));
            assertThat(Json.parse("\"\\u00e9\\u00C9\"")).isEqualTo(Json.of("éÉ"));
            assertThat(Json.parse("\"\\ud83d\\ude00\"")).isEqualTo(Json.of("😀"));
        }

        @Test
        void keepsALoneSurrogate() {
            assertThat(Json.parse("\"a\\ud800b\"")).isEqualTo(Json.of("a\ud800b"));
        }

        @Test
        void keepsTheFirstPositionAndTheLastValueOfARepeatedKey() {
            JsonObject parsed = (JsonObject) Json.parse("{\"a\":1,\"b\":2,\"a\":3}");
            assertThat(parsed.keys()).containsExactly("a", "b");
            assertThat(parsed.get("a")).contains(Json.of(3));
        }

        @Test
        void keepsTheOrderTheKeysWereWrittenIn() {
            JsonObject parsed = (JsonObject) Json.parse("{\"z\":1,\"a\":2,\"m\":3}");
            assertThat(parsed.keys()).containsExactly("z", "a", "m");
        }

        @Test
        void readsADocumentNestedFarDeeperThanTheCallStackGoes() {
            int depth = 200_000;
            String text = "[".repeat(depth) + "]".repeat(depth);
            Json value = Json.parse(text);
            for (int level = 1; level < depth; level += 1) {
                value = ((JsonArray) value).get(0);
            }
            assertThat(value).isEqualTo(JsonArray.EMPTY);
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "",
                    " ",
                    "[1,]",
                    "{\"a\":1,}",
                    "[1 2]",
                    "{\"a\" 1}",
                    "{a:1}",
                    "{'a':1}",
                    "'a'",
                    "01",
                    "+1",
                    ".5",
                    "5.",
                    "1e",
                    "-",
                    "0x10",
                    "NaN",
                    "Infinity",
                    "tru",
                    "nul",
                    "\"a",
                    "\"\\x\"",
                    "\"\\u12\"",
                    "\"\\u12g4\"",
                    "\"tab\there\"",
                    "[1]]",
                    "{} {}",
                    "\u00a01",
                })
        void refusesTextThatIsNotJson(String text) {
            assertThatThrownBy(() -> Json.parse(text)).isInstanceOf(JsonSyntaxException.class);
        }

        @Test
        void saysWhereItStoppedAndIsNotADomainFailure() {
            JsonSyntaxException error = (JsonSyntaxException) catchSyntaxError("{\"seq\":1,");
            assertThat(error.position()).isEqualTo(9);
            assertThat(error).hasMessage("unexpected end of json input at position 9");
            assertThat(error).isNotInstanceOf(StemwrightException.class);
            assertThat(catchSyntaxError("[1,]")).hasMessage("unexpected character ']' at position 3");
        }

        private static Throwable catchSyntaxError(String text) {
            return catchThrowable(() -> Json.parse(text));
        }
    }

    @Nested
    class StringifyCompactly {

        @Test
        void writesNoSpaceAnywhere() {
            assertThat(Json.stringify(LEXEME))
                    .isEqualTo("{\"lemma\":\"puella\",\"homograph\":1,\"tags\":[\"core\",\"chapter-one\"],"
                            + "\"stems\":[{\"role\":\"base\",\"form\":\"puell\"}],\"gloss\":null,\"ready\":true,"
                            + "\"empty\":[],\"nothing\":{}}");
        }

        @Test
        void printsNumbersInTheirShortestRoundTripForm() {
            assertThat(Json.stringify(Json.array(
                            Json.of(1),
                            Json.of(2.5),
                            Json.of(-0.0),
                            Json.of(1e21),
                            Json.of(1.5e-7),
                            Json.of(0.1 + 0.2))))
                    .isEqualTo("[1,2.5,0,1e+21,1.5e-7,0.30000000000000004]");
        }

        @Test
        void printsANumberThatIsNotFiniteAsNull() {
            assertThat(Json.stringify(Json.array(
                            Json.of(Double.NaN), Json.of(Double.POSITIVE_INFINITY), Json.of(Double.NEGATIVE_INFINITY))))
                    .isEqualTo("[null,null,null]");
        }

        @Test
        void escapesTheQuoteTheBackslashAndTheShortControls() {
            assertThat(Json.stringify(Json.of("\" \\ / \b \f \n \r \t")))
                    .isEqualTo("\"\\\" \\\\ / \\b \\f \\n \\r \\t\"");
        }

        @Test
        void escapesEveryOtherControlInLowerCaseHex() {
            assertThat(Json.stringify(Json.of("\u0000\u0001\u001f\u000b")))
                    .isEqualTo("\"\\u0000\\u0001\\u001f\\u000b\"");
        }

        @Test
        void escapesALoneSurrogateAndKeepsAPair() {
            assertThat(Json.stringify(Json.of("\ud800"))).isEqualTo("\"\\ud800\"");
            assertThat(Json.stringify(Json.of("x\udc00y"))).isEqualTo("\"x\\udc00y\"");
            assertThat(Json.stringify(Json.of("\udc00\ud800"))).isEqualTo("\"\\udc00\\ud800\"");
            assertThat(Json.stringify(Json.of("😀"))).isEqualTo("\"😀\"");
        }

        @Test
        void leavesNonAsciiAndTheLineSeparatorsAlone() {
            assertThat(Json.stringify(Json.of("λύω é \u2028\u2029 \u007f"))).isEqualTo("\"λύω é \u2028\u2029 \u007f\"");
        }

        @Test
        void escapesKeysAsItEscapesValues() {
            assertThat(Json.stringify(Json.object().put("a\"b", 1).build())).isEqualTo("{\"a\\\"b\":1}");
        }
    }

    @Nested
    class StringifyIndented {

        @Test
        void putsOneEntryOnEachLineWithOneSpaceAfterTheColon() {
            assertThat(Json.stringify(LEXEME, 2))
                    .isEqualTo(String.join(
                            "\n",
                            "{",
                            "  \"lemma\": \"puella\",",
                            "  \"homograph\": 1,",
                            "  \"tags\": [",
                            "    \"core\",",
                            "    \"chapter-one\"",
                            "  ],",
                            "  \"stems\": [",
                            "    {",
                            "      \"role\": \"base\",",
                            "      \"form\": \"puell\"",
                            "    }",
                            "  ],",
                            "  \"gloss\": null,",
                            "  \"ready\": true,",
                            "  \"empty\": [],",
                            "  \"nothing\": {}",
                            "}"));
        }

        @Test
        void printsAScalarAndAnEmptyContainerOnOneLine() {
            assertThat(Json.stringify(Json.of("a"), 2)).isEqualTo("\"a\"");
            assertThat(Json.stringify(JsonArray.EMPTY, 2)).isEqualTo("[]");
            assertThat(Json.stringify(JsonObject.EMPTY, 2)).isEqualTo("{}");
        }

        @Test
        void indentsByAtMostTenSpaces() {
            assertThat(Json.stringify(Json.array(Json.of(1)), 12)).isEqualTo("[\n          1\n]");
        }

        @Test
        void printsCompactlyForAnIndentBelowOne() {
            assertThat(Json.stringify(LEXEME, 0)).isEqualTo(Json.stringify(LEXEME));
            assertThat(Json.stringify(LEXEME, -4)).isEqualTo(Json.stringify(LEXEME));
        }
    }

    @Nested
    class RoundTrips {

        @Test
        void readsBackWhatItWrote() {
            assertThat(Json.parse(Json.stringify(LEXEME))).isEqualTo(LEXEME);
            assertThat(Json.parse(Json.stringify(LEXEME, 2))).isEqualTo(LEXEME);
        }

        @Test
        void writesBackWhatItReadWhenTheTextWasCompact() {
            String text = "{\"seq\":1,\"at\":\"2025-03-03T09:00:00.000Z\",\"kind\":\"lexeme.put\","
                    + "\"payload\":{\"id\":\"lex_a\",\"n\":[1.5,-2,true,null,\"\\u0001\\ud800\"]}}";
            assertThat(Json.stringify(Json.parse(text))).isEqualTo(text);
        }

        @Test
        void roundTripsAStringOfEveryUnitBelowTheFirstSurrogate() {
            StringBuilder every = new StringBuilder();
            for (char unit = 0; unit < 0xd800; unit += 1) {
                every.append(unit);
            }
            JsonString value = Json.of(every.toString());
            assertThat(Json.parse(Json.stringify(value))).isEqualTo(value);
        }
    }

    @Nested
    class Values {

        @Test
        void comparesObjectsWithoutRegardToKeyOrder() {
            JsonObject first = Json.object().put("a", 1).put("b", 2).build();
            JsonObject second = Json.object().put("b", 2).put("a", 1).build();
            assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
            assertThat(Json.stringify(first)).isNotEqualTo(Json.stringify(second));
        }

        @Test
        void tellsZeroFromNegativeZeroAsToEqualDoes() {
            assertThat(Json.of(0.0)).isNotEqualTo(Json.of(-0.0));
            assertThat(Json.of(3)).isEqualTo(Json.of(3.0));
        }

        @Test
        void printsItselfAsCompactJson() {
            assertThat(LEXEME.get("stems").orElseThrow()).hasToString("[{\"role\":\"base\",\"form\":\"puell\"}]");
            assertThat(Json.NULL).hasToString("null");
            assertThat(Json.of(2.5)).hasToString("2.5");
            assertThat(Json.of(false)).hasToString("false");
            assertThat(Json.of("a")).hasToString("\"a\"");
        }

        @Test
        void keepsTheObjectItWasBuiltFromOutOfReach() {
            Map<String, Json> fields = new LinkedHashMap<>();
            fields.put("a", Json.of(1));
            JsonObject object = new JsonObject(fields);
            fields.put("b", Json.of(2));
            assertThat(object.keys()).containsExactly("a");
            assertThatThrownBy(() -> object.fields().put("c", Json.NULL))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void putsAnOptionalFieldOnlyWhenItIsPresent() {
            JsonObject object = Json.object()
                    .putIfPresent("note", Optional.of(Json.of("unrelated root")))
                    .putIfPresent("gloss", Optional.empty())
                    .build();
            assertThat(object)
                    .isEqualTo(Json.object().put("note", "unrelated root").build());
            assertThat(object.has("gloss")).isFalse();
        }

        @Test
        void replacesAKeyInPlace() {
            JsonObject object = Json.object().put("a", 1).put("b", 2).build();
            assertThat(Json.stringify(object.with("a", Json.of(3)))).isEqualTo("{\"a\":3,\"b\":2}");
            assertThat(Json.stringify(object.with("c", Json.NULL))).isEqualTo("{\"a\":1,\"b\":2,\"c\":null}");
            assertThat(object.size()).isEqualTo(2);
        }

        @Test
        void tellsAPresentNullFromAMissingKey() {
            assertThat(LEXEME.has("gloss")).isTrue();
            assertThat(LEXEME.get("gloss")).contains(Json.NULL);
            assertThat(LEXEME.has("note")).isFalse();
            assertThat(LEXEME.get("note")).isEmpty();
        }

        @Test
        void readsEachShapeThroughItsOwnAccessorOnly() {
            assertThat(Json.of("a").asString()).contains("a");
            assertThat(Json.of(2).asNumber()).contains(2.0);
            assertThat(Json.of(true).asBoolean()).contains(true);
            assertThat(LEXEME.asObject()).containsSame(LEXEME);
            assertThat(JsonArray.EMPTY.asArray()).containsSame(JsonArray.EMPTY);
            assertThat(Json.NULL.asString()).isEmpty();
            assertThat(Json.of("a").asNumber()).isEmpty();
            assertThat(Json.of(1).asBoolean()).isEmpty();
            assertThat(Json.of(true).asObject()).isEmpty();
            assertThat(LEXEME.asArray()).isEmpty();
            assertThat(Json.of("a").get("a")).isEmpty();
            assertThat(JsonArray.EMPTY.isEmpty()).isTrue();
            assertThat(LEXEME.isEmpty()).isFalse();
        }
    }
}
