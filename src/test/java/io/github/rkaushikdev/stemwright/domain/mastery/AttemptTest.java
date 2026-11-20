package io.github.rkaushikdev.stemwright.domain.mastery;

import static io.github.rkaushikdev.stemwright.support.Problems.problemMessages;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.stemwright.domain.IdKind;
import io.github.rkaushikdev.stemwright.domain.Identifiers;
import io.github.rkaushikdev.stemwright.domain.ortho.MatchVerdict;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AttemptTest {

    private static final AttemptInput BASE = AttemptInput.builder()
            .learnerId("lrn_one")
            .sessionId("sess_aaaaaa")
            .cellId("cell_aaaaaa")
            .deckKey("first-year")
            .on(d("2025-03-03"))
            .sequence(1)
            .given("puellae")
            .expected("puellae")
            .verdict(MatchVerdict.EXACT)
            .toleranceLevel("standard")
            .revisionId("rev_aaaaaa")
            .build();

    private static LocalDate d(String value) {
        return CivilDates.parseCivilDate(value);
    }

    private static Attempt make() {
        return Attempt.of(BASE);
    }

    /** The base attempt with some of its parts changed, as a spread over the base would. */
    private static Attempt make(UnaryOperator<AttemptInput.Builder> patch) {
        return Attempt.of(patch.apply(BASE.toBuilder()).build());
    }

    @Nested
    class Of {

        @Test
        void keepsEveryFieldItWasGiven() {
            Attempt entry = make();
            assertThat(entry.learnerId()).isEqualTo("lrn_one");
            assertThat(entry.cellId()).isEqualTo("cell_aaaaaa");
            assertThat(entry.revisionId()).isEqualTo("rev_aaaaaa");
            assertThat(Identifiers.isId(IdKind.ATTEMPT, entry.id())).isTrue();
        }

        @Test
        void derivesTheIdFromTheSessionAndTheSequence() {
            assertThat(make().id()).isEqualTo(Attempt.idFor("sess_aaaaaa", 1));
            assertThat(make(input -> input.sequence(2)).id()).isNotEqualTo(make().id());
        }

        @Test
        void letsTheSameCellBeAnsweredTwiceInOneSession() {
            Attempt first = make(input -> input.sequence(1));
            Attempt second =
                    make(input -> input.sequence(2).verdict(MatchVerdict.WRONG).given("puella"));
            assertThat(first.id()).isNotEqualTo(second.id());
            assertThat(first.cellId()).isEqualTo(second.cellId());
        }

        @Test
        void normalisesTheTypedAnswer() {
            assertThat(make(input -> input.given("  puellae  ")).given()).isEqualTo("puellae");
        }

        @Test
        void keepsABlankAnswerRatherThanRefusingIt() {
            Attempt skipped = make(input -> input.given("   ").verdict(MatchVerdict.WRONG));
            assertThat(skipped.given()).isEmpty();
            assertThat(skipped.wasBlank()).isTrue();
        }

        @Test
        void refusesABlankExpectedForm() {
            assertThat(problemMessages(() -> make(input -> input.expected("   "))))
                    .containsExactly("expected must not be blank");
        }

        @Test
        void refusesASequenceBelowOne() {
            assertThat(problemMessages(() -> make(input -> input.sequence(0))))
                    .containsExactly("sequence must be a positive whole number: 0");
        }

        @Test
        void refusesAVerdictThatIsNotOneOfTheFour() {
            assertThat(problemMessages(() -> make(input -> input.verdict("partial"))))
                    .containsExactly("verdict is not a verdict: partial");
        }

        @Test
        void refusesANegativeElapsedTimeAndAcceptsZero() {
            assertThat(problemMessages(() -> make(input -> input.elapsedMs(-1))))
                    .containsExactly("elapsedMs must not be negative: -1");
            assertThat(make(input -> input.elapsedMs(0)).elapsedMs()).contains(0.0);
        }

        @Test
        void leavesTheElapsedTimeAbsentWhenNoneIsGiven() {
            assertThat(make().elapsedMs()).isEmpty();
        }
    }

    @Nested
    class IsMatchVerdict {

        @Test
        void acceptsTheFourVerdictsAndNothingElse() {
            for (String verdict : List.of("exact", "tolerated", "near", "wrong")) {
                assertThat(Attempt.isMatchVerdict(verdict)).isTrue();
            }
            assertThat(Attempt.isMatchVerdict("right")).isFalse();
        }
    }

    @Nested
    class WasCorrect {

        @Test
        void countsExactAndToleratedAsCorrect() {
            assertThat(make(input -> input.verdict(MatchVerdict.EXACT)).wasCorrect())
                    .isTrue();
            assertThat(make(input -> input.verdict(MatchVerdict.TOLERATED)).wasCorrect())
                    .isTrue();
        }

        @Test
        void countsNearAndWrongAsNotCorrect() {
            assertThat(make(input -> input.verdict(MatchVerdict.NEAR)).wasCorrect())
                    .isFalse();
            assertThat(make(input -> input.verdict(MatchVerdict.WRONG)).wasCorrect())
                    .isFalse();
        }
    }

    @Nested
    class Compare {

        @Test
        void sortsByDayThenSessionThenSequence() {
            List<Attempt> sorted = new ArrayList<>(List.of(
                    make(input -> input.sequence(2)),
                    make(input -> input.on(d("2025-03-01")).sequence(9)),
                    make(input -> input.sequence(1))));
            sorted.sort(Attempt.ORDER);
            assertThat(sorted.stream().map(Attempt::sequence)).containsExactly(9, 1, 2);
        }

        @Test
        void callsAnAttemptEqualToItself() {
            Attempt entry = make();
            assertThat(Attempt.compare(entry, entry)).isZero();
        }
    }

    @Nested
    class Tally {

        private final List<Attempt> attempts = List.of(
                make(input -> input.sequence(1).verdict(MatchVerdict.EXACT)),
                make(input -> input.sequence(2).verdict(MatchVerdict.TOLERATED)),
                make(input -> input.sequence(3).verdict(MatchVerdict.NEAR)),
                make(input -> input.sequence(4).verdict(MatchVerdict.WRONG)),
                make(input -> input.sequence(5).verdict(MatchVerdict.WRONG).given("")));

        @Test
        void countsEveryVerdict() {
            assertThat(AttemptTally.of(attempts)).isEqualTo(new AttemptTally(5, 2, 1, 1, 2, 1));
        }

        @Test
        void countsNothingForAnEmptyList() {
            assertThat(AttemptTally.of(List.of())).isEqualTo(new AttemptTally(0, 0, 0, 0, 0, 0));
        }

        @Test
        void readsAccuracyOffTheTally() {
            assertThat(AttemptTally.of(attempts).accuracy()).isCloseTo(0.4, byLessThan(0.005));
            assertThat(AttemptTally.of(List.of()).accuracy()).isEqualTo(0.0);
        }
    }

    @Nested
    class MedianElapsed {

        private List<Attempt> timed(double... elapsed) {
            return IntStream.range(0, elapsed.length)
                    .mapToObj(index -> make(input -> input.sequence(index + 1).elapsedMs(elapsed[index])))
                    .toList();
        }

        @Test
        void takesTheMiddleOfAnOddList() {
            assertThat(Attempt.medianElapsed(timed(100, 300, 200))).contains(200.0);
        }

        @Test
        void averagesTheMiddleTwoOfAnEvenList() {
            assertThat(Attempt.medianElapsed(timed(100, 200, 300, 400))).contains(250.0);
        }

        @Test
        void ignoresAttemptsThatWereNotTimed() {
            List<Attempt> attempts =
                    List.of(make(input -> input.sequence(1).elapsedMs(500)), make(input -> input.sequence(2)));
            assertThat(Attempt.medianElapsed(attempts)).contains(500.0);
        }

        @Test
        void givesNothingWhenNothingWasTimed() {
            assertThat(Attempt.medianElapsed(List.of(make()))).isEmpty();
            assertThat(Attempt.medianElapsed(List.of())).isEmpty();
        }
    }
}
