package io.github.rkaushikdev.stemwright.infra;

import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.features.FeatureBundle;
import io.github.rkaushikdev.stemwright.domain.features.FeaturePair;
import io.github.rkaushikdev.stemwright.domain.features.Selector;
import io.github.rkaushikdev.stemwright.domain.features.SelectorClause;
import io.github.rkaushikdev.stemwright.domain.lexicon.StemSet;
import io.github.rkaushikdev.stemwright.domain.text.Numbers;
import io.github.rkaushikdev.stemwright.domain.text.Text;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import io.github.rkaushikdev.stemwright.domain.time.DayRange;
import io.github.rkaushikdev.stemwright.infra.json.Json;
import io.github.rkaushikdev.stemwright.infra.json.JsonArray;
import io.github.rkaushikdev.stemwright.infra.json.JsonBoolean;
import io.github.rkaushikdev.stemwright.infra.json.JsonNull;
import io.github.rkaushikdev.stemwright.infra.json.JsonNumber;
import io.github.rkaushikdev.stemwright.infra.json.JsonObject;
import io.github.rkaushikdev.stemwright.infra.json.JsonString;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turning domain values into json and back.
 *
 * <p>Decoding is deliberately suspicious. Everything it reads came off a disk that a previous
 * version of this code wrote, and the one thing that is certain about stored data is that some of it
 * was written by a version that no longer exists. So every decoder checks the shape it was handed
 * rather than trusting the type it is meant to be, and a bad row fails with the field that was wrong
 * rather than with a class cast three layers up.
 *
 * <p>The readers take a row and the path that row was found at, and name {@code path.key} when the
 * field is wrong. A missing field and a json {@code null} read the same: both are absent to an
 * optional reader and wrong to a required one.
 */
public final class Codec {

    /** The largest whole number a double holds exactly. Past it, two neighbouring counts read the same. */
    private static final double LARGEST_EXACT = 9_007_199_254_740_991d;

    private Codec() {}

    /** True for an object, and for nothing else: not an array, not {@code null}. */
    public static boolean isObject(Json value) {
        return value instanceof JsonObject;
    }

    /** The value as an object, refusing anything else under the path. */
    public static JsonObject requireObject(String path, Json value) {
        if (!(value instanceof JsonObject row)) {
            throw ValidationException.field(path, "is not an object");
        }
        return row;
    }

    public static String readString(JsonObject row, String path, String key) {
        if (!(field(row, key) instanceof JsonString string)) {
            throw ValidationException.field(path + "." + key, "is not a string");
        }
        return string.value();
    }

    /** Nothing for a missing or {@code null} field, the string for a string, and a refusal otherwise. */
    public static Optional<String> readOptionalString(JsonObject row, String path, String key) {
        if (isAbsent(row, key)) {
            return Optional.empty();
        }
        return Optional.of(readString(row, path, key));
    }

    /** A number that is finite, refusing an infinity as firmly as a string. */
    public static double readNumber(JsonObject row, String path, String key) {
        if (!(field(row, key) instanceof JsonNumber number) || !number.isFinite()) {
            throw ValidationException.field(path + "." + key, "is not a finite number");
        }
        return number.value();
    }

    public static Optional<Double> readOptionalNumber(JsonObject row, String path, String key) {
        if (isAbsent(row, key)) {
            return Optional.empty();
        }
        return Optional.of(readNumber(row, path, key));
    }

    /**
     * A finite number with no fractional part, small enough that a double still counts it exactly.
     *
     * <p>The original read such fields with {@link #readNumber} and carried a fraction along until
     * something compared it. A Java field that holds a count or a sequence cannot hold the fraction,
     * so this refuses it where it is read, under {@code path.key}: {@code must be a whole number:
     * 2.5}, and for a number past {@code 2^53 - 1} either way, the range it must fall in.
     */
    public static long readWholeNumber(JsonObject row, String path, String key) {
        double value = readNumber(row, path, key);
        if (!Numbers.isInteger(value)) {
            throw ValidationException.field(path + "." + key, "must be a whole number: " + Numbers.format(value));
        }
        if (Math.abs(value) > LARGEST_EXACT) {
            throw ValidationException.field(
                    path + "." + key,
                    "must be between -" + Numbers.format(LARGEST_EXACT) + " and " + Numbers.format(LARGEST_EXACT) + ": "
                            + Numbers.format(value));
        }
        return (long) value;
    }

    public static boolean readBoolean(JsonObject row, String path, String key) {
        if (!(field(row, key) instanceof JsonBoolean flag)) {
            throw ValidationException.field(path + "." + key, "is not a boolean");
        }
        return flag.value();
    }

    public static List<Json> readArray(JsonObject row, String path, String key) {
        if (!(field(row, key) instanceof JsonArray array)) {
            throw ValidationException.field(path + "." + key, "is not an array");
        }
        return array.items();
    }

    /** An array whose every entry is a string, naming the index of the first that is not. */
    public static List<String> readStringArray(JsonObject row, String path, String key) {
        List<Json> entries = readArray(row, path, key);
        List<String> strings = new ArrayList<>(entries.size());
        for (int index = 0; index < entries.size(); index += 1) {
            if (!(entries.get(index) instanceof JsonString string)) {
                throw ValidationException.field(path + "." + key + "." + index, "is not a string");
            }
            strings.add(string.value());
        }
        return List.copyOf(strings);
    }

    /** A study day stored in its iso form, refusing one that is not real. */
    public static LocalDate readDate(JsonObject row, String path, String key) {
        return CivilDates.parseCivilDate(readString(row, path, key), path + "." + key);
    }

    public static Optional<LocalDate> readOptionalDate(JsonObject row, String path, String key) {
        return readOptionalString(row, path, key).map(value -> CivilDates.parseCivilDate(value, path + "." + key));
    }

    public static JsonObject encodeRange(DayRange range) {
        return Json.object()
                .put("from", range.from().toString())
                .put("to", range.to().toString())
                .build();
    }

    public static DayRange decodeRange(Json value, String path) {
        JsonObject row = requireObject(path, value);
        return DayRange.of(readDate(row, path, "from"), readDate(row, path, "to"));
    }

    /**
     * A bundle is stored as its canonical key rather than as a list of pairs. It is shorter, it is the
     * same string the cell id was derived from, and it cannot drift out of the order the schema
     * declares.
     */
    public static JsonString encodeBundle(FeatureBundle bundle) {
        return Json.of(bundle.key());
    }

    /**
     * Reads a stored key back without checking it against a schema, because the codec wrote it from
     * a bundle that had already been checked.
     */
    public static FeatureBundle decodeBundle(Json value, String path) {
        if (!(value instanceof JsonString key)) {
            throw ValidationException.field(path, "is not a bundle key");
        }
        if (key.value().isEmpty()) {
            return FeatureBundle.EMPTY;
        }
        List<FeaturePair> pairs = new ArrayList<>();
        for (String part : Text.split(key.value(), ";")) {
            int at = part.indexOf('=');
            if (at <= 0) {
                throw ValidationException.field(path, "is not a dimension value pair: " + part);
            }
            pairs.add(new FeaturePair(part.substring(0, at), part.substring(at + 1)));
        }
        return FeatureBundle.unsafe(pairs);
    }

    /** Every stem with its role, form and suppletion flag, and its note only when it has one. */
    public static JsonArray encodeStems(StemSet stems) {
        return Json.array(stems.stems().stream()
                .map(stem -> Json.object()
                        .put("role", stem.role())
                        .put("form", stem.form())
                        .put("suppletive", stem.suppletive())
                        .putIfPresent("note", stem.note().map(Json::of))
                        .build())
                .toList());
    }

    public static JsonArray encodeSelector(Selector selection) {
        return Json.array(selection.clauses().stream()
                .map(clause -> Json.object()
                        .put("dimension", clause.dimension())
                        .put("values", Json.strings(clause.values()))
                        .put("negated", clause.negated())
                        .build())
                .toList());
    }

    /**
     * Reads the clauses of a stored selector, checking only their shape. The caller checks them
     * against a schema with {@link Selector#of}, which is where an unknown dimension is refused.
     */
    public static List<SelectorClause> decodeSelectorClauses(Json value, String path) {
        if (!(value instanceof JsonArray array)) {
            throw ValidationException.field(path, "is not an array of clauses");
        }
        List<SelectorClause> clauses = new ArrayList<>(array.size());
        for (int index = 0; index < array.size(); index += 1) {
            String at = path + "." + index;
            JsonObject row = requireObject(at, array.get(index));
            clauses.add(new SelectorClause(
                    readString(row, at, "dimension"),
                    readStringArray(row, at, "values"),
                    readBoolean(row, at, "negated")));
        }
        return List.copyOf(clauses);
    }

    /** The field, with a missing one read as {@code null} as every reader here treats them alike. */
    private static Json field(JsonObject row, String key) {
        return row.get(key).orElse(Json.NULL);
    }

    private static boolean isAbsent(JsonObject row, String key) {
        return field(row, key) instanceof JsonNull;
    }
}
