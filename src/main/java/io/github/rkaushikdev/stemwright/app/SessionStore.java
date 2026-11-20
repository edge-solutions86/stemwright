package io.github.rkaushikdev.stemwright.app;

import io.github.rkaushikdev.stemwright.domain.drill.Session;
import java.util.List;
import java.util.Optional;

/** The drill sessions of every learner, open and closed, keyed by session id. */
public interface SessionStore {

    Optional<Session> get(String id);

    /** Stores the session under its id, in place of the session stored there before. */
    void put(Session session);

    /** The learner's sessions that are still open, in the order they were first stored. */
    List<Session> listOpen(String learnerId);
}
