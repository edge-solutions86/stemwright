package io.github.rkaushikdev.stemwright.domain.report;

import static io.github.rkaushikdev.stemwright.support.Course.MICHAELMAS;
import static io.github.rkaushikdev.stemwright.support.Drill.DRILL_CONTEXT;
import static io.github.rkaushikdev.stemwright.support.Profiles.LATIN_NOUN;
import static io.github.rkaushikdev.stemwright.support.SeededRandom.forEachDraw;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.domain.course.Enrolment;
import io.github.rkaushikdev.stemwright.domain.course.EnrolmentInput;
import io.github.rkaushikdev.stemwright.domain.course.LearnerPreferences;
import io.github.rkaushikdev.stemwright.domain.course.PreferencesInput;
import io.github.rkaushikdev.stemwright.domain.drill.Deck;
import io.github.rkaushikdev.stemwright.domain.drill.DeckInput;
import io.github.rkaushikdev.stemwright.domain.drill.DrillPolicy;
import io.github.rkaushikdev.stemwright.domain.drill.DrillPolicyPatch;
import io.github.rkaushikdev.stemwright.domain.drill.Membership;
import io.github.rkaushikdev.stemwright.domain.mastery.CellStrength;
import io.github.rkaushikdev.stemwright.domain.mastery.Ladder;
import io.github.rkaushikdev.stemwright.domain.mastery.LedgerCounts;
import io.github.rkaushikdev.stemwright.domain.mastery.MasteryLedger;
import io.github.rkaushikdev.stemwright.domain.paradigm.Cell;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import io.github.rkaushikdev.stemwright.domain.time.DayRange;
import io.github.rkaushikdev.stemwright.support.SeededRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReportPropertyTest {

    private static final LocalDate DAY = CivilDates.parseCivilDate("2024-10-21");
    private static final Ladder STANDARD = Ladder.STANDARD;
    private static final Membership MEMBERSHIP =
            Membership.resolve(DRILL_CONTEXT, Deck.of(LATIN_NOUN, DeckInput.of("all")), DAY);
    private static final List<Cell> CELLS = MEMBERSHIP.cells();

    /**
     * A ledger over the deck: about seven cells in ten answered between one and nine times, right
     * about two times in three, and about one in ten of those suspended. The bound of the answer loop
     * is drawn again on every pass, as the original drew it.
     */
    private static MasteryLedger ledgerFor(SeededRandom random) {
        List<CellStrength> strengths = new ArrayList<>();
        for (Cell cell : CELLS) {
            if (random.next() < 0.3) {
                continue;
            }
            CellStrength strength = CellStrength.fresh(cell.id());
            for (int step = 0; step < random.nextInt(1, 9); step += 1) {
                strength =
                        random.bool(0.65) ? strength.recordCorrect(STANDARD, DAY) : strength.recordLapse(STANDARD, DAY);
            }
            strengths.add(random.bool(0.1) ? strength.suspend() : strength);
        }
        return MasteryLedger.of("lrn_one", strengths);
    }

    private static DrillPolicy policy(DrillPolicyPatch.Builder patch) {
        return DrillPolicy.of(patch.build());
    }

    @Nested
    class CoverageProperties {

        @Test
        void countsEveryCellExactlyOnceDownEitherAxis() {
            forEachDraw(30, 1223, (random, draw) -> {
                MasteryLedger ledger = ledgerFor(random);
                for (List<CoverageRow> rows : List.of(
                        Coverage.byBundle(CELLS, ledger, STANDARD, DAY),
                        Coverage.byLexeme(CELLS, ledger, STANDARD, DAY))) {
                    assertThat(rows.stream().mapToInt(CoverageRow::total).sum()).isEqualTo(CELLS.size());
                }
            });
        }

        @Test
        void splitsEveryRowIntoTheFourStatesWithNothingLeftOver() {
            forEachDraw(30, 2333, (random, draw) -> {
                MasteryLedger ledger = ledgerFor(random);
                for (CoverageRow row : Coverage.byBundle(CELLS, ledger, STANDARD, DAY)) {
                    assertThat(row.untouched() + row.learning() + row.mature() + row.suspended())
                            .isEqualTo(row.total());
                }
            });
        }

        @Test
        void neverCountsMoreDueThanThereAreCellsInARow() {
            forEachDraw(30, 3449, (random, draw) -> {
                MasteryLedger ledger = ledgerFor(random);
                for (CoverageRow row : Coverage.byBundle(CELLS, ledger, STANDARD, DAY)) {
                    assertThat(row.due()).isLessThanOrEqualTo(row.total());
                }
            });
        }

        @Test
        void keepsEveryShareBetweenNoughtAndOne() {
            forEachDraw(30, 4547, (random, draw) -> {
                MasteryLedger ledger = ledgerFor(random);
                for (CoverageRow row : Coverage.byLexeme(CELLS, ledger, STANDARD, DAY)) {
                    assertThat(row.maturity()).isGreaterThanOrEqualTo(0);
                    assertThat(row.maturity()).isLessThanOrEqualTo(1);
                    assertThat(row.touchedShare()).isGreaterThanOrEqualTo(0);
                    assertThat(row.touchedShare()).isLessThanOrEqualTo(1);
                }
            });
        }

        @Test
        void totalsToTheSameCountsTheLedgerReports() {
            forEachDraw(30, 5651, (random, draw) -> {
                MasteryLedger ledger = ledgerFor(random);
                CoverageRow total = Coverage.total(Coverage.byBundle(CELLS, ledger, STANDARD, DAY));
                LedgerCounts counts = ledger.counts(CELLS.stream().map(Cell::id).toList(), STANDARD, DAY);
                assertThat(total.total()).isEqualTo(counts.known());
                assertThat(total.mature()).isEqualTo(counts.mature());
                assertThat(total.suspended()).isEqualTo(counts.suspended());
                assertThat(total.untouched()).isEqualTo(counts.untouched());
            });
        }

        @Test
        void ranksTheWeakestRowsNoHigherInMaturityThanTheRest() {
            forEachDraw(30, 6761, (random, draw) -> {
                List<CoverageRow> rows = Coverage.byBundle(CELLS, ledgerFor(random), STANDARD, DAY);
                List<CoverageRow> weakest = Coverage.weakestRows(rows, 3);
                double worst = weakest.stream()
                        .mapToDouble(CoverageRow::maturity)
                        .max()
                        .orElse(Double.NEGATIVE_INFINITY);
                for (CoverageRow row : rows.stream()
                        .filter(candidate -> !weakest.contains(candidate))
                        .toList()) {
                    assertThat(row.maturity()).isGreaterThanOrEqualTo(worst - 1e-9);
                }
            });
        }

        @Test
        void neverReportsACellAtARungAboveTheTopOfTheLadder() {
            forEachDraw(30, 7873, (random, draw) -> {
                for (CellStrength strength : ledgerFor(random).byCell().values()) {
                    assertThat(strength.rung()).isLessThanOrEqualTo(STANDARD.topRung());
                }
            });
        }
    }

    @Nested
    class ForecastProperties {

        @Test
        void neverForecastsMoreReviewsThanThereAreScheduledCells() {
            forEachDraw(30, 8887, (random, draw) -> {
                MasteryLedger ledger = ledgerFor(random);
                DayRange range = DayRange.of(DAY, CivilDates.addDays(DAY, random.nextInt(1, 60)));
                long scheduled = ledger.byCell().values().stream()
                        .filter(strength ->
                                !strength.suspended() && strength.dueOn().isPresent())
                        .count();
                List<ForecastDay> days = Forecast.daily(CELLS, ledger, range, DrillPolicy.DEFAULT);
                assertThat((long) Forecast.totalDue(days)).isLessThanOrEqualTo(scheduled);
            });
        }

        @Test
        void coversEveryDayOfTheRangeItWasGiven() {
            forEachDraw(30, 9901, (random, draw) -> {
                int length = random.nextInt(1, 40);
                DayRange range = DayRange.of(DAY, CivilDates.addDays(DAY, length - 1L));
                assertThat(Forecast.daily(CELLS, ledgerFor(random), range, DrillPolicy.DEFAULT))
                        .hasSize(length);
            });
        }
    }

    @Nested
    class PreferenceProperties {

        private final Enrolment enrolment = Enrolment.of(MICHAELMAS, EnrolmentInput.of("lrn_one"));

        @Test
        void neverLetsALearnerRaiseABudgetAboveTheLayerAbove() {
            forEachDraw(60, 1013, (random, draw) -> {
                DrillPolicy base = policy(DrillPolicyPatch.builder()
                        .newPerDay(random.nextInt(1, 20))
                        .reviewsPerDay(random.nextInt(1, 90)));
                LearnerPreferences preferences = LearnerPreferences.of(PreferencesInput.builder("lrn_one")
                        .patch(DrillPolicyPatch.builder()
                                .newPerDay(random.nextInt(0, 60))
                                .reviewsPerDay(random.nextInt(1, 200))
                                .build())
                        .build());
                LearnerPreferences.PolicyResolution resolved = LearnerPreferences.resolvePolicyFor(
                        base, DrillPolicyPatch.EMPTY, Optional.of(enrolment), preferences);
                assertThat(resolved.policy().newPerDay()).isLessThanOrEqualTo(base.newPerDay());
                assertThat(resolved.policy().reviewsPerDay()).isLessThanOrEqualTo(base.reviewsPerDay());
            });
        }

        @Test
        void changesNothingWhenTheLearnerAsksForNothing() {
            forEachDraw(30, 2027, (random, draw) -> {
                DrillPolicy base = policy(DrillPolicyPatch.builder().newPerDay(random.nextInt(1, 20)));
                LearnerPreferences.PolicyResolution resolved = LearnerPreferences.resolvePolicyFor(
                        base, DrillPolicyPatch.EMPTY, Optional.of(enrolment), LearnerPreferences.none("lrn_one"));
                assertThat(resolved.policy()).isEqualTo(base.applyPatch(DrillPolicyPatch.EMPTY));
                assertThat(resolved.refused()).isEmpty();
            });
        }

        @Test
        void refusesExactlyTheFieldsItDropped() {
            forEachDraw(60, 3037, (random, draw) -> {
                DrillPolicy base = policy(DrillPolicyPatch.builder().newPerDay(random.nextInt(1, 10)));
                int asked = random.nextInt(0, 30);
                LearnerPreferences.ClampedPatch clamped = LearnerPreferences.clampToCeiling(
                        base, DrillPolicyPatch.builder().newPerDay(asked).build());
                assertThat(clamped.refused().contains("newPerDay")).isEqualTo(asked > base.newPerDay());
            });
        }
    }
}
