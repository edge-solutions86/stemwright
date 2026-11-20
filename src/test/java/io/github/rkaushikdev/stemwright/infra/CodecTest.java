package io.github.rkaushikdev.stemwright.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.features.FeatureBundle;
import io.github.rkaushikdev.stemwright.domain.features.Selector;
import io.github.rkaushikdev.stemwright.domain.features.SelectorClause;
import io.github.rkaushikdev.stemwright.domain.lexicon.StemInput;
import io.github.rkaushikdev.stemwright.domain.lexicon.StemSet;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import io.github.rkaushikdev.stemwright.domain.time.DayRange;
import io.github.rkaushikdev.stemwright.infra.json.Json;
import io.github.rkaushikdev.stemwright.infra.json.JsonArray;
import io.github.rkaushikdev.stemwright.infra.json.JsonObject;
import io.github.rkaushikdev.stemwright.support.Profiles;
import io.github.rkaushikdev.stemwright.support.TestMaps;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CodecTest {

    private static final JsonObject ROW = Json.object()
            .put("name", "puella")
            .put("count", 3)
            .put("flag", true)
            .put("when", "2025-03-03")
            .put("tags", Json.strings(List.of("core", "chapter-one")))
            .put("nothing", Json.NULL)
            .build();

    private static LocalDate d(String value) {
        return CivilDates.parseCivilDate(value);
    }

    private static JsonObject single(String key, Json value) {
        return Json.object().put(key, value).build();
    }

    @Nested
    class IsObjectAndRequireObject {

        @Test
        void recognisesAPlainObjectAndNothingElse() {
            assertThat(Codec.isObject(JsonObject.EMPTY)).isTrue();
            assertThat(Codec.isObject(JsonArray.EMPTY)).isFalse();
            assertThat(Codec.isObject(Json.NULL)).isFalse();
            assertThat(Codec.isObject(Json.of("x"))).isFalse();
        }

        @Test
        void namesThePathWhenItRefuses() {
            assertThatThrownBy(() -> Codec.requireObject("lexeme", JsonArray.EMPTY))
                    .hasMessageContaining("lexeme is not an object");
            assertThat(Codec.requireObject("lexeme", ROW)).isSameAs(ROW);
        }
    }

    @Nested
    class ReadString {

        @Test
        void readsAString() {
            assertThat(Codec.readString(ROW, "lexeme", "name")).isEqualTo("puella");
        }

        @Test
        void refusesAMissingOrWronglyTypedField() {
            assertThatThrownBy(() -> Codec.readString(ROW, "lexeme", "count"))
                    .hasMessageContaining("lexeme.count is not a string");
            assertThatThrownBy(() -> Codec.readString(ROW, "lexeme", "absent")).isInstanceOf(ValidationException.class);
        }

        @Test
        void readsAnOptionalStringAsEmptyWhenAbsentOrNull() {
            assertThat(Codec.readOptionalString(ROW, "lexeme", "absent")).isEmpty();
            assertThat(Codec.readOptionalString(ROW, "lexeme", "nothing")).isEmpty();
            assertThat(Codec.readOptionalString(ROW, "lexeme", "name")).contains("puella");
        }

        @Test
        void stillRefusesAnOptionalFieldOfTheWrongType() {
            assertThatThrownBy(() -> Codec.readOptionalString(ROW, "lexeme", "count"))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class ReadNumberAndReadBoolean {

        @Test
        void readsAFiniteNumber() {
            assertThat(Codec.readNumber(ROW, "lexeme", "count")).isEqualTo(3);
        }

        @Test
        void refusesAStringAMissingFieldAndAnInfinity() {
            assertThatThrownBy(() -> Codec.readNumber(ROW, "lexeme", "name"))
                    .hasMessageContaining("is not a finite number");
            assertThatThrownBy(() -> Codec.readNumber(single("x", Json.of(Double.POSITIVE_INFINITY)), "lexeme", "x"))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void readsAnOptionalNumber() {
            assertThat(Codec.readOptionalNumber(ROW, "lexeme", "absent")).isEmpty();
            assertThat(Codec.readOptionalNumber(ROW, "lexeme", "count")).contains(3.0);
        }

        @Test
        void readsABooleanAndRefusesAnythingElse() {
            assertThat(Codec.readBoolean(ROW, "lexeme", "flag")).isTrue();
            assertThatThrownBy(() -> Codec.readBoolean(ROW, "lexeme", "count"))
                    .hasMessageContaining("is not a boolean");
        }
    }

    @Nested
    class ReadArrayAndReadStringArray {

        @Test
        void readsAnArray() {
            assertThat(Codec.readArray(ROW, "lexeme", "tags")).hasSize(2);
            assertThatThrownBy(() -> Codec.readArray(ROW, "lexeme", "name")).hasMessageContaining("is not an array");
        }

        @Test
        void readsAnArrayOfStringsAndNamesTheBadIndex() {
            assertThat(Codec.readStringArray(ROW, "lexeme", "tags")).containsExactly("core", "chapter-one");
            assertThatThrownBy(() -> Codec.readStringArray(
                            single("tags", Json.array(Json.of("a"), Json.of(2))), "lexeme", "tags"))
                    .hasMessageContaining("lexeme.tags.1 is not a string");
        }
    }

    @Nested
    class ReadDate {

        @Test
        void parsesAStoredDay() {
            assertThat(Codec.readDate(ROW, "lexeme", "when")).isEqualTo(d("2025-03-03"));
        }

        @Test
        void refusesADayThatIsNotReal() {
            assertThatThrownBy(() -> Codec.readDate(single("when", Json.of("2025-02-30")), "lexeme", "when"))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void readsAnOptionalDay() {
            assertThat(Codec.readOptionalDate(ROW, "lexeme", "absent")).isEmpty();
            assertThat(Codec.readOptionalDate(ROW, "lexeme", "when")).contains(d("2025-03-03"));
        }
    }

    @Nested
    class Ranges {

        @Test
        void roundTrips() {
            DayRange range = DayRange.of(d("2025-03-03"), d("2025-03-09"));
            assertThat(Codec.decodeRange(Codec.encodeRange(range), "window")).isEqualTo(range);
        }

        @Test
        void refusesARangeThatEndsBeforeItStarts() {
            JsonObject reversed = Json.object()
                    .put("from", "2025-03-09")
                    .put("to", "2025-03-03")
                    .build();
            assertThatThrownBy(() -> Codec.decodeRange(reversed, "window")).isInstanceOf(ValidationException.class);
        }

        @Test
        void refusesSomethingThatIsNotAnObject() {
            assertThatThrownBy(() -> Codec.decodeRange(Json.of("2025-03-03"), "window"))
                    .hasMessageContaining("window is not an object");
        }
    }

    @Nested
    class Bundles {

        private final FeatureBundle bundle =
                FeatureBundle.of(Profiles.LATIN_NOUN, TestMaps.ordered("case", "gen", "number", "pl"));

        @Test
        void storesABundleAsItsCanonicalKey() {
            assertThat(Codec.encodeBundle(bundle)).isEqualTo(Json.of("case=gen;number=pl"));
        }

        @Test
        void roundTripsThroughTheKey() {
            assertThat(Codec.decodeBundle(Codec.encodeBundle(bundle), "bundle").key())
                    .isEqualTo(bundle.key());
        }

        @Test
        void roundTripsTheEmptyBundle() {
            assertThat(Codec.decodeBundle(Json.of(""), "bundle").pairs()).isEmpty();
        }

        @Test
        void refusesAMalformedKey() {
            assertThatThrownBy(() -> Codec.decodeBundle(Json.of("casegen"), "bundle"))
                    .hasMessageContaining("dimension value pair");
            assertThatThrownBy(() -> Codec.decodeBundle(Json.of(42), "bundle"))
                    .hasMessageContaining("is not a bundle key");
        }
    }

    @Nested
    class Stems {

        @Test
        void encodesEveryStemWithItsRoleAndFlag() {
            StemSet stems = StemSet.of(
                    StemInput.of("base", "fer"),
                    StemInput.suppletive("perfect", "tul").withNote("unrelated root"));
            assertThat(Codec.encodeStems(stems))
                    .isEqualTo(Json.array(
                            Json.object()
                                    .put("role", "base")
                                    .put("form", "fer")
                                    .put("suppletive", false)
                                    .build(),
                            Json.object()
                                    .put("role", "perfect")
                                    .put("form", "tul")
                                    .put("suppletive", true)
                                    .put("note", "unrelated root")
                                    .build()));
        }
    }

    @Nested
    class Selectors {

        @Test
        void roundTripsThroughClauses() {
            Selector selection = Selector.parse(Profiles.LATIN_NOUN, "case=nom|gen,number!=pl");
            List<SelectorClause> decoded = Codec.decodeSelectorClauses(Codec.encodeSelector(selection), "cells");
            assertThat(decoded).hasSize(2);
            assertThat(Selector.parse(Profiles.LATIN_NOUN, selection.format()).format())
                    .isEqualTo(selection.format());
        }

        @Test
        void encodesTheEmptySelectorAsAnEmptyList() {
            assertThat(Codec.encodeSelector(Selector.parse(Profiles.LATIN_NOUN, "")))
                    .isEqualTo(JsonArray.EMPTY);
        }

        @Test
        void refusesAClauseListThatIsNotAnArray() {
            assertThatThrownBy(() -> Codec.decodeSelectorClauses(Json.of("nope"), "cells"))
                    .hasMessageContaining("is not an array of clauses");
        }

        @Test
        void namesTheBadClause() {
            JsonArray clauses = Json.array(single("dimension", Json.of("case")));
            assertThatThrownBy(() -> Codec.decodeSelectorClauses(clauses, "cells"))
                    .hasMessageContaining("cells.0.values is not an array");
        }
    }
}
