package io.github.rkaushikdev.stemwright.infra.json;

import java.util.Objects;
import java.util.Optional;

/**
 * A json string. It holds any sequence of utf-16 units, a lone surrogate included, because {@code
 * JSON.parse} accepts one and the journal has to read back whatever it was given.
 *
 * @param value the text
 */
public record JsonString(String value) implements Json {

    public JsonString {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public Optional<String> asString() {
        return Optional.of(value);
    }

    /** The json text, quoted and escaped. */
    @Override
    public String toString() {
        return Json.stringify(this);
    }
}
