package io.github.rkaushikdev.stemwright.infra.json;

import java.util.List;
import java.util.Optional;

/**
 * A json array: values in order.
 *
 * @param items the values, unmodifiable
 */
public record JsonArray(List<Json> items) implements Json {

    /** The array with nothing in it. */
    public static final JsonArray EMPTY = new JsonArray(List.of());

    public JsonArray {
        items = List.copyOf(items);
    }

    /**
     * The value at the index.
     *
     * @throws IndexOutOfBoundsException when the array is not that long
     */
    public Json get(int index) {
        return items.get(index);
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    @Override
    public Optional<JsonArray> asArray() {
        return Optional.of(this);
    }

    /** The compact json text. */
    @Override
    public String toString() {
        return Json.stringify(this);
    }
}
