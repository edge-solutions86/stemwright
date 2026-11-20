package io.github.rkaushikdev.stemwright.domain.errors;

/** The actor may not see or touch this. */
public class ForbiddenException extends StemwrightException {

    private static final long serialVersionUID = 1L;

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}
