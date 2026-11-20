package io.github.rkaushikdev.stemwright.infra.json;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * A json value: what the journal, the snapshots, the configuration file and the http bodies are
 * written in.
 *
 * <p>The model is the six shapes json has, as records under one sealed interface, so a decoder can
 * switch over them exhaustively and the compiler says when a case is missing. It is immutable and
 * compares by value: two objects are equal when they hold the same keys with equal values in any
 * order, the way a test compares two parsed documents.
 *
 * <p>{@link #parse} and {@link #stringify} reproduce what {@code JSON.parse} and {@code
 * JSON.stringify} do in the TypeScript this desk was first written in, byte for byte, because the
 * journal on disk and every response body on the wire were written by them. Numbers are doubles and
 * print through {@link io.github.rkaushikdev.stemwright.domain.text.Numbers#format(double)}. The
 * one difference is key order: an object keeps the order its keys were added in, where a JavaScript
 * object would move keys that look like array indices to the front. Nothing this desk stores is
 * keyed by a bare number.
 */
public sealed interface Json permits JsonString, JsonNumber, JsonBoolean, JsonNull, JsonArray, JsonObject {

    /** The json {@code null}. */
    JsonNull NULL = JsonNull.INSTANCE;

    static JsonString of(String value) {
        return new JsonString(value);
    }

    static JsonNumber of(double value) {
        return new JsonNumber(value);
    }

    static JsonNumber of(long value) {
        return new JsonNumber(value);
    }

    static JsonBoolean of(boolean value) {
        return value ? JsonBoolean.TRUE : JsonBoolean.FALSE;
    }

    static JsonArray array(Json... items) {
        return new JsonArray(Arrays.asList(items));
    }

    static JsonArray array(Collection<? extends Json> items) {
        return new JsonArray(List.copyOf(items));
    }

    /** An array of strings, in the order given. */
    static JsonArray strings(Collection<String> values) {
        return new JsonArray(values.stream().<Json>map(Json::of).toList());
    }

    /** A builder for an object, which keeps the keys in the order they are put. */
    static JsonObject.Builder object() {
        return JsonObject.builder();
    }

    /**
     * Reads json text as {@code JSON.parse} does: the whole text must be one value, with only spaces,
     * tabs and line ends around it; numbers become doubles, and a key written twice keeps its first
     * position and its last value.
     *
     * @throws JsonSyntaxException when the text is not json. It is not a domain failure, because the
     *     caller knows what the text was and is the one to say what it means that it was broken.
     */
    static Json parse(String text) {
        return JsonParser.parse(text);
    }

    /** The compact text {@code JSON.stringify(value)} prints, with no space anywhere. */
    static String stringify(Json value) {
        return JsonPrinter.print(value, 0);
    }

    /**
     * The text {@code JSON.stringify(value, null, indent)} prints: one entry per line, nested by the
     * given number of spaces (at most ten), {@code "key": value} with one space, and an empty array or
     * object kept on one line as {@code []} or {@code {}}. An indent below one prints compactly.
     */
    static String stringify(Json value, int indent) {
        return JsonPrinter.print(value, indent);
    }

    /** The text when this is a string, otherwise nothing. */
    default Optional<String> asString() {
        return Optional.empty();
    }

    /** The number when this is a number, otherwise nothing. */
    default Optional<Double> asNumber() {
        return Optional.empty();
    }

    /** The flag when this is a boolean, otherwise nothing. */
    default Optional<Boolean> asBoolean() {
        return Optional.empty();
    }

    /** This array, or nothing when it is something else. */
    default Optional<JsonArray> asArray() {
        return Optional.empty();
    }

    /** This object, or nothing when it is something else. */
    default Optional<JsonObject> asObject() {
        return Optional.empty();
    }

    /**
     * The value under the key when this is an object that has it, otherwise nothing, so a caller can
     * look into a body it has not yet checked the shape of.
     */
    default Optional<Json> get(String key) {
        return Optional.empty();
    }
}
