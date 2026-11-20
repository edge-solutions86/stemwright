package io.github.rkaushikdev.stemwright.domain.report;

import io.github.rkaushikdev.stemwright.domain.course.AssignmentProgress;
import java.util.Objects;

/**
 * One learner's line of a cohort report.
 *
 * @param learnerId the learner
 * @param mastered how many of the assignment's cells they have mastered
 * @param cells how many cells the assignment selects
 * @param practiceDays how many days they practised inside the window
 * @param met whether they have met every target
 * @param share the share of the selected cells they have mastered
 */
public record CohortRow(String learnerId, int mastered, int cells, int practiceDays, boolean met, double share) {

    public CohortRow {
        Objects.requireNonNull(learnerId, "learnerId");
    }

    /** The row a progress reading prints as. */
    static CohortRow of(AssignmentProgress progress) {
        return new CohortRow(
                progress.learnerId(),
                progress.mastered(),
                progress.cells(),
                progress.practiceDays(),
                progress.met(),
                progress.masteryShare());
    }
}
