package io.github.rkaushikdev.stemwright.domain.drill;

import io.github.rkaushikdev.stemwright.domain.mastery.AttemptTally;
import io.github.rkaushikdev.stemwright.domain.text.Numbers;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * One sitting counted up, as {@link Session#summarise()} gives it.
 *
 * @param sessionId the id of the session
 * @param learnerId the learner who sat it
 * @param deckKey the deck that was drilled
 * @param on the study day of the sitting
 * @param tally how the answers were marked
 * @param accuracy the share of answers that were right, nought for a sitting with none
 * @param distinctCells how many different cells were answered
 * @param medianElapsedMs the median time an answer took, absent when none was timed
 * @param closed whether the sitting is over
 */
public record SessionSummary(
        String sessionId,
        String learnerId,
        String deckKey,
        LocalDate on,
        AttemptTally tally,
        double accuracy,
        int distinctCells,
        Optional<Double> medianElapsedMs,
        boolean closed) {

    private static final int PERCENT = 100;

    public SessionSummary {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(learnerId, "learnerId");
        Objects.requireNonNull(deckKey, "deckKey");
        Objects.requireNonNull(on, "on");
        Objects.requireNonNull(tally, "tally");
        Objects.requireNonNull(medianElapsedMs, "medianElapsedMs");
    }

    /**
     * The sitting read out for the learner, such as {@code 1 answers across 1 cells on 2025-03-03,
     * 100 percent right}.
     */
    public String describe() {
        if (tally.total() == 0) {
            return "no answers on " + on;
        }
        long percent = Numbers.round(accuracy * PERCENT);
        return tally.total() + " answers across " + distinctCells + " cells on " + on + ", " + percent
                + " percent right";
    }
}
