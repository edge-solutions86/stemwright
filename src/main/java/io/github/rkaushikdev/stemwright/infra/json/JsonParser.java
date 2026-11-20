package io.github.rkaushikdev.stemwright.infra.json;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The reader behind {@link Json#parse}: the json grammar exactly, nothing more lenient.
 *
 * <p>It keeps the containers it is inside on a stack of its own rather than on the call stack, so a
 * request body that opens a hundred thousand arrays is read like any other document instead of
 * overflowing the thread that is serving it.
 */
final class JsonParser {

    private final String text;
    private int position;

    private JsonParser(String text) {
        this.text = text;
    }

    static Json parse(String text) {
        return new JsonParser(text).document();
    }

    private Json document() {
        Deque<Container> open = new ArrayDeque<>();
        while (true) {
            skipSpace();
            Json value;
            char opening = peek();
            if (opening == '[') {
                position += 1;
                skipSpace();
                if (!consume(']')) {
                    open.push(new ArrayContainer());
                    continue;
                }
                value = JsonArray.EMPTY;
            } else if (opening == '{') {
                position += 1;
                skipSpace();
                if (!consume('}')) {
                    ObjectContainer object = new ObjectContainer();
                    object.pendingKey = key();
                    open.push(object);
                    continue;
                }
                value = JsonObject.EMPTY;
            } else {
                value = scalar();
            }

            while (true) {
                Container innermost = open.peek();
                if (innermost == null) {
                    skipSpace();
                    if (position < text.length()) {
                        throw unexpected();
                    }
                    return value;
                }
                innermost.add(value);
                skipSpace();
                char next = peek();
                position += 1;
                if (next == ',') {
                    if (innermost instanceof ObjectContainer object) {
                        skipSpace();
                        object.pendingKey = key();
                    }
                    break;
                }
                if (next != innermost.closer()) {
                    position -= 1;
                    throw unexpected();
                }
                open.pop();
                value = innermost.build();
            }
        }
    }

    /** Reads a member name and the colon after it. */
    private String key() {
        if (peek() != '"') {
            throw unexpected();
        }
        String key = string();
        skipSpace();
        if (!consume(':')) {
            throw unexpected();
        }
        return key;
    }

    private Json scalar() {
        char first = peek();
        if (first == '"') {
            return new JsonString(string());
        }
        if (first == '-' || isDigit(first)) {
            return new JsonNumber(number());
        }
        if (literal("true")) {
            return JsonBoolean.TRUE;
        }
        if (literal("false")) {
            return JsonBoolean.FALSE;
        }
        if (literal("null")) {
            return JsonNull.INSTANCE;
        }
        throw unexpected();
    }

    private boolean literal(String word) {
        if (text.startsWith(word, position)) {
            position += word.length();
            return true;
        }
        return false;
    }

    private String string() {
        position += 1;
        StringBuilder out = new StringBuilder();
        int runStart = position;
        while (true) {
            if (position >= text.length()) {
                throw new JsonSyntaxException("unterminated string", position);
            }
            char character = text.charAt(position);
            if (character == '"') {
                out.append(text, runStart, position);
                position += 1;
                return out.toString();
            }
            if (character == '\\') {
                out.append(text, runStart, position);
                position += 1;
                out.append(escape());
                runStart = position;
                continue;
            }
            if (character < 0x20) {
                throw new JsonSyntaxException("bad control character in string", position);
            }
            position += 1;
        }
    }

    private char escape() {
        if (position >= text.length()) {
            throw new JsonSyntaxException("unterminated string", position);
        }
        char marker = text.charAt(position);
        position += 1;
        return switch (marker) {
            case '"' -> '"';
            case '\\' -> '\\';
            case '/' -> '/';
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case 'u' -> unicodeEscape();
            default -> throw new JsonSyntaxException("bad escaped character", position - 1);
        };
    }

    private char unicodeEscape() {
        int unit = 0;
        for (int digit = 0; digit < 4; digit += 1) {
            if (position >= text.length() || !isAsciiHex(text.charAt(position))) {
                throw new JsonSyntaxException("bad unicode escape", position);
            }
            unit = unit * 16 + Character.digit(text.charAt(position), 16);
            position += 1;
        }
        return (char) unit;
    }

    private double number() {
        int start = position;
        consume('-');
        // A leading zero stands alone, so a digit after it is not part of this number.
        if (!consume('0') && !digits()) {
            throw new JsonSyntaxException("no number after minus sign", position);
        }
        if (consume('.')) {
            if (!digits()) {
                throw new JsonSyntaxException("unterminated fractional number", position);
            }
        }
        if (consume('e') || consume('E')) {
            if (!consume('+')) {
                consume('-');
            }
            if (!digits()) {
                throw new JsonSyntaxException("exponent part is missing a number", position);
            }
        }
        return Double.parseDouble(text.substring(start, position));
    }

    /** Reads a run of ascii digits and says whether there was at least one. */
    private boolean digits() {
        int start = position;
        while (position < text.length() && isDigit(text.charAt(position))) {
            position += 1;
        }
        return position > start;
    }

    private void skipSpace() {
        while (position < text.length()) {
            char character = text.charAt(position);
            if (character != ' ' && character != '\t' && character != '\n' && character != '\r') {
                return;
            }
            position += 1;
        }
    }

    private boolean consume(char expected) {
        if (position < text.length() && text.charAt(position) == expected) {
            position += 1;
            return true;
        }
        return false;
    }

    /** The character at the current position, which must exist. */
    private char peek() {
        if (position >= text.length()) {
            throw new JsonSyntaxException("unexpected end of json input", position);
        }
        return text.charAt(position);
    }

    private JsonSyntaxException unexpected() {
        if (position >= text.length()) {
            return new JsonSyntaxException("unexpected end of json input", position);
        }
        char found = text.charAt(position);
        String shown =
                found >= 0x20 && found < 0x7f ? "'" + found + "'" : String.format(Locale.ROOT, "U+%04X", (int) found);
        return new JsonSyntaxException("unexpected character " + shown, position);
    }

    private static boolean isDigit(char character) {
        return character >= '0' && character <= '9';
    }

    private static boolean isAsciiHex(char character) {
        return isDigit(character) || (character >= 'a' && character <= 'f') || (character >= 'A' && character <= 'F');
    }

    /** An array or object that has been opened and not yet closed. */
    private sealed interface Container permits ArrayContainer, ObjectContainer {

        void add(Json value);

        char closer();

        Json build();
    }

    private static final class ArrayContainer implements Container {

        private final List<Json> items = new ArrayList<>();

        @Override
        public void add(Json value) {
            items.add(value);
        }

        @Override
        public char closer() {
            return ']';
        }

        @Override
        public Json build() {
            return new JsonArray(items);
        }
    }

    private static final class ObjectContainer implements Container {

        private final Map<String, Json> fields = new LinkedHashMap<>();
        private String pendingKey;

        /** A repeated key keeps the position it first had and takes the value it was given last. */
        @Override
        public void add(Json value) {
            fields.put(pendingKey, value);
        }

        @Override
        public char closer() {
            return '}';
        }

        @Override
        public Json build() {
            return new JsonObject(fields);
        }
    }
}
