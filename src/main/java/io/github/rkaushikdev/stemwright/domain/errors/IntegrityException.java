package io.github.rkaushikdev.stemwright.domain.errors;

import java.util.List;

/** Stored state contradicts itself. Always a bug, never a user mistake. */
public class IntegrityException extends StemwrightException {

    private static final long serialVersionUID = 1L;

    public IntegrityException(String message) {
        this(message, List.of(), null);
    }

    public IntegrityException(String message, Throwable cause) {
        this(message, List.of(), cause);
    }

    public IntegrityException(String message, List<ErrorDetail> details, Throwable cause) {
        super(ErrorCode.INTEGRITY, message, details, cause);
    }
}
