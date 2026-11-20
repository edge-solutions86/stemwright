package io.github.rkaushikdev.stemwright.infra.json;

import java.util.Optional;

/**
 * A json number, which is an IEEE 754 double, as every number the desk stores is.
 *
 * <p>It can hold a value json cannot write, an infinity or a NaN, because a value is often built
 * before anything checks it; {@link Json#stringify} prints such a value as {@code null}. Two
 * numbers are equal by {@link Double#compare}, so {@code 0} and {@code -0} differ and a NaN equals
 * itself.
 *
 * @param value the number
 */
public record JsonNumber(double value) implements Json {

    /** True unless the value is an infinity or a NaN. */
    public boolean isFinite() {
        return Double.isFinite(value);
    }

    @Override
    public Optional<Double> asNumber() {
        return Optional.of(value);
    }

    /** The json text, which is {@code null} for a value that is not finite. */
    @Override
    public String toString() {
        return Json.stringify(this);
    }
}
