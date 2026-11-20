package io.github.rkaushikdev.stemwright.infra;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.domain.errors.ErrorDetail;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.mastery.Attempt;
import io.github.rkaushikdev.stemwright.domain.mastery.AttemptInput;
import io.github.rkaushikdev.stemwright.domain.mastery.CellStrength;
import io.github.rkaushikdev.stemwright.domain.ortho.MatchVerdict;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import io.github.rkaushikdev.stemwright.infra.json.Json;
import io.github.rkaushikdev.stemwright.infra.json.JsonObject;
import io.github.rkaushikdev.stemwright.support.Problems;
import org.junit.jupiter.api.Test;

/**
 * A stored sequence or count is a json number, and the records hold them as {@code int}. A number an
 * {@code int} cannot carry is refused where the original refused it, with the original's words.
 */
class RecordsWholeNumberTest {

    private static final JsonObject ATTEMPT = Records.encodeAttempt(Attempt.of(AttemptInput.builder()
            .learnerId("lrn_one")
            .sessionId("sess_aaaaaa")
            .cellId("cell_aaaaaa")
            .deckKey("first-year")
            .on(CivilDates.parseCivilDate("2025-03-03"))
            .sequence(1)
            .given("puellae")
            .expected("puellae")
            .verdict(MatchVerdict.EXACT)
            .toleranceLevel("standard")
            .revisionId("rev_aaaaaa")
            .build()));

    @Test
    void restatesAFractionalSequenceAsTheAttemptRefusesIt() {
        ValidationException failure =
                Problems.validationFailureOf(() -> Records.decodeAttempt(ATTEMPT.with("sequence", Json.of(1.5))));
        assertThat(failure).hasMessage("attempt is not usable");
        assertThat(failure.details())
                .containsExactly(new ErrorDetail("sequence", "must be a positive whole number: 1.5"));
    }

    @Test
    void reportsAFractionalSequenceAmongTheOtherProblems() {
        JsonObject row = ATTEMPT.with("sequence", Json.of(2.5)).with("expected", Json.of(" "));
        assertThat(Problems.detailsOf(() -> Records.decodeAttempt(row)))
                .containsExactly(
                        new ErrorDetail("sequence", "must be a positive whole number: 2.5"),
                        new ErrorDetail("expected", "must not be blank"));
    }

    @Test
    void refusesASequenceNoIntCanHold() {
        assertThat(Problems.detailsOf(() -> Records.decodeAttempt(ATTEMPT.with("sequence", Json.of(3e9)))))
                .containsExactly(new ErrorDetail("sequence", "must be at most 2147483647: 3000000000"));
    }

    @Test
    void refusesACountNoIntCanHold() {
        JsonObject row = Records.encodeStrength(CellStrength.fresh("cell_a")).with("streak", Json.of(3e9));
        assertThat(Problems.problemMessages(() -> Records.decodeStrength(row)))
                .containsExactly("strength.streak must be at most 2147483647: 3000000000");
    }
}
