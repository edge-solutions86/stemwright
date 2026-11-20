package io.github.rkaushikdev.stemwright.infra.json;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A json object: named values, kept in the order the names were first added, which is the order
 * {@link Json#stringify} writes them in.
 *
 * <p>Equality ignores that order, so two objects are equal when they hold the same names with equal
 * values, which is what a test means when it compares a decoded row with the one it expected.
 *
 * @param fields the named values, unmodifiable and in insertion order
 */
public record JsonObject(Map<String, Json> fields) implements Json {

    /** The object with nothing in it. */
    public static final JsonObject EMPTY = new JsonObject(Map.of());

    public JsonObject {
        Map<String, Json> copy = new LinkedHashMap<>();
        fields.forEach(
                (key, value) -> copy.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value")));
        fields = Collections.unmodifiableMap(copy);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The value under the key, or nothing when the object does not have it. */
    @Override
    public Optional<Json> get(String key) {
        return Optional.ofNullable(fields.get(key));
    }

    /** True when the key is present, whatever its value, a json {@code null} included. */
    public boolean has(String key) {
        return fields.containsKey(key);
    }

    /** The names, in insertion order. */
    public Set<String> keys() {
        return fields.keySet();
    }

    public int size() {
        return fields.size();
    }

    public boolean isEmpty() {
        return fields.isEmpty();
    }

    /** A copy with the key set to the value, keeping the key's position when it was already there. */
    public JsonObject with(String key, Json value) {
        Map<String, Json> copy = new LinkedHashMap<>(fields);
        copy.put(key, value);
        return new JsonObject(copy);
    }

    @Override
    public Optional<JsonObject> asObject() {
        return Optional.of(this);
    }

    /** The compact json text. */
    @Override
    public String toString() {
        return Json.stringify(this);
    }

    /**
     * Builds an object a field at a time. Putting a key a second time replaces its value and keeps
     * its first position, so the printed key order is the order keys were first put in.
     */
    public static final class Builder {

        private final Map<String, Json> fields = new LinkedHashMap<>();

        private Builder() {}

        public Builder put(String key, Json value) {
            fields.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
            return this;
        }

        public Builder put(String key, String value) {
            return put(key, Json.of(value));
        }

        public Builder put(String key, double value) {
            return put(key, Json.of(value));
        }

        public Builder put(String key, long value) {
            return put(key, Json.of(value));
        }

        public Builder put(String key, boolean value) {
            return put(key, Json.of(value));
        }

        /**
         * Puts the value only when there is one, which is how an optional field is left out of a
         * document rather than written as {@code null}.
         */
        public Builder putIfPresent(String key, Optional<? extends Json> value) {
            value.ifPresent(present -> put(key, present));
            return this;
        }

        public JsonObject build() {
            return new JsonObject(fields);
        }
    }
}
