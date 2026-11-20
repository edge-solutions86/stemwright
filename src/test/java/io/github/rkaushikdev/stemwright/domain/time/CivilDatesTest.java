package io.github.rkaushikdev.stemwright.domain.time;

import static io.github.rkaushikdev.stemwright.domain.time.CivilDates.addDays;
import static io.github.rkaushikdev.stemwright.domain.time.CivilDates.addMonths;
import static io.github.rkaushikdev.stemwright.domain.time.CivilDates.civilDate;
import static io.github.rkaushikdev.stemwright.domain.time.CivilDates.compareCivil;
import static io.github.rkaushikdev.stemwright.domain.time.CivilDates.daysBetween;
import static io.github.rkaushikdev.stemwright.domain.time.CivilDates.daysInMonth;
import static io.github.rkaushikdev.stemwright.domain.time.CivilDates.fromEpochDay;
import static io.github.rkaushikdev.stemwright.domain.time.CivilDates.isCivilDate;
import static io.github.rkaushikdev.stemwright.domain.time.CivilDates.isLeapYear;
import static io.github.rkaushikdev.stemwright.domain.time.CivilDates.maxCivil;
import static io.github.rkaushikdev.stemwright.domain.time.CivilDates.minCivil;
import static io.github.rkaushikdev.stemwright.domain.time.CivilDates.parseCivilDate;
import static io.github.rkaushikdev.stemwright.domain.time.CivilDates.partsOf;
import static io.github.rkaushikdev.stemwright.domain.time.CivilDates.startOfWeek;
import static io.github.rkaushikdev.stemwright.domain.time.CivilDates.toEpochDay;
import static io.github.rkaushikdev.stemwright.domain.time.CivilDates.today;
import static io.github.rkaushikdev.stemwright.domain.time.CivilDates.weekday;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates.CivilParts;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CivilDatesTest {

    @Nested
    class IsLeapYear {

        @Test
        void followsTheGregorianRuleAtEveryBranch() {
            assertThat(isLeapYear(2024)).isTrue();
            assertThat(isLeapYear(2025)).isFalse();
            assertThat(isLeapYear(1900)).isFalse();
            assertThat(isLeapYear(2000)).isTrue();
        }
    }

    @Nested
    class DaysInMonth {

        @Test
        void knowsFebruaryInBothKindsOfYear() {
            assertThat(daysInMonth(2024, 2)).isEqualTo(29);
            assertThat(daysInMonth(2023, 2)).isEqualTo(28);
        }

        @Test
        void knowsTheThirtyAndThirtyOneDayMonths() {
            assertThat(daysInMonth(2024, 4)).isEqualTo(30);
            assertThat(daysInMonth(2024, 12)).isEqualTo(31);
        }

        @Test
        void refusesAMonthOutsideOneToTwelve() {
            assertThatThrownBy(() -> daysInMonth(2024, 0)).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> daysInMonth(2024, 13)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class CivilDate {

        @Test
        void padsToTheIsoForm() {
            assertThat(civilDate(2024, 4, 8)).hasToString("2024-04-08");
        }

        @Test
        void refusesADayTheMonthDoesNotHave() {
            assertThatThrownBy(() -> civilDate(2023, 2, 29)).hasMessageContaining("day is out of range");
            assertThatCode(() -> civilDate(2024, 2, 29)).doesNotThrowAnyException();
        }

        @Test
        void refusesNonIntegersAndYearsOutsideTheGregorianWindow() {
            assertThatThrownBy(() -> civilDate(1500, 1, 1)).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> civilDate(10_000, 1, 1)).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> civilDate(2024, 1, 0)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class ParseCivilDate {

        @Test
        void readsAWellFormedDate() {
            assertThat(parseCivilDate("2025-02-28")).hasToString("2025-02-28");
        }

        @Test
        void refusesAShapeThatIsNotTheIsoOne() {
            assertThatThrownBy(() -> parseCivilDate("2025-2-8")).hasMessageContaining("iso calendar date");
            assertThatThrownBy(() -> parseCivilDate("2025-02-28T00:00:00Z")).hasMessageContaining("iso calendar date");
            assertThatThrownBy(() -> parseCivilDate("")).isInstanceOf(ValidationException.class);
        }

        @Test
        void separatesABadShapeFromADateThatCannotExist() {
            assertThatThrownBy(() -> parseCivilDate("2025-02-30")).hasMessageContaining("not a real calendar date");
        }

        @Test
        void namesThePathItWasGiven() {
            assertThatThrownBy(() -> parseCivilDate("nope", "assignment.dueOn"))
                    .hasMessageContaining("assignment.dueOn");
        }
    }

    @Nested
    class IsCivilDate {

        @Test
        void isTrueOnlyForARealIsoDay() {
            assertThat(isCivilDate("2024-04-08")).isTrue();
            assertThat(isCivilDate("2024-04-31")).isFalse();
            assertThat(isCivilDate(20240408)).isFalse();
        }
    }

    @Nested
    class PartsOf {

        @Test
        void splitsTheDateBackIntoNumbers() {
            assertThat(partsOf(civilDate(2025, 12, 31))).isEqualTo(new CivilParts(2025, 12, 31));
        }
    }

    @Nested
    class EpochDayRoundTrip {

        @Test
        void putsTheEpochAtZero() {
            assertThat(toEpochDay(civilDate(1970, 1, 1))).isZero();
            assertThat(fromEpochDay(0)).hasToString("1970-01-01");
        }

        @Test
        void roundTripsAcrossALeapDayAndACenturyBoundary() {
            for (String date : List.of("2024-02-28", "2024-02-29", "2024-03-01", "2000-02-29", "1999-12-31")) {
                LocalDate parsed = parseCivilDate(date);
                assertThat(fromEpochDay(toEpochDay(parsed))).isEqualTo(parsed);
            }
        }

        @Test
        void refusesAnEpochDayTheCalendarCannotHold() {
            assertThatThrownBy(() -> fromEpochDay(Long.MAX_VALUE)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class AddDaysAndDaysBetween {

        @Test
        void crossesAMonthEnd() {
            assertThat(addDays(parseCivilDate("2024-01-31"), 1)).hasToString("2024-02-01");
        }

        @Test
        void crossesALeapDay() {
            assertThat(addDays(parseCivilDate("2024-02-28"), 1)).hasToString("2024-02-29");
            assertThat(addDays(parseCivilDate("2023-02-28"), 1)).hasToString("2023-03-01");
        }

        @Test
        void goesBackwards() {
            assertThat(addDays(parseCivilDate("2024-03-01"), -1)).hasToString("2024-02-29");
        }

        @Test
        void countsTheGapInBothDirections() {
            LocalDate from = parseCivilDate("2024-04-08");
            LocalDate to = parseCivilDate("2024-05-08");
            assertThat(daysBetween(from, to)).isEqualTo(30);
            assertThat(daysBetween(to, from)).isEqualTo(-30);
            assertThat(daysBetween(from, from)).isZero();
        }
    }

    @Nested
    class OrderingHelpers {

        @Test
        void comparesAsCalendarOrder() {
            LocalDate early = parseCivilDate("2024-04-08");
            LocalDate late = parseCivilDate("2024-11-01");
            assertThat(compareCivil(early, late)).isEqualTo(-1);
            assertThat(compareCivil(late, early)).isEqualTo(1);
            assertThat(compareCivil(early, early)).isZero();
            assertThat(minCivil(early, late)).isEqualTo(early);
            assertThat(maxCivil(early, late)).isEqualTo(late);
        }
    }

    @Nested
    class Weekday {

        @Test
        void putsMondayAtOneAndSundayAtSeven() {
            assertThat(weekday(parseCivilDate("2024-04-08"))).isEqualTo(1);
            assertThat(weekday(parseCivilDate("2024-04-14"))).isEqualTo(7);
        }

        @Test
        void walksAWholeWeekWithoutRepeating() {
            LocalDate start = parseCivilDate("2024-04-08");
            Set<Integer> seen = new HashSet<>();
            for (int offset = 0; offset < 7; offset += 1) {
                seen.add(weekday(addDays(start, offset)));
            }
            assertThat(seen).hasSize(7);
        }

        @Test
        void staysRightWellBeforeTheEpoch() {
            assertThat(weekday(parseCivilDate("1969-07-20"))).isEqualTo(7);
        }
    }

    @Nested
    class StartOfWeek {

        @Test
        void returnsTheMondayOfTheContainingWeek() {
            assertThat(startOfWeek(parseCivilDate("2024-04-11"))).hasToString("2024-04-08");
            assertThat(startOfWeek(parseCivilDate("2024-04-08"))).hasToString("2024-04-08");
            assertThat(startOfWeek(parseCivilDate("2024-04-14"))).hasToString("2024-04-08");
        }
    }

    @Nested
    class AddMonths {

        @Test
        void clampsToTheLastDayOfAShorterMonth() {
            assertThat(addMonths(parseCivilDate("2024-01-31"), 1)).hasToString("2024-02-29");
            assertThat(addMonths(parseCivilDate("2023-01-31"), 1)).hasToString("2023-02-28");
        }

        @Test
        void crossesYearsInBothDirections() {
            assertThat(addMonths(parseCivilDate("2024-11-15"), 3)).hasToString("2025-02-15");
            assertThat(addMonths(parseCivilDate("2024-02-15"), -3)).hasToString("2023-11-15");
        }
    }

    @Nested
    class Today {

        @Test
        void readsTheUtcDayOffTheClockItIsHanded() {
            assertThat(today(Instant.parse("2025-03-09T23:30:00Z"))).hasToString("2025-03-09");
            assertThat(today(Instant.parse("2025-03-10T00:30:00Z"))).hasToString("2025-03-10");
        }
    }
}
