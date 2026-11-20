package io.github.rkaushikdev.stemwright.tooling;

import io.github.rkaushikdev.stemwright.domain.text.Numbers;
import io.github.rkaushikdev.stemwright.domain.text.Text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A benchmark runner small enough to trust.
 *
 * <p>It reports the median rather than the mean, because one garbage collection pause in twenty
 * runs moves a mean by more than most changes worth measuring and moves a median not at all. It also
 * reports the spread, so a reader can see whether the median means anything on the machine it was
 * taken on.
 *
 * <p>Nothing here decides whether a number is good. That belongs to the timing suite, which has
 * ceilings; this is for a person comparing two branches.
 */
public final class Bench {

    /** How many measured runs {@link #bench(String, Runnable)} takes. */
    public static final int DEFAULT_RUNS = 20;

    /** How many runs {@link #bench(String, Runnable)} throws away first. */
    public static final int DEFAULT_WARM_UP = 3;

    private static final String NEWLINE = Character.toString(0x0a);
    private static final double NANOS_PER_MILLI = 1_000_000.0;

    private Bench() {}

    /**
     * The shape of a set of timings, in milliseconds.
     *
     * @param name the benchmark
     * @param runs how many timings there were
     * @param medianMs the middle timing, or the mean of the middle two
     * @param fastestMs the shortest timing
     * @param slowestMs the longest timing
     * @param spreadMs the distance between the shortest and the longest
     */
    public record Summary(String name, int runs, double medianMs, double fastestMs, double slowestMs, double spreadMs) {

        public Summary {
            Objects.requireNonNull(name, "name");
        }
    }

    /** The middle value, the mean of the middle two for an even count, and zero for no values. */
    public static double median(List<Double> values) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compare);
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }

    /** The distance between the largest and the smallest value, and zero for no values. */
    public static double spread(List<Double> values) {
        if (values.isEmpty()) {
            return 0;
        }
        return Collections.max(values) - Collections.min(values);
    }

    /** Summarises a set of timings; every figure is zero when there are none. */
    public static Summary summarise(String name, List<Double> samples) {
        return new Summary(
                name,
                samples.size(),
                median(samples),
                samples.isEmpty() ? 0 : Collections.min(samples),
                samples.isEmpty() ? 0 : Collections.max(samples),
                spread(samples));
    }

    /** One line: the name padded to a column, then the median, the fastest and the spread. */
    public static String formatSummary(Summary summary) {
        return String.join(
                "  ",
                Text.padEnd(summary.name(), 28),
                Numbers.toFixed(summary.medianMs(), 2) + "ms median",
                Numbers.toFixed(summary.fastestMs(), 2) + "ms fastest",
                Numbers.toFixed(summary.spreadMs(), 2) + "ms spread",
                "over " + summary.runs() + " runs");
    }

    /** Runs the body once and returns how long it took, in milliseconds. */
    public static double timeOnce(Runnable body) {
        long started = System.nanoTime();
        body.run();
        return (System.nanoTime() - started) / NANOS_PER_MILLI;
    }

    /** Runs {@value #DEFAULT_WARM_UP} warm up passes, thrown away, then {@value #DEFAULT_RUNS} measured ones. */
    public static Summary bench(String name, Runnable body) {
        return bench(name, body, DEFAULT_RUNS, DEFAULT_WARM_UP);
    }

    /** Runs the warm up passes, which are thrown away, then the measured ones. */
    public static Summary bench(String name, Runnable body, int runs, int warmUp) {
        for (int index = 0; index < warmUp; index += 1) {
            body.run();
        }
        List<Double> samples = new ArrayList<>();
        for (int index = 0; index < runs; index += 1) {
            samples.add(timeOnce(body));
        }
        return summarise(name, samples);
    }

    /**
     * What the entry point prints. A benchmark needs a scenario, and that belongs to whoever is
     * comparing two branches rather than to this class, so it says how to use it rather than
     * guessing.
     */
    public static String usage() {
        return String.join(
                NEWLINE,
                "stemwright bench",
                "",
                "This class is a runner, not a suite. Call bench and summarise from it",
                "and time whatever you are comparing, or run ./mvnw -B -Pperf test for the",
                "timing suite with its ceilings.");
    }

    /** Prints {@link #usage()}. */
    public static void main(String[] args) {
        System.out.print(usage() + NEWLINE);
        System.out.flush();
    }
}
