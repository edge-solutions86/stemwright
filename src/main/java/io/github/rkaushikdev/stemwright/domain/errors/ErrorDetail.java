package io.github.rkaushikdev.stemwright.domain.errors;

import java.util.Objects;

/**
 * One field level problem: where it is, and what is wrong with it.
 *
 * @param path the dotted path of the field that was wrong, such as {@code bundle.case}
 * @param message what is wrong with it, written to follow the path
 */
public record ErrorDetail(String path, String message) {

    public ErrorDetail {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(message, "message");
    }
}
