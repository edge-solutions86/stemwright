package io.github.rkaushikdev.stemwright.infra.json;

import java.util.Optional;

/**
 * A json {@code true} or {@code false}.
 *
 * @param value the flag
 */
public record JsonBoolean(boolean value) implements Json {

    public static final JsonBoolean TRUE = new JsonBoolean(true);
    public static final JsonBoolean FALSE = new JsonBoolean(false);

    @Override
    public Optional<Boolean> asBoolean() {
        return Optional.of(value);
    }

    /** The json text, {@code true} or {@code false}. */
    @Override
    public String toString() {
        return Json.stringify(this);
    }
}
