package io.github.rkaushikdev.stemwright.domain.errors;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Every failure that crosses a module boundary in stemwright is one of these.
 *
 * <p>The {@link ErrorCode} is the contract; the concrete subclass is a convenience for the code
 * that raises it. A validation failure carries its content in {@link #details()}: the message is a
 * summary for a human, and a caller that reads only the message is asserting nothing.
 */
public class StemwrightException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode code;
    private final transient List<ErrorDetail> details;

    public StemwrightException(ErrorCode code, String message) {
        this(code, message, List.of(), null);
    }

    public StemwrightException(ErrorCode code, String message, List<ErrorDetail> details, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
        this.details = List.copyOf(details);
    }

    public ErrorCode code() {
        return code;
    }

    /** The field level problems, empty rather than absent when there are none. */
    public List<ErrorDetail> details() {
        return details;
    }

    /** A shape safe to put on the wire or in the journal. */
    public WireError toWire() {
        return new WireError(code, getMessage(), details);
    }

    public static boolean isStemwrightError(Object value) {
        return value instanceof StemwrightException;
    }

    public static Optional<ErrorCode> errorCodeOf(Object value) {
        return value instanceof StemwrightException error ? Optional.of(error.code()) : Optional.empty();
    }

    /**
     * The wire shape of a failure: its code, its message and its details, and nothing that could
     * leak a stack trace.
     */
    public record WireError(ErrorCode code, String message, List<ErrorDetail> details) {

        public WireError {
            details = List.copyOf(details);
        }
    }
}
