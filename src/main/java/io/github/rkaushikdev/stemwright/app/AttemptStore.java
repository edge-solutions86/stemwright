package io.github.rkaushikdev.stemwright.app;

import io.github.rkaushikdev.stemwright.domain.mastery.Attempt;
import io.github.rkaushikdev.stemwright.domain.time.DayRange;
import java.util.List;

/**
 * The attempt log. It is append only: an attempt records what a learner was shown and how it was
 * marked at the time, and nothing that happens later changes that.
 */
public interface AttemptStore {

    /** Appends the attempts, in the order given. */
    void append(List<Attempt> entries);

    /** Every attempt of the learner, in {@link Attempt#ORDER}. */
    List<Attempt> listByLearner(String learnerId);

    /** The attempts of the learner on the days of the range, in {@link Attempt#ORDER}. */
    List<Attempt> listByLearner(String learnerId, DayRange range);

    /** Every attempt of the session, in {@link Attempt#ORDER}. */
    List<Attempt> listBySession(String sessionId);
}
