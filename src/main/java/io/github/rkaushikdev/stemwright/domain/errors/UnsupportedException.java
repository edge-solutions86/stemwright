package io.github.rkaushikdev.stemwright.domain.errors;

/** A feature exists in the model but not in this build or this profile. */
public class UnsupportedException extends StemwrightException {

    private static final long serialVersionUID = 1L;

    public UnsupportedException(String message) {
        super(ErrorCode.UNSUPPORTED, message);
    }
}
