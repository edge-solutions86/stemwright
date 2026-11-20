package io.github.rkaushikdev.stemwright.app;

import static io.github.rkaushikdev.stemwright.support.Problems.problemMessages;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.domain.drill.DrillPolicyPatch;
import io.github.rkaushikdev.stemwright.infra.json.Json;
import io.github.rkaushikdev.stemwright.infra.json.JsonObject;
import org.junit.jupiter.api.Test;

/**
 * The policy block's counts are ints in Java and doubles in json. These pin down that a count an
 * int cannot hold is refused exactly as the original policy refused it, in its words and at its
 * place among the other problems, and that the block keeps telling a missing field from a {@code
 * null} one.
 */
class ConfigPolicyCountsTest {

    private static Config withPolicy(JsonObject.Builder policy) {
        return Config.read(Json.object().put("policy", policy.build()).build());
    }

    @Test
    void refusesAFractionalCountInThePolicysWords() {
        assertThat(problemMessages(() -> withPolicy(Json.object().put("newPerDay", 2.5))))
                .containsExactly("policy newPerDay must be a whole number: 2.5");
    }

    @Test
    void keepsThePolicysOrderWhenAFractionSitsBesideAnotherRefusal() {
        assertThat(problemMessages(
                        () -> withPolicy(Json.object().put("newPerDay", -1).put("reviewsPerDay", 2.5))))
                .containsExactly(
                        "policy newPerDay must not be negative: -1",
                        "policy reviewsPerDay must be a whole number: 2.5");
        assertThat(problemMessages(() -> withPolicy(Json.object()
                        .put("newPerDay", 0)
                        .put("reviewsPerDay", 0)
                        .put("perLexemeCap", 0.5))))
                .containsExactly(
                        "policy perLexemeCap must be a whole number: 0.5",
                        "policy newPerDay is zero and so is reviewsPerDay, which asks for no drilling at all");
    }

    @Test
    void reportsAFieldOfTheWrongTypeBeforeWhatThePolicyRefuses() {
        assertThat(problemMessages(
                        () -> withPolicy(Json.object().put("newPerDay", 2.5).put("reviewsPerDay", "lots"))))
                .containsExactly(
                        "policy.reviewsPerDay is not a number", "policy newPerDay must be a whole number: 2.5");
    }

    @Test
    void namesTheNumberWrittenWhenAnIntCannotHoldIt() {
        assertThat(problemMessages(() -> withPolicy(Json.object().put("newPerDay", 3e9))))
                .containsExactly("policy newPerDay must be at most 500: 3000000000");
        assertThat(problemMessages(() -> withPolicy(Json.object().put("rowSpacing", -3e9))))
                .containsExactly("policy rowSpacing must not be negative: -3000000000");
    }

    @Test
    void printsACountThatIsNotFiniteAsTheOriginalDid() {
        assertThat(problemMessages(() -> withPolicy(Json.object().put("perLexemeCap", Double.NaN))))
                .containsExactly("policy perLexemeCap must be a whole number: NaN");
        assertThat(problemMessages(() -> withPolicy(Json.object().put("rowSpacing", Double.NEGATIVE_INFINITY))))
                .containsExactly("policy rowSpacing must be a whole number: -Infinity");
    }

    @Test
    void refusesABodyLimitPastWhatAnIntHolds() {
        assertThat(problemMessages(
                        () -> Config.read(Json.object().put("bodyLimit", 3e9).build())))
                .containsExactly("bodyLimit must be a whole number above zero: 3000000000");
    }

    @Test
    void readsANullFieldAsTheWrongTypeAndANullBlockAsNoBlock() {
        assertThat(problemMessages(() -> withPolicy(Json.object().put("newPerDay", Json.NULL))))
                .containsExactly("policy.newPerDay is not a number");
        assertThat(Config.read(Json.object().put("policy", Json.NULL).build()).policy())
                .isEqualTo(DrillPolicyPatch.EMPTY);
    }
}
