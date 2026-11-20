package io.github.rkaushikdev.stemwright.domain.time;

import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Study days. A study day is a calendar day with no zone attached: a learner in Lisbon and a
 * learner in Seoul both get a queue for "the fourth of March", and the instant that boundary falls
 * on is resolved later, once per learner.
 *
 * <p>A study day is a {@link LocalDate} between 1583 and 9999, the years for which the Gregorian
 * calendar and the four digit iso form both hold. Its text form is always {@code yyyy-mm-dd}.
 */
public final class CivilDates {

    private static final Pattern PATTERN = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})$");

    private static final int FIRST_YEAR = 1583;
    private static final int LAST_YEAR = 9999;

    private CivilDates() {}

    public static boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
    }

    public static int daysInMonth(int year, int month) {
        return switch (month) {
            case 1, 3, 5, 7, 8, 10, 12 -> 31;
            case 4, 6, 9, 11 -> 30;
            case 2 -> isLeapYear(year) ? 29 : 28;
            default -> throw ValidationException.field("month", "is not a month: " + month);
        };
    }

    /** Builds a study day, refusing a year outside the window and a day the month does not have. */
    public static LocalDate civilDate(int year, int month, int day) {
        if (year < FIRST_YEAR || year > LAST_YEAR) {
            throw ValidationException.field("year", "is out of range: " + year);
        }
        if (month < 1 || month > 12) {
            throw ValidationException.field("month", "is out of range: " + month);
        }
        int last = daysInMonth(year, month);
        if (day < 1 || day > last) {
            throw ValidationException.field("day", "is out of range for " + year + "-" + month + ": " + day);
        }
        return LocalDate.of(year, month, day);
    }

    /** {@link #parseCivilDate(String, String)} reporting failures under {@code date}. */
    public static LocalDate parseCivilDate(String value) {
        return parseCivilDate(value, "date");
    }

    /**
     * Reads the iso form, separating a string of the wrong shape from a well shaped date that cannot
     * exist, and naming the given path in either failure.
     */
    public static LocalDate parseCivilDate(String value, String path) {
        Matcher match = PATTERN.matcher(value);
        if (!match.matches()) {
            throw ValidationException.field(path, "is not an iso calendar date: " + value);
        }
        int year = Integer.parseInt(match.group(1));
        int month = Integer.parseInt(match.group(2));
        int day = Integer.parseInt(match.group(3));
        try {
            return civilDate(year, month, day);
        } catch (ValidationException ignored) {
            throw ValidationException.field(path, "is not a real calendar date: " + value);
        }
    }

    /** True only for a string holding a real study day in the iso form. */
    public static boolean isCivilDate(Object value) {
        if (!(value instanceof String text)) {
            return false;
        }
        try {
            parseCivilDate(text);
            return true;
        } catch (ValidationException ignored) {
            return false;
        }
    }

    /** The year, month and day of a study day as plain numbers. */
    public record CivilParts(int year, int month, int day) {}

    public static CivilParts partsOf(LocalDate date) {
        return new CivilParts(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
    }

    /** Days since 1970-01-01. No zone is involved, so none can shift it. */
    public static long toEpochDay(LocalDate date) {
        return date.toEpochDay();
    }

    public static LocalDate fromEpochDay(long epochDay) {
        try {
            return LocalDate.ofEpochDay(epochDay);
        } catch (DateTimeException error) {
            throw ValidationException.field("epochDay", "is outside the calendar: " + epochDay);
        }
    }

    public static LocalDate addDays(LocalDate date, long days) {
        return date.plusDays(days);
    }

    /** How many days {@code to} lies after {@code from}, negative when it lies before. */
    public static int daysBetween(LocalDate from, LocalDate to) {
        return Math.toIntExact(ChronoUnit.DAYS.between(from, to));
    }

    /** Calendar order as exactly -1, 0 or 1. */
    public static int compareCivil(LocalDate a, LocalDate b) {
        return Integer.signum(a.compareTo(b));
    }

    public static LocalDate minCivil(LocalDate a, LocalDate b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    public static LocalDate maxCivil(LocalDate a, LocalDate b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    /** Monday is 1 and Sunday is 7, as iso 8601 numbers them. */
    public static int weekday(LocalDate date) {
        return date.getDayOfWeek().getValue();
    }

    public static LocalDate startOfWeek(LocalDate date) {
        return addDays(date, 1L - weekday(date));
    }

    /** Moves by whole months, clamping to the last day of a shorter month. */
    public static LocalDate addMonths(LocalDate date, int months) {
        LocalDate moved = date.plusMonths(months);
        return civilDate(moved.getYear(), moved.getMonthValue(), moved.getDayOfMonth());
    }

    /** The utc study day the given instant falls on. */
    public static LocalDate today(Instant now) {
        return LocalDate.ofInstant(now, ZoneOffset.UTC);
    }
}
