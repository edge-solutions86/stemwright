package io.github.rkaushikdev.stemwright.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.domain.errors.ErrorDetail;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.infra.json.Json;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The wire shape of a problem, which the original got from {@code JSON.stringify} of the problem
 * object and which a client reads field by field.
 */
class ProblemBodyTest {

    @Test
    void writesTheDetailsAsPathAndMessagePairsAfterTheCodeAndMessage() {
        ValidationException error = new ValidationException(
                "two problems", List.of(new ErrorDetail("a", "is wrong"), new ErrorDetail("b", "is missing")));
        HttpResponse response = Problem.responseFor(error);
        assertThat(response.status()).isEqualTo(400);
        assertThat(Json.stringify(response.body()))
                .isEqualTo("{\"code\":\"VALIDATION\",\"message\":\"two problems\",\"details\":"
                        + "[{\"path\":\"a\",\"message\":\"is wrong\"},{\"path\":\"b\",\"message\":\"is missing\"}]}");
    }

    @Test
    void writesOnlyTheCodeAndTheFixedMessageForAnUnexpectedFailure() {
        HttpResponse response = Problem.responseFor(new IllegalStateException("cannot read /etc/secret"));
        assertThat(Json.stringify(response.body()))
                .isEqualTo("{\"code\":\"INTERNAL\",\"message\":\"the request could not be completed\"}");
    }
}
