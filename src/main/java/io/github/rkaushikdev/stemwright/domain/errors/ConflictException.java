package io.github.rkaushikdev.stemwright.domain.errors;

import java.util.List;

/** The write would collide with something already stored. */
public class ConflictException extends StemwrightException {

    private static final long serialVersionUID = 1L;

    public ConflictException(String message) {
        this(message, List.of());
    }

    public ConflictException(String message, List<ErrorDetail> details) {
        super(ErrorCode.CONFLICT, message, details, null);
    }
}
