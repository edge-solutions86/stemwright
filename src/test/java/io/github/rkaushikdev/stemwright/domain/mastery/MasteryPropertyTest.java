package io.github.rkaushikdev.stemwright.domain.mastery;

import static io.github.rkaushikdev.stemwright.support.SeededRandom.forEachDraw;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import io.github.rkaushikdev.stemwright.support.SeededRandom;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MasteryPropertyTest {

    private static final Ladder STANDARD = Ladder.STANDARD;
    private static final LocalDate START = CivilDates.parseCivilDate("2025-01-06");

    private enum Step {
        CORRECT,
        LAPSE,
        NEAR
    }

    private static final List<Step> STEPS = List.of(Step.CORRECT, Step.LAPSE, Step.NEAR);

    /** Where a walk ended: the strength it left and the day after its last answer. */
    private record Walk(CellStrength strength, LocalDate day) {}

    private static Walk walk(SeededRandom random, int steps) {
        CellStrength strength = CellStrength.fresh("cell_a");
        LocalDate day = START;
        for (int index = 0; index < steps; index += 1) {
            strength = switch (random.pick(STEPS)) {
                case CORRECT -> strength.recordCorrect(STANDARD, day);
                case LAPSE -> strength.recordLapse(STANDARD, day);
                case NEAR -> strength.recordNearMiss(STANDARD, day);
            };
            day = CivilDates.addDays(day, random.nextInt(0, 20));
        }
        return new Walk(strength, day);
    }

    @Nested
    class LadderProperties {

        @Test
        void neverLeavesTheLadderHoweverFarItIsPushed() {
            forEachDraw(80, 1117, (random, draw) -> {
                int rung = STANDARD.clampRung(random.nextInt(-50, 50));
                assertThat(rung).isGreaterThanOrEqualTo(0);
                assertThat(rung).isLessThanOrEqualTo(STANDARD.topRung());
            });
        }

        @Test
        void climbsToARungThatIsNeverShorter() {
            forEachDraw(60, 3323, (random, draw) -> {
                int rung = random.nextInt(0, STANDARD.topRung());
                assertThat(STANDARD.intervalAt(STANDARD.climb(rung))).isGreaterThanOrEqualTo(STANDARD.intervalAt(rung));
            });
        }

        @Test
        void lapsesToARungThatIsNeverLonger() {
            forEachDraw(60, 4441, (random, draw) -> {
                int rung = random.nextInt(0, STANDARD.topRung());
                assertThat(STANDARD.intervalAt(STANDARD.lapse(rung))).isLessThanOrEqualTo(STANDARD.intervalAt(rung));
            });
        }

        @Test
        void spreadsAnIntervalWithinTheConfiguredSwingAndNeverBelowADay() {
            forEachDraw(120, 5557, (random, draw) -> {
                int rung = random.nextInt(0, STANDARD.topRung());
                int base = STANDARD.intervalAt(rung);
                int spread = STANDARD.spreadInterval(rung, random.word());
                assertThat(spread).isGreaterThanOrEqualTo(1);
                assertThat((double) Math.abs(spread - base)).isLessThanOrEqualTo(Math.ceil(base * STANDARD.spread()));
            });
        }
    }

    @Nested
    class StrengthProperties {

        @Test
        void countsEverySightingExactlyOnce() {
            forEachDraw(80, 6661, (random, draw) -> {
                int steps = random.nextInt(1, 12);
                CellStrength strength = walk(random, steps).strength();
                assertThat(strength.seen()).isEqualTo(steps);
            });
        }

        @Test
        void neverRecordsMoreCorrectAnswersThanSightings() {
            forEachDraw(80, 7717, (random, draw) -> {
                CellStrength strength = walk(random, random.nextInt(1, 12)).strength();
                assertThat(strength.correct()).isLessThanOrEqualTo(strength.seen());
                assertThat(strength.accuracy()).isLessThanOrEqualTo(1);
            });
        }

        @Test
        void alwaysCarriesADueDateOnceItHasBeenAnswered() {
            forEachDraw(80, 8887, (random, draw) -> {
                CellStrength strength = walk(random, random.nextInt(1, 12)).strength();
                assertThat(strength.dueOn()).isPresent();
                assertThat(strength.isNew()).isFalse();
            });
        }

        /** The loop bound is drawn again on every pass, exactly as the original wrote it. */
        @Test
        void neverSchedulesAReviewInThePastRelativeToTheAnswer() {
            forEachDraw(80, 9973, (random, draw) -> {
                CellStrength strength = CellStrength.fresh("cell_a");
                LocalDate day = START;
                for (int index = 0; index < random.nextInt(1, 8); index += 1) {
                    strength = strength.recordCorrect(STANDARD, day);
                    assertThat(CivilDates.compareCivil(strength.dueOn().orElseThrow(), day))
                            .isGreaterThan(0);
                    day = CivilDates.addDays(day, random.nextInt(0, 30));
                }
            });
        }

        @Test
        void keepsTheRungInsideTheLadderHoweverItWasWalked() {
            forEachDraw(80, 2237, (random, draw) -> {
                CellStrength strength = walk(random, random.nextInt(1, 20)).strength();
                assertThat(strength.rung()).isGreaterThanOrEqualTo(0);
                assertThat(strength.rung()).isLessThanOrEqualTo(STANDARD.topRung());
            });
        }

        @Test
        void takesASuspendedCellOutOfTheDueSetOnEveryDay() {
            forEachDraw(60, 3391, (random, draw) -> {
                Walk walked = walk(random, random.nextInt(1, 8));
                CellStrength gone = walked.strength().suspend();
                assertThat(gone.isDue(CivilDates.addDays(walked.day(), random.nextInt(0, 400))))
                        .isFalse();
            });
        }

        @Test
        void neverPullsADueDateForwardWhenDeferring() {
            forEachDraw(80, 4457, (random, draw) -> {
                Walk walked = walk(random, random.nextInt(1, 8));
                CellStrength strength = walked.strength();
                LocalDate target = CivilDates.addDays(walked.day(), random.nextInt(-30, 30));
                CellStrength deferred = strength.deferTo(target);
                assertThat(CivilDates.compareCivil(
                                deferred.dueOn().orElseThrow(), strength.dueOn().orElseThrow()))
                        .isGreaterThanOrEqualTo(0);
            });
        }

        @Test
        void reportsNothingOverdueBeforeTheDueDate() {
            forEachDraw(60, 5573, (random, draw) -> {
                CellStrength strength = walk(random, random.nextInt(1, 8)).strength();
                LocalDate before = CivilDates.addDays(strength.dueOn().orElseThrow(), -random.nextInt(1, 20));
                assertThat(strength.daysOverdue(before)).isEqualTo(0);
                assertThat(strength.isDue(before)).isFalse();
            });
        }
    }
}
