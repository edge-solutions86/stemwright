package io.github.rkaushikdev.stemwright.domain.text;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.regex.Pattern;

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

    /** A decimal as JavaScript's {@code Number(text)} reads one, once the spacing is gone. */
    private static final Pattern DECIMAL =
            Pattern.compile("[+-]?(?:Infinity|(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?)");

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

    /**
     * A number as a message prints it, which is {@link #format(double)} for a finite value and
     * {@code NaN}, {@code Infinity} or {@code -Infinity} otherwise, as JavaScript's {@code String(n)}
     * spells them. A refusal quotes the value it refused, whatever it was.
     */
    public static String text(double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "Infinity" : "-Infinity";
        }
        return format(value);
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

    /**
     * Reads text as a number the way the cli and the query string always have, which is the way
     * JavaScript's {@code Number(text)} does: spacing around the number is ignored and blank text is
     * zero; a decimal may have a sign, a missing integer or fraction part ({@code .5}, {@code 5.})
     * and an exponent; {@code 0x}, {@code 0o} and {@code 0b} introduce an unsigned hex, octal or
     * binary integer; {@code Infinity} reads as an infinity. Anything else, a stray character or a
     * digit separator included, is NaN rather than an exception, so a caller decides what a bad
     * number means.
     */
    public static double parse(String text) {
        String trimmed = Text.trim(text);
        if (trimmed.isEmpty()) {
            return 0;
        }
        if (trimmed.length() > 2 && trimmed.charAt(0) == '0') {
            int radix =
                    switch (trimmed.charAt(1)) {
                        case 'x', 'X' -> 16;
                        case 'o', 'O' -> 8;
                        case 'b', 'B' -> 2;
                        default -> 0;
                    };
            if (radix != 0) {
                return parseUnsigned(trimmed.substring(2), radix);
            }
        }
        return DECIMAL.matcher(trimmed).matches() ? Double.parseDouble(trimmed) : Double.NaN;
    }

    private static double parseUnsigned(String digits, int radix) {
        for (int index = 0; index < digits.length(); index += 1) {
            char digit = digits.charAt(index);
            if (digit >= 0x80 || Character.digit(digit, radix) < 0) {
                return Double.NaN;
            }
        }
        return new BigInteger(digits, radix).doubleValue();
    }

    /** True for a finite value with no fractional part, as JavaScript's {@code Number.isInteger}. */
    public static boolean isInteger(double value) {
        return Double.isFinite(value) && Math.rint(value) == value;
    }

    /** True for a whole number an {@code int} can hold, so a cast to {@code int} loses nothing. */
    public static boolean fitsInt(double value) {
        return isInteger(value) && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
    }
}
