package io.github.rkaushikdev.stemwright.domain.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InstantsTest {

    @ParameterizedTest
    @CsvSource({
        "2025-03-03T09:00:00Z, 2025-03-03T09:00:00.000Z",
        "2025-03-03T09:00:00.5Z, 2025-03-03T09:00:00.500Z",
        "2025-03-03T09:00:00.123456789Z, 2025-03-03T09:00:00.123Z",
        "1970-01-01T00:00:00Z, 1970-01-01T00:00:00.000Z",
        "0001-01-01T00:00:00Z, 0001-01-01T00:00:00.000Z",
        "+10000-01-01T00:00:00Z, +010000-01-01T00:00:00.000Z",
        "-0001-12-31T23:59:59.999Z, -000001-12-31T23:59:59.999Z",
    })
    void printsWhatToIsoStringPrints(String instant, String expected) {
        assertThat(Instants.toIsoString(Instant.parse(instant))).isEqualTo(expected);
    }

    @Test
    void dropsTimeBelowAMillisecondTowardsThePast() {
        Instant justBeforeTheEpoch = Instant.ofEpochSecond(0, -1);
        assertThat(Instants.toIsoString(justBeforeTheEpoch)).isEqualTo("1969-12-31T23:59:59.999Z");
    }
}
