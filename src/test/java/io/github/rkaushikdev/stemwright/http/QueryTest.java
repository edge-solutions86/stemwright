package io.github.rkaushikdev.stemwright.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import io.github.rkaushikdev.stemwright.domain.time.DayRange;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class QueryTest {

    private static LocalDate d(String value) {
        return CivilDates.parseCivilDate(value);
    }

    @Nested
    class DayFrom {

        @Test
        void readsADay() {
            assertThat(Query.dayFrom(Map.of("on", "2025-03-03"))).contains(d("2025-03-03"));
        }

        @Test
        void givesNothingForAMissingOrEmptyValue() {
            assertThat(Query.dayFrom(Map.of())).isEmpty();
            assertThat(Query.dayFrom(Map.of("on", ""))).isEmpty();
        }

        @Test
        void refusesADayThatIsNotReal() {
            assertThatThrownBy(() -> Query.dayFrom(Map.of("on", "2025-02-30"))).isInstanceOf(ValidationException.class);
        }

        @Test
        void readsTheNameItWasGiven() {
            assertThat(Query.dayFrom(Map.of("since", "2025-03-03"), "since")).contains(d("2025-03-03"));
        }
    }

    @Nested
    class RequireDay {

        @Test
        void readsADayAndRefusesAMissingOne() {
            assertThat(Query.requireDay(Map.of("on", "2025-03-03"))).isEqualTo(d("2025-03-03"));
            assertThatThrownBy(() -> Query.requireDay(Map.of())).hasMessageContaining("on must be given");
        }
    }

    @Nested
    class RequireCourseKey {

        @Test
        void readsTheCourse() {
            assertThat(Query.requireCourseKey(Map.of("course", "latin-1a"))).isEqualTo("latin-1a");
        }

        @Test
        void refusesAMissingOrEmptyCourseWithOneMessage() {
            assertThatThrownBy(() -> Query.requireCourseKey(Map.of()))
                    .hasMessageContaining("course must be given so permissions");
            assertThatThrownBy(() -> Query.requireCourseKey(Map.of("course", "")))
                    .hasMessageContaining("course must be given so permissions");
        }
    }

    @Nested
    class RangeFrom {

        @Test
        void readsBothEnds() {
            assertThat(Query.rangeFrom(Map.of("from", "2025-03-03", "to", "2025-03-09")))
                    .isEqualTo(new DayRange(d("2025-03-03"), d("2025-03-09")));
        }

        @Test
        void refusesAWindowWithOnlyOneEnd() {
            assertThatThrownBy(() -> Query.rangeFrom(Map.of("from", "2025-03-03")))
                    .hasMessageContaining("needs both a from and a to day");
            assertThatThrownBy(() -> Query.rangeFrom(Map.of())).isInstanceOf(ValidationException.class);
        }

        @Test
        void refusesAWindowThatEndsBeforeItStarts() {
            assertThatThrownBy(() -> Query.rangeFrom(Map.of("from", "2025-03-09", "to", "2025-03-03")))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class FlagFrom {

        @Test
        void isTrueForABareFlagTrueAndOne() {
            assertThat(Query.flagFrom(Map.of("verbose", ""), "verbose")).isTrue();
            assertThat(Query.flagFrom(Map.of("verbose", "true"), "verbose")).isTrue();
            assertThat(Query.flagFrom(Map.of("verbose", "1"), "verbose")).isTrue();
        }

        @Test
        void isFalseWhenAbsentOrSetToAnythingElse() {
            assertThat(Query.flagFrom(Map.of(), "verbose")).isFalse();
            assertThat(Query.flagFrom(Map.of("verbose", "false"), "verbose")).isFalse();
            assertThat(Query.flagFrom(Map.of("verbose", "no"), "verbose")).isFalse();
        }
    }

    @Nested
    class PositiveFrom {

        @Test
        void readsAWholeNumberAboveZero() {
            assertThat(Query.positiveFrom(Map.of("limit", "5"), "limit")).contains(5);
        }

        @Test
        void givesNothingWhenAbsentOrEmpty() {
            assertThat(Query.positiveFrom(Map.of(), "limit")).isEmpty();
            assertThat(Query.positiveFrom(Map.of("limit", ""), "limit")).isEmpty();
        }

        @Test
        void refusesZeroANegativeAndAFraction() {
            assertThatThrownBy(() -> Query.positiveFrom(Map.of("limit", "0"), "limit"))
                    .hasMessageContaining("above zero");
            assertThatThrownBy(() -> Query.positiveFrom(Map.of("limit", "-1"), "limit"))
                    .isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> Query.positiveFrom(Map.of("limit", "1.5"), "limit"))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void refusesSomethingThatIsNotANumberAtAll() {
            assertThatThrownBy(() -> Query.positiveFrom(Map.of("limit", "lots"), "limit"))
                    .isInstanceOf(ValidationException.class);
        }
    }
}
