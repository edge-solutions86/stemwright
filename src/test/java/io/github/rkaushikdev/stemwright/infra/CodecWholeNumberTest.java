package io.github.rkaushikdev.stemwright.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.infra.json.Json;
import io.github.rkaushikdev.stemwright.infra.json.JsonObject;
import io.github.rkaushikdev.stemwright.support.Problems;
import org.junit.jupiter.api.Test;

/**
 * The one reader the original did not have. A Java count or sequence cannot carry a fraction the
 * way a JavaScript number did, so {@link Codec#readWholeNumber} refuses it where it is read.
 */
class CodecWholeNumberTest {

    private static JsonObject count(Json value) {
        return Json.object().put("count", value).build();
    }

    @Test
    void readsAWholeNumber() {
        assertThat(Codec.readWholeNumber(count(Json.of(3)), "lexeme", "count")).isEqualTo(3L);
        assertThat(Codec.readWholeNumber(count(Json.of(-2.0)), "lexeme", "count"))
                .isEqualTo(-2L);
        assertThat(Codec.readWholeNumber(count(Json.of(9_007_199_254_740_991L)), "lexeme", "count"))
                .isEqualTo(9_007_199_254_740_991L);
    }

    @Test
    void refusesAFractionUnderThePathOfTheField() {
        assertThat(Problems.problemMessages(() -> Codec.readWholeNumber(count(Json.of(2.5)), "lexeme", "count")))
                .containsExactly("lexeme.count must be a whole number: 2.5");
    }

    @Test
    void refusesANumberTooLargeForADoubleToCountExactly() {
        assertThat(Problems.problemMessages(() -> Codec.readWholeNumber(count(Json.of(-1e20)), "lexeme", "count")))
                .containsExactly("lexeme.count must be between -9007199254740991 and 9007199254740991: "
                        + "-100000000000000000000");
    }

    @Test
    void isHowTheJournalReadsASequence() {
        String line = "{\"seq\":1.5,\"at\":\"2025-03-03T09:00:00.000Z\",\"kind\":\"lexeme.put\",\"payload\":{}}";
        assertThat(Problems.problemMessages(() -> JournalEntry.decode(line, 1)))
                .containsExactly("journal.1.seq must be a whole number: 1.5");
    }

    @Test
    void refusesWhatReadNumberRefuses() {
        assertThatThrownBy(() -> Codec.readWholeNumber(count(Json.of("3")), "lexeme", "count"))
                .hasMessage("lexeme.count is not a finite number");
        assertThatThrownBy(() -> Codec.readWholeNumber(JsonObject.EMPTY, "lexeme", "count"))
                .hasMessage("lexeme.count is not a finite number");
    }
}
