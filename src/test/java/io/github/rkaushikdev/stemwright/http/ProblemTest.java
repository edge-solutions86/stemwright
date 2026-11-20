package io.github.rkaushikdev.stemwright.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.domain.errors.ConflictException;
import io.github.rkaushikdev.stemwright.domain.errors.ErrorCode;
import io.github.rkaushikdev.stemwright.domain.errors.ErrorDetail;
import io.github.rkaushikdev.stemwright.domain.errors.ForbiddenException;
import io.github.rkaushikdev.stemwright.domain.errors.IntegrityException;
import io.github.rkaushikdev.stemwright.domain.errors.NotFoundException;
import io.github.rkaushikdev.stemwright.domain.errors.PolicyException;
import io.github.rkaushikdev.stemwright.domain.errors.UnsupportedException;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.infra.json.Json;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProblemTest {

    private static final HttpRequest REQUEST = HttpRequest.of(Method.GET, "/decks");

    private static Handler throwing(RuntimeException error) {
        return request -> {
            throw error;
        };
    }

    @Nested
    class StatusFor {

        @Test
        void mapsEveryCodeToAStatus() {
            assertThat(Problem.statusFor(ErrorCode.VALIDATION)).isEqualTo(400);
            assertThat(Problem.statusFor(ErrorCode.FORBIDDEN)).isEqualTo(403);
            assertThat(Problem.statusFor(ErrorCode.NOT_FOUND)).isEqualTo(404);
            assertThat(Problem.statusFor(ErrorCode.CONFLICT)).isEqualTo(409);
            assertThat(Problem.statusFor(ErrorCode.POLICY)).isEqualTo(422);
            assertThat(Problem.statusFor(ErrorCode.INTEGRITY)).isEqualTo(500);
            assertThat(Problem.statusFor(ErrorCode.UNSUPPORTED)).isEqualTo(501);
        }
    }

    @Nested
    class ToProblem {

        @Test
        void keepsTheMessageOfAValidationFailure() {
            Problem.Outcome outcome = Problem.forError(ValidationException.field("lemma", "must not be blank"));
            assertThat(outcome.status()).isEqualTo(400);
            assertThat(outcome.problem().code()).isEqualTo("VALIDATION");
            assertThat(outcome.problem().message()).isEqualTo("lemma must not be blank");
        }

        @Test
        void carriesTheDetailsWhenThereAreAny() {
            ValidationException error = new ValidationException(
                    "two problems", List.of(new ErrorDetail("a", "is wrong"), new ErrorDetail("b", "is wrong")));
            assertThat(Problem.forError(error).problem().details()).hasSize(2);
        }

        @Test
        void leavesDetailsOutWhenThereAreNone() {
            Problem problem = Problem.forError(new ConflictException("taken")).problem();
            assertThat(problem.details()).isEmpty();
            assertThat(problem.toJson().has("details")).isFalse();
        }

        @Test
        void mapsTheOtherDomainErrors() {
            assertThat(Problem.forError(new NotFoundException("deck", "all")).status())
                    .isEqualTo(404);
            assertThat(Problem.forError(new ForbiddenException("no")).status()).isEqualTo(403);
            assertThat(Problem.forError(new PolicyException("budget", "none left"))
                            .status())
                    .isEqualTo(422);
            assertThat(Problem.forError(new UnsupportedException("later")).status())
                    .isEqualTo(501);
        }

        @Test
        void hidesTheMessageOfAnIntegrityFailure() {
            Problem.Outcome outcome = Problem.forError(new IntegrityException("journal line 7 is torn"));
            assertThat(outcome.status()).isEqualTo(500);
            assertThat(outcome.problem().message()).isEqualTo(Problem.INTERNAL_MESSAGE);
            assertThat(outcome.problem().message()).doesNotContain("journal");
        }

        @Test
        void hidesEverythingAboutAnErrorTheDomainDidNotRaise() {
            Problem.Outcome outcome = Problem.forError(new IllegalStateException("cannot read /etc/secret"));
            assertThat(outcome.status()).isEqualTo(500);
            assertThat(outcome.problem().code()).isEqualTo("INTERNAL");
            assertThat(outcome.problem().message()).isEqualTo(Problem.INTERNAL_MESSAGE);
        }

        @Test
        void hidesAThrownValueThatIsNotAnErrorAtAll() {
            assertThat(Problem.forError(new Throwable("a string")).problem().code())
                    .isEqualTo("INTERNAL");
        }
    }

    @Nested
    class ProblemResponse {

        @Test
        void buildsAResponseFromAnError() {
            HttpResponse response = Problem.responseFor(new NotFoundException("deck", "all"));
            assertThat(response.status()).isEqualTo(404);
            assertThat(response.body().get("code")).contains(Json.of("NOT_FOUND"));
        }
    }

    @Nested
    class WithProblems {

        @Test
        void passesASuccessfulResponseStraightThrough() {
            Handler handler = Problem.withProblems(request ->
                    HttpResponse.json(200, Json.object().put("ok", true).build()));
            assertThat(handler.handle(REQUEST).status()).isEqualTo(200);
        }

        @Test
        void turnsAThrownDomainErrorIntoAResponse() {
            Handler handler = Problem.withProblems(throwing(new ForbiddenException("no")));
            assertThat(handler.handle(REQUEST).status()).isEqualTo(403);
        }

        @Test
        void turnsAnythingElseIntoABare500() {
            Handler handler = Problem.withProblems(throwing(new IllegalStateException("boom")));
            HttpResponse response = handler.handle(REQUEST);
            assertThat(response.status()).isEqualTo(500);
            assertThat(response.body().get("code")).contains(Json.of("INTERNAL"));
        }

        @Test
        void reportsAnUnexpectedErrorToTheOperator() {
            List<Throwable> seen = new ArrayList<>();
            Handler handler = Problem.withProblems(throwing(new IllegalStateException("boom")), seen::add);
            handler.handle(REQUEST);
            assertThat(seen).hasSize(1);
            assertThat(seen.get(0).getMessage()).isEqualTo("boom");
        }

        @Test
        void doesNotReportAnOrdinaryDomainFailure() {
            List<Throwable> seen = new ArrayList<>();
            Handler handler = Problem.withProblems(throwing(new NotFoundException("deck", "all")), seen::add);
            handler.handle(REQUEST);
            assertThat(seen).isEmpty();
        }
    }

    @Nested
    class StatusPredicates {

        @Test
        void splitsClientFromServerProblems() {
            assertThat(Problem.isClientProblem(404)).isTrue();
            assertThat(Problem.isClientProblem(500)).isFalse();
            assertThat(Problem.isServerProblem(500)).isTrue();
            assertThat(Problem.isServerProblem(404)).isFalse();
            assertThat(Problem.isClientProblem(200)).isFalse();
        }
    }

    @Nested
    class DescribeProblem {

        @Test
        void readsAProblemOut() {
            assertThat(Problem.of("NOT_FOUND", "deck all was not found").describe(404))
                    .isEqualTo("404 NOT_FOUND: deck all was not found");
        }

        @Test
        void countsTheDetailsWhenThereAreAny() {
            Problem problem = new Problem(
                    "VALIDATION", "two problems", List.of(new ErrorDetail("a", "x"), new ErrorDetail("b", "y")));
            assertThat(problem.describe(400)).isEqualTo("400 VALIDATION: two problems (2 details)");
        }
    }
}
