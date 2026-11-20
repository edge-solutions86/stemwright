package io.github.rkaushikdev.stemwright.domain.text;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How numbers are printed and rounded wherever a person or a file will read them.
 *
 * <p>The desk's reports, its json and its cli all print a number the same way: a whole number has
 * no decimal point, a fraction has the shortest digits that read back as the same double, and a
 * fixed number of places rounds half away from zero on the exact binary value. {@link
 * Double#toString(double)} answers a different question ({@code 1.0}, {@code 1.0E7}), so nothing
 * that is shown or stored goes through it.
 */
public final class Numbers {

    private static final double PLAIN_LIMIT = 1e21;

    private Numbers() {}

    /**
     * The shortest text that reads back as the same double: {@code 1}, {@code 2.5}, {@code 0.1},
     * {@code 1e+21}, {@code 1.5e-7}.
     *
     * @throws IllegalArgumentException for a value that is not finite, which has no such text
     */
    public static String format(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("cannot format a value that is not finite: " + value);
        }
        if (value == 0) {
            return "0";
        }
        if (value < 0) {
            return "-" + format(-value);
        }
        BigDecimal shortest = new BigDecimal(Double.toString(value)).stripTrailingZeros();
        String digits = shortest.unscaledValue().toString();
        int count = digits.length();
        int exponent = count - shortest.scale();
        if (count <= exponent && exponent <= 21) {
            return digits + "0".repeat(exponent - count);
        }
        if (0 < exponent && exponent <= 21) {
            return digits.substring(0, exponent) + "." + digits.substring(exponent);
        }
        if (-6 < exponent && exponent <= 0) {
            return "0." + "0".repeat(-exponent) + digits;
        }
        String mantissa = count == 1 ? digits : digits.charAt(0) + "." + digits.substring(1);
        int shown = exponent - 1;
        return mantissa + "e" + (shown < 0 ? "-" : "+") + Math.abs(shown);
    }

    /** {@link #format(double)} for a whole number. */
    public static String format(long value) {
        return Long.toString(value);
    }

    /**
     * Prints with exactly the given number of places, rounding half away from zero on the exact
     * value the double holds, so {@code toFixed(1.005, 2)} is {@code 1.00} because the double is
     * a little under 1.005.
     */
    public static String toFixed(double value, int places) {
        if (places < 0 || places > 100) {
            throw new IllegalArgumentException("places must be between 0 and 100: " + places);
        }
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("cannot format a value that is not finite: " + value);
        }
        if (Math.abs(value) >= PLAIN_LIMIT) {
            return format(value);
        }
        boolean negative = value < 0;
        String magnitude = new BigDecimal(Math.abs(value))
                .setScale(places, RoundingMode.HALF_UP)
                .toPlainString();
        return negative ? "-" + magnitude : magnitude;
    }

    /** Rounds to the nearest whole number, a tie going towards positive infinity. */
    public static long round(double value) {
        return Math.round(value);
    }
}
