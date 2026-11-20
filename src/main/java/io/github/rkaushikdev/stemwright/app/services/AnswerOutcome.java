package io.github.rkaushikdev.stemwright.app.services;

import io.github.rkaushikdev.stemwright.domain.drill.ReviewResult;
import io.github.rkaushikdev.stemwright.domain.drill.Session;
import java.util.Objects;

/**
 * What answering did, once it was written.
 *
 * @param review how the answer was marked and everything it changed
 * @param session the open session with the answer recorded in it
 */
public record AnswerOutcome(ReviewResult review, Session session) {

    public AnswerOutcome {
        Objects.requireNonNull(review, "review");
        Objects.requireNonNull(session, "session");
    }
}
