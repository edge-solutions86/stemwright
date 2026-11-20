package io.github.rkaushikdev.stemwright.tooling;

import static io.github.rkaushikdev.stemwright.tooling.Bench.bench;
import static io.github.rkaushikdev.stemwright.tooling.Bench.formatSummary;
import static io.github.rkaushikdev.stemwright.tooling.Bench.median;
import static io.github.rkaushikdev.stemwright.tooling.Bench.spread;
import static io.github.rkaushikdev.stemwright.tooling.Bench.summarise;
import static io.github.rkaushikdev.stemwright.tooling.Bench.timeOnce;
import static io.github.rkaushikdev.stemwright.tooling.Bench.usage;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.tooling.Bench.Summary;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BenchTest {

    @Nested
    class Median {

        @Test
        void takesTheMiddleOfAnOddList() {
            assertThat(median(List.of(3.0, 1.0, 2.0))).isEqualTo(2.0);
        }

        @Test
        void averagesTheMiddleTwoOfAnEvenList() {
            assertThat(median(List.of(1.0, 2.0, 3.0, 4.0))).isEqualTo(2.5);
        }

        @Test
        void isZeroForAnEmptyList() {
            assertThat(median(List.of())).isEqualTo(0.0);
        }

        @Test
        void isUnmovedByOneWildOutlier() {
            assertThat(median(List.of(1.0, 1.0, 1.0, 1.0, 900.0))).isEqualTo(1.0);
        }
    }

    @Nested
    class Spread {

        @Test
        void isTheDistanceBetweenTheExtremes() {
            assertThat(spread(List.of(2.0, 9.0, 4.0))).isEqualTo(7.0);
        }

        @Test
        void isZeroForOneSampleAndForNone() {
            assertThat(spread(List.of(5.0))).isEqualTo(0.0);
            assertThat(spread(List.of())).isEqualTo(0.0);
        }
    }

    @Nested
    class Summarise {

        @Test
        void reportsTheShapeOfTheSamples() {
            Summary summary = summarise("queue", List.of(4.0, 1.0, 2.0, 3.0));
            assertThat(summary).isEqualTo(new Summary("queue", 4, 2.5, 1, 4, 3));
        }

        @Test
        void reportsZerosForNoSamples() {
            Summary summary = summarise("queue", List.of());
            assertThat(summary.runs()).isZero();
            assertThat(summary.medianMs()).isEqualTo(0.0);
            assertThat(summary.spreadMs()).isEqualTo(0.0);
        }
    }

    @Nested
    class FormatSummary {

        @Test
        void namesTheBenchmarkAndTheMedianFirst() {
            String line = formatSummary(summarise("queue", List.of(1.0, 2.0, 3.0)));
            assertThat(line).startsWith("queue");
            assertThat(line).contains("2.00ms median");
            assertThat(line).contains("over 3 runs");
        }
    }

    @Nested
    class TimeOnce {

        @Test
        void returnsANumberThatIsNeverNegative() {
            assertThat(timeOnce(() -> {})).isGreaterThanOrEqualTo(0.0);
        }

        @Test
        void runsTheBodyExactlyOnce() {
            AtomicInteger calls = new AtomicInteger();
            timeOnce(calls::incrementAndGet);
            assertThat(calls.get()).isEqualTo(1);
        }
    }

    @Nested
    class BenchRuns {

        @Test
        void runsTheWarmUpPassesAndTheMeasuredOnes() {
            AtomicInteger calls = new AtomicInteger();
            Summary summary = bench("counting", calls::incrementAndGet, 5, 2);
            assertThat(calls.get()).isEqualTo(7);
            assertThat(summary.runs()).isEqualTo(5);
        }

        @Test
        void throwsTheWarmUpAwayRatherThanMeasuringIt() {
            Summary summary = bench("counting", () -> {}, 4, 3);
            assertThat(summary.runs()).isEqualTo(4);
        }

        @Test
        void namesTheBenchmarkItWasGiven() {
            assertThat(bench("queue assembly", () -> {}, 2, 0).name()).isEqualTo("queue assembly");
        }
    }

    @Nested
    class Usage {

        @Test
        void saysWhatTheClassIsFor() {
            assertThat(usage()).contains("runner, not a suite");
            assertThat(usage()).contains("./mvnw -B -Pperf test");
        }
    }
}
