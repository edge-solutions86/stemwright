package io.github.rkaushikdev.stemwright.domain.mastery;

import java.util.List;

/**
 * How a list of attempts was marked. Exact answers are counted twice, once as exact and once as
 * correct, and a blank answer is counted under its verdict as well as under blank.
 *
 * @param total how many attempts there were
 * @param correct exact and tolerated answers together
 * @param exact answers that matched to the letter
 * @param near wrong answers close enough to earn a hint
 * @param wrong answers that were simply wrong
 * @param blank answers left empty, whatever they were marked
 */
public record AttemptTally(int total, int correct, int exact, int near, int wrong, int blank) {

    public static AttemptTally of(List<Attempt> attempts) {
        int correct = 0;
        int exact = 0;
        int near = 0;
        int wrong = 0;
        int blank = 0;

        for (Attempt entry : attempts) {
            if (entry.wasBlank()) {
                blank += 1;
            }
            switch (entry.verdict()) {
                case EXACT -> {
                    exact += 1;
                    correct += 1;
                }
                case TOLERATED -> correct += 1;
                case NEAR -> near += 1;
                case WRONG -> wrong += 1;
            }
        }

        return new AttemptTally(attempts.size(), correct, exact, near, wrong, blank);
    }

    /** The share of attempts that were correct, zero when there were none. */
    public double accuracy() {
        return total == 0 ? 0 : (double) correct / total;
    }
}
