package io.github.rkaushikdev.stemwright.cli;

import java.util.Objects;

/**
 * One column of a {@link Render#table}.
 *
 * @param header the header text
 * @param align the side the values sit against
 */
public record Column(String header, Align align) {

    public Column {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(align, "align");
    }

    /** A left aligned column, which is what text wants. */
    public static Column of(String header) {
        return new Column(header, Align.LEFT);
    }

    public static Column of(String header, Align align) {
        return new Column(header, align);
    }
}
