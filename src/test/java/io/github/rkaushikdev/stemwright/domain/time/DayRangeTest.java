package io.github.rkaushikdev.stemwright.domain.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DayRangeTest {

    private static LocalDate d(String value) {
        return CivilDates.parseCivilDate(value);
    }

    private static DayRange r(String from, String to) {
        return DayRange.of(d(from), d(to));
    }

    @Nested
    class Construction {

        @Test
        void acceptsASingleDayRange() {
            assertThat(r("2024-05-01", "2024-05-01").length()).isEqualTo(1);
        }

        @Test
        void refusesARangeThatEndsBeforeItStarts() {
            assertThatThrownBy(() -> r("2024-05-02", "2024-05-01")).isInstanceOf(ValidationException.class);
        }

        @Test
        void countsBothEndsIn() {
            assertThat(r("2024-05-01", "2024-05-07").length()).isEqualTo(7);
        }
    }

    @Nested
    class Contains {

        private final DayRange week = r("2024-05-06", "2024-05-12");

        @Test
        void holdsBothEndpoints() {
            assertThat(week.contains(d("2024-05-06"))).isTrue();
            assertThat(week.contains(d("2024-05-12"))).isTrue();
        }

        @Test
        void excludesTheDaysEitherSide() {
            assertThat(week.contains(d("2024-05-05"))).isFalse();
            assertThat(week.contains(d("2024-05-13"))).isFalse();
        }
    }

    @Nested
    class OverlapAndIntersection {

        @Test
        void countsASharedEndpointAsAnOverlap() {
            assertThat(r("2024-05-01", "2024-05-05").overlaps(r("2024-05-05", "2024-05-09")))
                    .isTrue();
        }

        @Test
        void doesNotCountAdjacencyAsAnOverlap() {
            assertThat(r("2024-05-01", "2024-05-04").overlaps(r("2024-05-05", "2024-05-09")))
                    .isFalse();
        }

        @Test
        void intersectsToTheTighterPairOfBounds() {
            assertThat(r("2024-05-01", "2024-05-10").intersect(r("2024-05-04", "2024-05-20")))
                    .contains(r("2024-05-04", "2024-05-10"));
        }

        @Test
        void givesNothingWhenTheyMiss() {
            assertThat(r("2024-05-01", "2024-05-02").intersect(r("2024-05-04", "2024-05-05")))
                    .isEmpty();
        }

        @Test
        void hullsAcrossTheGap() {
            assertThat(r("2024-05-01", "2024-05-02").hull(r("2024-05-20", "2024-05-21")))
                    .isEqualTo(r("2024-05-01", "2024-05-21"));
        }
    }

    @Nested
    class Shift {

        @Test
        void movesBothEndsByTheSameNumberOfDays() {
            assertThat(r("2024-02-27", "2024-02-28").shift(2)).isEqualTo(r("2024-02-29", "2024-03-01"));
        }

        @Test
        void movesBackwardsToo() {
            assertThat(r("2024-03-01", "2024-03-02").shift(-1)).isEqualTo(r("2024-02-29", "2024-03-01"));
        }
    }

    @Nested
    class EachDay {

        @Test
        void walksEveryDayInclusive() {
            assertThat(r("2024-04-29", "2024-05-02").days())
                    .containsExactly(d("2024-04-29"), d("2024-04-30"), d("2024-05-01"), d("2024-05-02"));
        }

        @Test
        void yieldsExactlyOneDayForASingleDayRange() {
            assertThat(DayRange.singleDay(d("2024-06-01")).eachDay()).containsExactly(d("2024-06-01"));
        }

        @Test
        void yieldsAsManyDaysAsTheLengthReports() {
            DayRange range = r("2024-01-01", "2024-12-31");
            assertThat(range.days()).hasSize(range.length());
        }
    }

    @Nested
    class FormattingAndParsing {

        @Test
        void printsASingleDayWithoutASlash() {
            assertThat(DayRange.singleDay(d("2024-07-04")).format()).isEqualTo("2024-07-04");
        }

        @Test
        void roundTripsAPair() {
            DayRange range = r("2024-07-01", "2024-07-31");
            assertThat(DayRange.parse(range.format())).isEqualTo(range);
        }

        @Test
        void readsABareDayAsASingleDayRange() {
            assertThat(DayRange.parse("2024-07-04")).isEqualTo(DayRange.singleDay(d("2024-07-04")));
        }

        @Test
        void refusesThreePartsAndNamesTheFailingEnd() {
            assertThatThrownBy(() -> DayRange.parse("2024-01-01/2024-01-02/2024-01-03"))
                    .hasMessageContaining("day or a day pair");
            assertThatThrownBy(() -> DayRange.parse("2024-01-01/nope", "window"))
                    .hasMessageContaining("window.to");
        }
    }

    @Nested
    class Normalise {

        @Test
        void returnsNothingForNothing() {
            assertThat(DayRange.normalise(List.of())).isEmpty();
        }

        @Test
        void sortsAndMergesOverlappingWindows() {
            List<DayRange> merged =
                    DayRange.normalise(List.of(r("2024-05-10", "2024-05-12"), r("2024-05-01", "2024-05-11")));
            assertThat(merged).containsExactly(r("2024-05-01", "2024-05-12"));
        }

        @Test
        void mergesWindowsThatOnlyTouch() {
            List<DayRange> merged =
                    DayRange.normalise(List.of(r("2024-05-01", "2024-05-04"), r("2024-05-05", "2024-05-06")));
            assertThat(merged).containsExactly(r("2024-05-01", "2024-05-06"));
        }

        @Test
        void keepsARealGapApart() {
            List<DayRange> merged =
                    DayRange.normalise(List.of(r("2024-05-01", "2024-05-04"), r("2024-05-06", "2024-05-07")));
            assertThat(merged).hasSize(2);
        }

        @Test
        void keepsAWindowSwallowedByAnEarlierOne() {
            List<DayRange> merged =
                    DayRange.normalise(List.of(r("2024-05-01", "2024-05-30"), r("2024-05-10", "2024-05-12")));
            assertThat(merged).containsExactly(r("2024-05-01", "2024-05-30"));
        }
    }

    @Nested
    class CoveredBy {

        private final List<DayRange> shields = List.of(r("2024-05-01", "2024-05-03"), r("2024-06-01", "2024-06-02"));

        @Test
        void isTrueInsideAnyWindow() {
            assertThat(DayRange.coveredBy(shields, d("2024-05-02"))).isTrue();
            assertThat(DayRange.coveredBy(shields, d("2024-06-02"))).isTrue();
        }

        @Test
        void isFalseInTheGapAndOutside() {
            assertThat(DayRange.coveredBy(shields, d("2024-05-20"))).isFalse();
            assertThat(DayRange.coveredBy(List.of(), d("2024-05-20"))).isFalse();
        }
    }

    @Nested
    class Subtract {

        @Test
        void leavesTheRangeAloneWhenTheCutMisses() {
            DayRange range = r("2024-05-01", "2024-05-10");
            assertThat(range.subtract(r("2024-06-01", "2024-06-02"))).containsExactly(range);
        }

        @Test
        void splitsIntoTwoWhenTheCutIsInside() {
            assertThat(r("2024-05-01", "2024-05-10").subtract(r("2024-05-04", "2024-05-05")))
                    .containsExactly(r("2024-05-01", "2024-05-03"), r("2024-05-06", "2024-05-10"));
        }

        @Test
        void trimsOneEndWhenTheCutOverhangs() {
            assertThat(r("2024-05-01", "2024-05-10").subtract(r("2024-04-20", "2024-05-03")))
                    .containsExactly(r("2024-05-04", "2024-05-10"));
        }

        @Test
        void returnsNothingWhenTheCutSwallowsTheRange() {
            assertThat(r("2024-05-04", "2024-05-05").subtract(r("2024-05-01", "2024-05-10")))
                    .isEmpty();
        }
    }
}
