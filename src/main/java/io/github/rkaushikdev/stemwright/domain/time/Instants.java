package io.github.rkaushikdev.stemwright.domain.time;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Instants as the journal, the snapshots and the audit trail write them.
 *
 * <p>Every stamp this desk has ever stored is the text JavaScript's {@code Date#toISOString}
 * printed: utc, always three digits of milliseconds, {@code Z} at the end. {@link
 * Instant#toString()} prints the same instant differently (it drops a zero fraction and keeps
 * nanoseconds), and a stamp that changes shape halfway through a journal is a stamp a later reader
 * has to parse two ways.
 */
public final class Instants {

    private static final int LAST_FOUR_DIGIT_YEAR = 9999;

    private Instants() {}

    /**
     * The stamp for an instant, such as {@code 2025-03-03T09:00:00.000Z}. Time below a millisecond
     * is dropped. A year outside 0 to 9999 is written with a sign and six digits, as {@code
     * toISOString} writes it.
     */
    public static String toIsoString(Instant at) {
        LocalDateTime time = LocalDateTime.ofInstant(at.truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC);
        int year = time.getYear();
        String yearText = year >= 0 && year <= LAST_FOUR_DIGIT_YEAR
                ? String.format(Locale.ROOT, "%04d", year)
                : (year < 0 ? "-" : "+") + String.format(Locale.ROOT, "%06d", Math.abs(year));
        return String.format(
                Locale.ROOT,
                "%s-%02d-%02dT%02d:%02d:%02d.%03dZ",
                yearText,
                time.getMonthValue(),
                time.getDayOfMonth(),
                time.getHour(),
                time.getMinute(),
                time.getSecond(),
                time.getNano() / 1_000_000);
    }
}
