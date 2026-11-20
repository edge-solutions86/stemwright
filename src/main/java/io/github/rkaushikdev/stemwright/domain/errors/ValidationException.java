package io.github.rkaushikdev.stemwright.domain.errors;

import java.util.List;

/** The caller sent something the domain cannot accept. */
public class ValidationException extends StemwrightException {

    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        this(message, List.of(), null);
    }

    public ValidationException(String message, List<ErrorDetail> details) {
        this(message, details, null);
    }

    public ValidationException(String message, List<ErrorDetail> details, Throwable cause) {
        super(ErrorCode.VALIDATION, message, details, cause);
    }

    /** A failure of one field, carrying that field as its single detail. */
    public static ValidationException field(String path, String message) {
        return new ValidationException(path + " " + message, List.of(new ErrorDetail(path, message)));
    }
}
