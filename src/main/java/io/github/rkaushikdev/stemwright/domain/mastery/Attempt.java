package io.github.rkaushikdev.stemwright.domain.mastery;

import io.github.rkaushikdev.stemwright.domain.IdKind;
import io.github.rkaushikdev.stemwright.domain.Identifiers;
import io.github.rkaushikdev.stemwright.domain.errors.ProblemCollector;
import io.github.rkaushikdev.stemwright.domain.ortho.MatchVerdict;
import io.github.rkaushikdev.stemwright.domain.text.Collation;
import io.github.rkaushikdev.stemwright.domain.text.Normalise;
import io.github.rkaushikdev.stemwright.domain.text.Numbers;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One typed answer, recorded as it was marked at the time. This record is the only thing in the
 * system that is never recomputed: the tolerance level, the profile revision and the expected form
 * are all copied in rather than looked up later, so a transcript printed next year says what the
 * learner was actually shown.
 *
 * <p>That redundancy is the point. Every other table can be rebuilt from the journal, and this one
 * cannot be rebuilt from anything, because the inputs that produced it have moved on.
 *
 * <p>Build one through {@link #of(AttemptInput)}, which checks it; the canonical constructor only
 * copies what it is given.
 *
 * @param id the derived attempt id, see {@link #idFor}
 * @param learnerId the learner who answered
 * @param sessionId the session the answer was given in
 * @param cellId the cell that was asked
 * @param deckKey the deck the cell was asked from
 * @param on the study day of the answer
 * @param sequence where the answer falls in its session, from one
 * @param given what the learner typed, normalised, and empty for a blank answer
 * @param expected the form that was expected, normalised
 * @param verdict how the answer was marked
 * @param toleranceLevel the tolerance level it was marked at
 * @param revisionId the profile revision the expected form came from
 * @param elapsedMs how long the learner took, in milliseconds, absent when it was not timed
 */
public record Attempt(
        String id,
        String learnerId,
        String sessionId,
        String cellId,
        String deckKey,
        LocalDate on,
        int sequence,
        String given,
        String expected,
        MatchVerdict verdict,
        String toleranceLevel,
        String revisionId,
        Optional<Double> elapsedMs) {

    /** {@link #compare} as a comparator: transcript order. */
    public static final Comparator<Attempt> ORDER = Attempt::compare;

    public Attempt {
        Objects.requireNonNull(on, "on");
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(elapsedMs, "elapsedMs");
    }

    /** True for exactly the keys of the four verdicts, compared as written. */
    public static boolean isMatchVerdict(String value) {
        return MatchVerdict.fromKey(value).isPresent();
    }

    /**
     * The id is derived from the session and the sequence number rather than the cell, because a
     * learner may meet the same cell twice in one session after a lapse and both attempts are real.
     */
    public static String idFor(String sessionId, int sequence) {
        return Identifiers.derivedId(IdKind.ATTEMPT, sessionId, Integer.toString(sequence));
    }

    /**
     * Checks an attempt as handed over. A sequence below one, a verdict that is not one of the four,
     * a negative or non finite elapsed time and a blank expected form are reported together; a deck
     * key or tolerance level that is not a slug fails on the spot. A blank answer is kept rather
     * than refused, because skipping a question is an answer too.
     */
    public static Attempt of(AttemptInput input) {
        ProblemCollector problems = new ProblemCollector();

        problems.addIf(input.sequence() < 1, "sequence", "must be a positive whole number: " + input.sequence());
        Optional<MatchVerdict> verdict = MatchVerdict.fromKey(input.verdict());
        problems.addIf(verdict.isEmpty(), "verdict", "is not a verdict: " + input.verdict());
        input.elapsedMs()
                .filter(elapsed -> !Double.isFinite(elapsed) || elapsed < 0)
                .ifPresent(elapsed -> problems.add("elapsedMs", "must not be negative: " + numberText(elapsed)));
        String expected = Normalise.normaliseInput(input.expected());
        problems.addIf(expected.isEmpty(), "expected", "must not be blank");

        problems.throwIfAny("attempt is not usable");

        return new Attempt(
                idFor(input.sessionId(), input.sequence()),
                input.learnerId(),
                input.sessionId(),
                input.cellId(),
                Identifiers.asSlug("deckKey", input.deckKey()),
                input.on(),
                input.sequence(),
                Normalise.normaliseInput(input.given()),
                expected,
                verdict.orElseThrow(),
                Identifiers.asSlug("toleranceLevel", input.toleranceLevel()),
                input.revisionId(),
                input.elapsedMs());
    }

    /** Exact and tolerated answers are right; near and wrong ones are not. */
    public boolean wasCorrect() {
        return verdict == MatchVerdict.EXACT || verdict == MatchVerdict.TOLERATED;
    }

    public boolean wasBlank() {
        return given.isEmpty();
    }

    /** Orders by study day, then by session id as a reader sorts them, then by sequence. */
    public static int compare(Attempt a, Attempt b) {
        int byDay = CivilDates.compareCivil(a.on, b.on);
        if (byDay != 0) {
            return byDay;
        }
        int bySession = Collation.compare(a.sessionId, b.sessionId);
        return bySession != 0 ? bySession : Integer.compare(a.sequence, b.sequence);
    }

    /**
     * The median time taken over the attempts that were timed, the mean of the middle two for an
     * even count, and nothing when none was timed.
     */
    public static Optional<Double> medianElapsed(List<Attempt> attempts) {
        List<Double> timed = attempts.stream()
                .map(Attempt::elapsedMs)
                .flatMap(Optional::stream)
                .sorted()
                .toList();
        if (timed.isEmpty()) {
            return Optional.empty();
        }
        int middle = timed.size() / 2;
        return Optional.of(timed.size() % 2 == 1 ? timed.get(middle) : (timed.get(middle - 1) + timed.get(middle)) / 2);
    }

    /** The number as a message prints it, with {@code NaN} and {@code Infinity} spelled out. */
    private static String numberText(double value) {
        return Double.isFinite(value) ? Numbers.format(value) : String.valueOf(value);
    }
}
