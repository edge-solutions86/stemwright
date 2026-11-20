package io.github.rkaushikdev.stemwright.domain.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NumbersTest {

    @Nested
    class Format {

        @ParameterizedTest
        @CsvSource({
            "0, 0",
            "-0.0, 0",
            "1, 1",
            "-3, -3",
            "2.5, 2.5",
            "0.1, 0.1",
            "0.30000000000000004, 0.30000000000000004",
            "10000000, 10000000",
            "123456789012345680000, 123456789012345680000",
            "1e21, 1e+21",
            "0.000001, 0.000001",
            "0.00000015, 1.5e-7",
            "-0.00000015, -1.5e-7",
            "1.7976931348623157e308, 1.7976931348623157e+308",
        })
        void printsTheShortestTextThatReadsBackAsTheSameValue(double value, String expected) {
            assertThat(Numbers.format(value)).isEqualTo(expected);
        }

        @Test
        void printsAWholeNumberWithoutAPoint() {
            assertThat(Numbers.format(42L)).isEqualTo("42");
        }

        @Test
        void refusesAValueThatIsNotFinite() {
            assertThatThrownBy(() -> Numbers.format(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Numbers.format(Double.POSITIVE_INFINITY))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class ToFixed {

        @ParameterizedTest
        @CsvSource({
            "2.5, 0, 3",
            "2.45, 1, 2.5",
            "1.005, 2, 1.00",
            "1.25, 1, 1.3",
            "0, 2, 0.00",
            "-0.0, 1, 0.0",
            "-0.04, 1, -0.0",
            "-1.25, 1, -1.3",
            "0.75, 0, 1",
            "33.333333, 1, 33.3",
        })
        void roundsHalfAwayFromZeroOnTheExactValue(double value, int places, String expected) {
            assertThat(Numbers.toFixed(value, places)).isEqualTo(expected);
        }

        @Test
        void refusesANegativeNumberOfPlaces() {
            assertThatThrownBy(() -> Numbers.toFixed(1, -1)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Round {

        @Test
        void sendsATieTowardsPositiveInfinity() {
            assertThat(Numbers.round(2.5)).isEqualTo(3);
            assertThat(Numbers.round(-2.5)).isEqualTo(-2);
            assertThat(Numbers.round(2.4)).isEqualTo(2);
        }
    }
}
