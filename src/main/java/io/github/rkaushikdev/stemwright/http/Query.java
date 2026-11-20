package io.github.rkaushikdev.stemwright.http;

import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.text.Numbers;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import io.github.rkaushikdev.stemwright.domain.time.DayRange;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * Reading the query string.
 *
 * <p>Two route modules had their own copy of each of these and they had already drifted: one of them
 * named the missing course {@code course} and the other named it {@code courseKey}, so the same
 * mistake reached a client under two different messages depending on which endpoint it hit.
 */
public final class Query {

    private Query() {}

    /** {@link #dayFrom(Map, String)} for the {@code on} parameter. */
    public static Optional<LocalDate> dayFrom(Map<String, String> query) {
        return dayFrom(query, "on");
    }

    /** The study day under the name, nothing when it is missing or empty, and a refusal when it is not real. */
    public static Optional<LocalDate> dayFrom(Map<String, String> query, String name) {
        String value = query.get(name);
        return value == null || value.isEmpty()
                ? Optional.empty()
                : Optional.of(CivilDates.parseCivilDate(value, name));
    }

    /** {@link #requireDay(Map, String)} for the {@code on} parameter. */
    public static LocalDate requireDay(Map<String, String> query) {
        return requireDay(query, "on");
    }

    public static LocalDate requireDay(Map<String, String> query, String name) {
        return dayFrom(query, name)
                .orElseThrow(() -> ValidationException.field(name, "must be given as an iso calendar date"));
    }

    /** The course the request is about, which every permission check needs. */
    public static String requireCourseKey(Map<String, String> query) {
        String value = query.get("course");
        if (value == null || value.isEmpty()) {
            throw ValidationException.field("course", "must be given so permissions can be checked");
        }
        return value;
    }

    /** The days {@code from} through {@code to}, both of which must be given. */
    public static DayRange rangeFrom(Map<String, String> query) {
        String from = query.get("from");
        String to = query.get("to");
        if (from == null || to == null) {
            throw ValidationException.field("range", "needs both a from and a to day");
        }
        return DayRange.of(CivilDates.parseCivilDate(from, "from"), CivilDates.parseCivilDate(to, "to"));
    }

    /** True for a bare flag ({@code ?verbose}), {@code true} and {@code 1}, false for anything else. */
    public static boolean flagFrom(Map<String, String> query, String name) {
        String value = query.get(name);
        return "".equals(value) || "true".equals(value) || "1".equals(value);
    }

    /**
     * A whole number above zero, read as JavaScript's {@code Number} reads it, or nothing when the
     * parameter is missing or empty. A count is an {@code int}, so a number past {@link
     * Integer#MAX_VALUE} is refused with the same message.
     */
    public static Optional<Integer> positiveFrom(Map<String, String> query, String name) {
        String value = query.get(name);
        if (value == null || value.isEmpty()) {
            return Optional.empty();
        }
        double parsed = Numbers.parse(value);
        if (!Numbers.isInteger(parsed) || parsed < 1 || parsed > Integer.MAX_VALUE) {
            throw ValidationException.field(name, "must be a whole number above zero: " + value);
        }
        return Optional.of((int) parsed);
    }
}
