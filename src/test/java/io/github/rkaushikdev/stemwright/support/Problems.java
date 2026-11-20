package io.github.rkaushikdev.stemwright.support;

import io.github.rkaushikdev.stemwright.domain.errors.ErrorDetail;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import java.util.List;

/**
 * A validation failure carries a short summary and the real information in its details, so a test
 * that only matches the summary would pass against any failure at all. These helpers make the
 * details the thing being asserted.
 */
public final class Problems {

    private Problems() {}

    /** Runs the action and returns the details of the validation failure it must raise. */
    public static List<ErrorDetail> detailsOf(Runnable run) {
        try {
            run.run();
        } catch (ValidationException error) {
            return error.details();
        }
        throw new AssertionError("expected a ValidationException and nothing was thrown");
    }

    public static List<String> problemPaths(Runnable run) {
        return detailsOf(run).stream().map(ErrorDetail::path).toList();
    }

    public static List<String> problemMessages(Runnable run) {
        return detailsOf(run).stream()
                .map(detail -> detail.path() + " " + detail.message())
                .toList();
    }

    /** Runs the action and returns the validation failure it must raise. */
    public static ValidationException validationFailureOf(Runnable run) {
        try {
            run.run();
        } catch (ValidationException error) {
            return error;
        }
        throw new AssertionError("expected a ValidationException and nothing was thrown");
    }
}
