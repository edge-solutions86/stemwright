package io.github.rkaushikdev.stemwright.domain.errors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.errors.StemwrightException.WireError;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ErrorsTest {

    @Nested
    class StemwrightExceptionBasics {

        @Test
        void keepsTheCodeItWasBuiltWith() {
            StemwrightException error = new StemwrightException(ErrorCode.VALIDATION, "bad input");
            assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION);
            assertThat(error.getMessage()).isEqualTo("bad input");
        }

        @Test
        void namesItselfAfterTheConcreteSubclass() {
            assertThat(new ValidationException("x").toString()).startsWith(ValidationException.class.getName());
            assertThat(new ConflictException("x").toString()).startsWith(ConflictException.class.getName());
            assertThat(new StemwrightException(ErrorCode.POLICY, "x").toString())
                    .startsWith(StemwrightException.class.getName());
        }

        @Test
        void defaultsDetailsToAnEmptyListRatherThanUndefined() {
            assertThat(new ValidationException("x").details()).isEmpty();
        }

        @Test
        void carriesACauseWhenOneIsGiven() {
            RuntimeException cause = new IllegalStateException("underlying");
            IntegrityException error = new IntegrityException("journal is torn", cause);
            assertThat(error.getCause()).isSameAs(cause);
        }

        @Test
        void leavesCauseAbsentWhenNoneIsGiven() {
            assertThat(new IntegrityException("torn").getCause()).isNull();
        }

        @Test
        void serialisesToAWireSafeShape() {
            ValidationException error = new ValidationException(
                    "two problems",
                    List.of(new ErrorDetail("lemma", "is empty"), new ErrorDetail("class", "is unknown")));
            assertThat(error.toWire())
                    .isEqualTo(new WireError(
                            ErrorCode.VALIDATION,
                            "two problems",
                            List.of(new ErrorDetail("lemma", "is empty"), new ErrorDetail("class", "is unknown"))));
        }
    }

    @Nested
    class ValidationExceptionField {

        @Test
        void buildsASingleDetailFromThePathAndMessage() {
            ValidationException error = ValidationException.field("bundle.case", "is not in the schema");
            assertThat(error.getMessage()).isEqualTo("bundle.case is not in the schema");
            assertThat(error.details()).containsExactly(new ErrorDetail("bundle.case", "is not in the schema"));
        }
    }

    @Nested
    class NotFoundExceptionBasics {

        @Test
        void readsTheKindAndTheIdBack() {
            NotFoundException error = new NotFoundException("lexeme", "lex_7");
            assertThat(error.kind()).isEqualTo("lexeme");
            assertThat(error.id()).isEqualTo("lex_7");
            assertThat(error.getMessage()).isEqualTo("lexeme lex_7 was not found");
            assertThat(error.code()).isEqualTo(ErrorCode.NOT_FOUND);
        }
    }

    @Nested
    class PolicyExceptionBasics {

        @Test
        void namesTheRuleThatRefused() {
            PolicyException error = new PolicyException("daily.new.budget", "no new cells left today");
            assertThat(error.rule()).isEqualTo("daily.new.budget");
            assertThat(error.code()).isEqualTo(ErrorCode.POLICY);
        }
    }

    @Nested
    class ErrorPredicates {

        @Test
        void recognisesItsOwnErrors() {
            assertThat(StemwrightException.isStemwrightError(new ForbiddenException("no")))
                    .isTrue();
            assertThat(StemwrightException.isStemwrightError(new IllegalStateException("no")))
                    .isFalse();
            assertThat(StemwrightException.isStemwrightError(null)).isFalse();
            assertThat(StemwrightException.isStemwrightError("FORBIDDEN")).isFalse();
        }

        @Test
        void readsACodeOffAnythingThatHasOne() {
            assertThat(StemwrightException.errorCodeOf(new UnsupportedException("later")))
                    .contains(ErrorCode.UNSUPPORTED);
            assertThat(StemwrightException.errorCodeOf(new IllegalArgumentException("no")))
                    .isEmpty();
        }
    }

    @Nested
    class ProblemCollectorBasics {

        @Test
        void staysQuietWhenNothingWasAdded() {
            ProblemCollector collector = new ProblemCollector();
            assertThat(collector.size()).isZero();
            assertThatCode(() -> collector.throwIfAny("bad row")).doesNotThrowAnyException();
        }

        @Test
        void throwsOnceWithEveryProblemItGathered() {
            ProblemCollector collector = new ProblemCollector();
            collector.add("lemma", "is empty");
            collector.addIf(true, "pos", "is unknown");
            collector.addIf(false, "gloss", "never fires");

            assertThat(collector.size()).isEqualTo(2);
            assertThatThrownBy(() -> collector.throwIfAny("row 4 is not usable"))
                    .isInstanceOfSatisfying(ValidationException.class, problem -> {
                        assertThat(problem.getMessage()).isEqualTo("row 4 is not usable");
                        assertThat(problem.details())
                                .extracting(ErrorDetail::path)
                                .containsExactly("lemma", "pos");
                    });
        }

        @Test
        void handsBackACopySoCallersCannotGrowTheList() {
            ProblemCollector collector = new ProblemCollector();
            collector.add("lemma", "is empty");
            List<ErrorDetail> first = collector.problems();
            first.add(new ErrorDetail("sneaky", "added from outside"));
            assertThat(collector.size()).isEqualTo(1);
        }
    }

    @Nested
    class ProblemCollectorAbsorb {

        @Test
        void returnsTheValueWhenNothingWasThrown() {
            ProblemCollector collector = new ProblemCollector();
            assertThat(collector.absorb("inherent", () -> 42)).contains(42);
            assertThat(collector.size()).isZero();
        }

        @Test
        void nestsTheDetailsOfAValidationFailureUnderThePath() {
            ProblemCollector collector = new ProblemCollector();
            Optional<Object> result = collector.absorb("inherent", () -> {
                throw new ValidationException(
                        "two problems",
                        List.of(new ErrorDetail("case", "is unknown"), new ErrorDetail("number", "is unknown")));
            });
            assertThat(result).isEmpty();
            assertThat(collector.problems())
                    .containsExactly(
                            new ErrorDetail("inherent.case", "is unknown"),
                            new ErrorDetail("inherent.number", "is unknown"));
        }

        @Test
        void recordsOneProblemForAFailureThatIsNotAValidationError() {
            ProblemCollector collector = new ProblemCollector();
            collector.absorb("inherent", () -> {
                throw new IllegalStateException("cannot read the schema");
            });
            assertThat(collector.problems()).containsExactly(new ErrorDetail("inherent", "cannot read the schema"));
        }

        @Test
        void recordsAValidationErrorCarryingNoDetailsAsOneProblem() {
            ProblemCollector collector = new ProblemCollector();
            collector.absorb("inherent", () -> {
                throw new ValidationException("nothing more to say");
            });
            assertThat(collector.problems()).containsExactly(new ErrorDetail("inherent", "nothing more to say"));
        }
    }

    @Nested
    class ProblemCollectorAbsorbFlat {

        @Test
        void keepsTheCallerPathAndFoldsTheDetailIntoTheMessage() {
            ProblemCollector collector = new ProblemCollector();
            collector.absorbFlat("slots.0", () -> {
                throw ValidationException.field("bundle", "is not a feature value");
            });
            assertThat(collector.problems())
                    .containsExactly(new ErrorDetail("slots.0", "bundle is not a feature value"));
        }

        @Test
        void returnsTheValueWhenNothingWasThrown() {
            ProblemCollector collector = new ProblemCollector();
            assertThat(collector.absorbFlat("slots.0", () -> "kept")).contains("kept");
        }

        @Test
        void recordsOneProblemForAnythingElseThatWasThrown() {
            ProblemCollector collector = new ProblemCollector();
            collector.absorbFlat("slots.0", () -> {
                throw new IndexOutOfBoundsException("out of range");
            });
            assertThat(collector.problems()).containsExactly(new ErrorDetail("slots.0", "out of range"));
        }
    }
}
