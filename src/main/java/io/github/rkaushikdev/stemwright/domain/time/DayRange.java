package io.github.rkaushikdev.stemwright.domain.time;

import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.text.Text;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A closed range of study days. Both ends are inclusive, which is what an assignment means when it
 * says "week of the fourth through the tenth", and it removes the empty range as a representable
 * value: a range that ends before it starts cannot be built.
 *
 * @param from the first day in the range
 * @param to the last day in the range, never before {@code from}
 */
public record DayRange(LocalDate from, LocalDate to) {

    public DayRange {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw ValidationException.field("range", "starts after it ends: " + from + " to " + to);
        }
    }

    public static DayRange of(LocalDate from, LocalDate to) {
        return new DayRange(from, to);
    }

    public static DayRange singleDay(LocalDate day) {
        return new DayRange(day, day);
    }

    /** How many days the range holds, both ends counted. */
    public int length() {
        return CivilDates.daysBetween(from, to) + 1;
    }

    public boolean contains(LocalDate day) {
        return !day.isBefore(from) && !day.isAfter(to);
    }

    /** True when the two share at least one day. Ranges that only touch do not overlap. */
    public boolean overlaps(DayRange other) {
        return !from.isAfter(other.to) && !other.from.isAfter(to);
    }

    public Optional<DayRange> intersect(DayRange other) {
        if (!overlaps(other)) {
            return Optional.empty();
        }
        return Optional.of(new DayRange(CivilDates.maxCivil(from, other.from), CivilDates.minCivil(to, other.to)));
    }

    /** The smallest range holding both, gaps included. Used for report headers. */
    public DayRange hull(DayRange other) {
        return new DayRange(CivilDates.minCivil(from, other.from), CivilDates.maxCivil(to, other.to));
    }

    public DayRange shift(long days) {
        return new DayRange(from.plusDays(days), to.plusDays(days));
    }

    /** Every day in the range, in order. */
    public Stream<LocalDate> eachDay() {
        return from.datesUntil(to.plusDays(1));
    }

    public List<LocalDate> days() {
        return eachDay().toList();
    }

    /** A single day prints as itself, a longer range as {@code from/to}. */
    public String format() {
        return from.equals(to) ? from.toString() : from + "/" + to;
    }

    /** The parts of this range that {@code cut} does not cover: none, one or two ranges. */
    public List<DayRange> subtract(DayRange cut) {
        Optional<DayRange> found = intersect(cut);
        if (found.isEmpty()) {
            return List.of(this);
        }
        DayRange overlap = found.get();
        List<DayRange> out = new ArrayList<>(2);
        if (from.isBefore(overlap.from)) {
            out.add(new DayRange(from, overlap.from.minusDays(1)));
        }
        if (overlap.to.isBefore(to)) {
            out.add(new DayRange(overlap.to.plusDays(1), to));
        }
        return List.copyOf(out);
    }

    /** {@link #parse(String, String)} reporting failures under {@code range}. */
    public static DayRange parse(String value) {
        return parse(value, "range");
    }

    /** Reads the form {@link #format()} prints: one day, or two joined by a slash. */
    public static DayRange parse(String value, String path) {
        List<String> parts = Text.split(value, "/");
        if (parts.size() == 1) {
            return singleDay(CivilDates.parseCivilDate(parts.get(0), path));
        }
        if (parts.size() != 2) {
            throw ValidationException.field(path, "is not a day or a day pair: " + value);
        }
        return new DayRange(
                CivilDates.parseCivilDate(parts.get(0), path + ".from"),
                CivilDates.parseCivilDate(parts.get(1), path + ".to"));
    }

    /**
     * Merges overlapping or touching ranges into the smallest covering set, sorted. Shield windows
     * and enrolment windows both arrive as unsorted lists that may abut, and a queue asking "is
     * this day shielded" wants one pass over these.
     */
    public static List<DayRange> normalise(Collection<DayRange> ranges) {
        if (ranges.isEmpty()) {
            return List.of();
        }
        List<DayRange> sorted = new ArrayList<>(ranges);
        sorted.sort(Comparator.comparing(DayRange::from));
        List<DayRange> merged = new ArrayList<>();
        DayRange current = sorted.get(0);
        for (DayRange next : sorted.subList(1, sorted.size())) {
            boolean touches = CivilDates.daysBetween(current.to, next.from) <= 1;
            if (touches) {
                current = new DayRange(current.from, CivilDates.maxCivil(current.to, next.to));
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return List.copyOf(merged);
    }

    public static boolean coveredBy(Collection<DayRange> ranges, LocalDate day) {
        return ranges.stream().anyMatch(range -> range.contains(day));
    }
}
