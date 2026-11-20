package io.github.rkaushikdev.stemwright.domain.mastery;

import io.github.rkaushikdev.stemwright.domain.ortho.MatchVerdict;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * One typed answer as the marker or a stored row hands it over, before {@link Attempt#of} checks it.
 *
 * <p>The verdict is kept as written rather than as a {@link MatchVerdict}, because an attempt can be
 * rebuilt from a stored row, and {@link Attempt#of} is where a word that is not one of the four gets
 * reported along with everything else wrong with the attempt.
 *
 * @param learnerId the learner who answered
 * @param sessionId the session the answer was given in
 * @param cellId the cell that was asked
 * @param deckKey the deck the cell was asked from, which becomes a slug
 * @param on the study day of the answer
 * @param sequence where the answer falls in its session, from one
 * @param given what the learner typed
 * @param expected the form that was expected
 * @param verdict how the answer was marked, as written, such as {@code tolerated}
 * @param toleranceLevel the tolerance level it was marked at, which becomes a slug
 * @param revisionId the profile revision the expected form came from
 * @param elapsedMs how long the learner took, in milliseconds, possibly fractional
 */
public record AttemptInput(
        String learnerId,
        String sessionId,
        String cellId,
        String deckKey,
        LocalDate on,
        int sequence,
        String given,
        String expected,
        String verdict,
        String toleranceLevel,
        String revisionId,
        Optional<Double> elapsedMs) {

    public AttemptInput {
        Objects.requireNonNull(learnerId, "learnerId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(cellId, "cellId");
        Objects.requireNonNull(deckKey, "deckKey");
        Objects.requireNonNull(on, "on");
        Objects.requireNonNull(given, "given");
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(toleranceLevel, "toleranceLevel");
        Objects.requireNonNull(revisionId, "revisionId");
        Objects.requireNonNull(elapsedMs, "elapsedMs");
    }

    /** An empty builder; every part but the elapsed time must be set before it is built. */
    public static Builder builder() {
        return new Builder();
    }

    /** A builder holding this input, for a caller that wants to vary some of it. */
    public Builder toBuilder() {
        Builder builder = new Builder()
                .learnerId(learnerId)
                .sessionId(sessionId)
                .cellId(cellId)
                .deckKey(deckKey)
                .on(on)
                .sequence(sequence)
                .given(given)
                .expected(expected)
                .verdict(verdict)
                .toleranceLevel(toleranceLevel)
                .revisionId(revisionId);
        elapsedMs.ifPresent(builder::elapsedMs);
        return builder;
    }

    /** Collects the parts of an attempt; the elapsed time is the only one that may be left out. */
    public static final class Builder {

        private String learnerId;
        private String sessionId;
        private String cellId;
        private String deckKey;
        private LocalDate on;
        private int sequence;
        private String given;
        private String expected;
        private String verdict;
        private String toleranceLevel;
        private String revisionId;
        private Double elapsedMs;

        private Builder() {}

        public Builder learnerId(String learnerId) {
            this.learnerId = learnerId;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder cellId(String cellId) {
            this.cellId = cellId;
            return this;
        }

        public Builder deckKey(String deckKey) {
            this.deckKey = deckKey;
            return this;
        }

        public Builder on(LocalDate on) {
            this.on = on;
            return this;
        }

        public Builder sequence(int sequence) {
            this.sequence = sequence;
            return this;
        }

        public Builder given(String given) {
            this.given = given;
            return this;
        }

        public Builder expected(String expected) {
            this.expected = expected;
            return this;
        }

        public Builder verdict(MatchVerdict verdict) {
            return verdict(verdict.key());
        }

        /** The verdict as written, such as a stored row holds it. */
        public Builder verdict(String verdict) {
            this.verdict = verdict;
            return this;
        }

        public Builder toleranceLevel(String toleranceLevel) {
            this.toleranceLevel = toleranceLevel;
            return this;
        }

        public Builder revisionId(String revisionId) {
            this.revisionId = revisionId;
            return this;
        }

        public Builder elapsedMs(double elapsedMs) {
            this.elapsedMs = elapsedMs;
            return this;
        }

        /**
         * The input, once every required part is set.
         *
         * @throws NullPointerException naming the first required part that was never set
         */
        public AttemptInput build() {
            return new AttemptInput(
                    learnerId,
                    sessionId,
                    cellId,
                    deckKey,
                    on,
                    sequence,
                    given,
                    expected,
                    verdict,
                    toleranceLevel,
                    revisionId,
                    Optional.ofNullable(elapsedMs));
        }
    }
}
