package io.github.rkaushikdev.stemwright.app.services;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * One typed answer, as a learner sends it to {@link DrillService#answer}.
 *
 * @param learnerId the learner answering
 * @param language the language key of the deck
 * @param deckKey the deck the cell was asked from
 * @param cellId the cell answered
 * @param given what the learner typed
 * @param on the study day of the answer, the clock's day when absent
 * @param elapsedMs how long the learner took, in milliseconds, absent when it was not timed
 */
public record AnswerRequest(
        String learnerId,
        String language,
        String deckKey,
        String cellId,
        String given,
        Optional<LocalDate> on,
        Optional<Double> elapsedMs) {

    public AnswerRequest {
        Objects.requireNonNull(learnerId, "learnerId");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(deckKey, "deckKey");
        Objects.requireNonNull(cellId, "cellId");
        Objects.requireNonNull(given, "given");
        Objects.requireNonNull(on, "on");
        Objects.requireNonNull(elapsedMs, "elapsedMs");
    }

    /** An untimed answer given today. */
    public static AnswerRequest of(String learnerId, String language, String deckKey, String cellId, String given) {
        return new AnswerRequest(learnerId, language, deckKey, cellId, given, Optional.empty(), Optional.empty());
    }

    /** A copy given on the study day. */
    public AnswerRequest withOn(LocalDate day) {
        return new AnswerRequest(learnerId, language, deckKey, cellId, given, Optional.of(day), elapsedMs);
    }

    /** A copy timed at the milliseconds. */
    public AnswerRequest withElapsedMs(double milliseconds) {
        return new AnswerRequest(learnerId, language, deckKey, cellId, given, on, Optional.of(milliseconds));
    }
}
