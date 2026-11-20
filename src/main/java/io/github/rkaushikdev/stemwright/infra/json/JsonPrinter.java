package io.github.rkaushikdev.stemwright.infra.json;

import io.github.rkaushikdev.stemwright.domain.text.Numbers;
import java.util.List;
import java.util.Map;

/**
 * The writer behind {@link Json#stringify}, which prints what {@code JSON.stringify} prints.
 *
 * <p>A string escapes the quote, the backslash and the five short control escapes, writes every
 * other unit below a space as a six character escape in lower case hex, writes a lone surrogate the
 * same way, and leaves everything else as it is, non ascii text and the line and paragraph
 * separators included.
 */
final class JsonPrinter {

    /** {@code JSON.stringify} never indents by more than this, whatever it is asked for. */
    private static final int MAX_INDENT = 10;

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private JsonPrinter() {}

    static String print(Json value, int indent) {
        String gap = " ".repeat(Math.max(0, Math.min(MAX_INDENT, indent)));
        StringBuilder out = new StringBuilder();
        write(value, gap, "", out);
        return out.toString();
    }

    private static void write(Json value, String gap, String indent, StringBuilder out) {
        switch (value) {
            case JsonString string -> quote(string.value(), out);
            case JsonNumber number -> out.append(number.isFinite() ? Numbers.format(number.value()) : "null");
            case JsonBoolean flag -> out.append(flag.value());
            case JsonNull nothing -> out.append(nothing);
            case JsonArray array -> writeArray(array.items(), gap, indent, out);
            case JsonObject object -> writeObject(object.fields(), gap, indent, out);
        }
    }

    private static void writeArray(List<Json> items, String gap, String indent, StringBuilder out) {
        if (items.isEmpty()) {
            out.append("[]");
            return;
        }
        String inner = indent + gap;
        out.append('[');
        for (int index = 0; index < items.size(); index += 1) {
            if (index > 0) {
                out.append(',');
            }
            newline(gap, inner, out);
            write(items.get(index), gap, inner, out);
        }
        newline(gap, indent, out);
        out.append(']');
    }

    private static void writeObject(Map<String, Json> fields, String gap, String indent, StringBuilder out) {
        if (fields.isEmpty()) {
            out.append("{}");
            return;
        }
        String inner = indent + gap;
        out.append('{');
        boolean first = true;
        for (Map.Entry<String, Json> field : fields.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            newline(gap, inner, out);
            quote(field.getKey(), out);
            out.append(':');
            if (!gap.isEmpty()) {
                out.append(' ');
            }
            write(field.getValue(), gap, inner, out);
        }
        newline(gap, indent, out);
        out.append('}');
    }

    /** Starts a new line at the given depth, and does nothing at all when printing compactly. */
    private static void newline(String gap, String indent, StringBuilder out) {
        if (!gap.isEmpty()) {
            out.append('\n').append(indent);
        }
    }

    private static void quote(String value, StringBuilder out) {
        out.append('"');
        for (int index = 0; index < value.length(); index += 1) {
            char unit = value.charAt(index);
            switch (unit) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (Character.isHighSurrogate(unit)
                            && index + 1 < value.length()
                            && Character.isLowSurrogate(value.charAt(index + 1))) {
                        out.append(unit).append(value.charAt(index + 1));
                        index += 1;
                    } else if (unit < 0x20 || Character.isSurrogate(unit)) {
                        escape(unit, out);
                    } else {
                        out.append(unit);
                    }
                }
            }
        }
        out.append('"');
    }

    private static void escape(char unit, StringBuilder out) {
        out.append('\\')
                .append('u')
                .append(HEX[(unit >> 12) & 0xf])
                .append(HEX[(unit >> 8) & 0xf])
                .append(HEX[(unit >> 4) & 0xf])
                .append(HEX[unit & 0xf]);
    }
}
