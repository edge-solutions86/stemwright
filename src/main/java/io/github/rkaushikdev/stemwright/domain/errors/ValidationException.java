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

    /**
     * The same refusal, with every detail at {@code path} carrying {@code message} instead. A reader
     * that had to narrow a value before a domain check saw it uses this to put back the message the
     * value itself would have earned; the refusal it restates is kept as the cause.
     */
    public ValidationException restating(String path, String message) {
        List<ErrorDetail> restated = details().stream()
                .map(detail -> detail.path().equals(path) ? new ErrorDetail(path, message) : detail)
                .toList();
        return new ValidationException(getMessage(), restated, this);
    }

    /** A failure of one field, carrying that field as its single detail. */
    public static ValidationException field(String path, String message) {
        return new ValidationException(path + " " + message, List.of(new ErrorDetail(path, message)));
    }
}
