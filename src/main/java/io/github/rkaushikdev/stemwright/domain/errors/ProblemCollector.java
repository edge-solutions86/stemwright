package io.github.rkaushikdev.stemwright.domain.errors;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Collects field level problems and throws once.
 *
 * <p>Callers that validate several fields use this rather than throwing on the first bad one,
 * because a bulk import wants the whole list back in a single pass.
 */
public final class ProblemCollector {

    private final List<ErrorDetail> collected = new ArrayList<>();

    public void add(String path, String message) {
        collected.add(new ErrorDetail(path, message));
    }

    public void addIf(boolean condition, String path, String message) {
        if (condition) {
            add(path, message);
        }
    }

    public int size() {
        return collected.size();
    }

    /** A copy of what was collected, so a caller cannot grow the list. */
    public List<ErrorDetail> problems() {
        return new ArrayList<>(collected);
    }

    public void throwIfAny(String summary) {
        if (collected.isEmpty()) {
            return;
        }
        throw new ValidationException(summary, problems());
    }

    /**
     * Runs something that validates, and folds whatever it threw into this collector rather than
     * letting it escape.
     *
     * <p>A nested {@link ValidationException} contributes each of its own details under a prefixed
     * path. Anything else contributes one problem carrying its message, because a non validation
     * failure is a bug and hiding it would make the bug look like bad input.
     *
     * @return the value, or empty when the run threw
     */
    public <T> Optional<T> absorb(String path, Supplier<T> run) {
        try {
            return Optional.ofNullable(run.get());
        } catch (ValidationException error) {
            if (error.details().isEmpty()) {
                add(path, messageOf(error));
            } else {
                for (ErrorDetail detail : error.details()) {
                    add(path + "." + detail.path(), detail.message());
                }
            }
            return Optional.empty();
        } catch (RuntimeException error) {
            add(path, messageOf(error));
            return Optional.empty();
        }
    }

    /** Absorbs under the caller's own path rather than nesting a child path. */
    public <T> Optional<T> absorbFlat(String path, Supplier<T> run) {
        try {
            return Optional.ofNullable(run.get());
        } catch (ValidationException error) {
            if (error.details().isEmpty()) {
                add(path, messageOf(error));
            } else {
                for (ErrorDetail detail : error.details()) {
                    add(path, detail.path() + " " + detail.message());
                }
            }
            return Optional.empty();
        } catch (RuntimeException error) {
            add(path, messageOf(error));
            return Optional.empty();
        }
    }

    private static String messageOf(RuntimeException error) {
        return String.valueOf(error.getMessage());
    }
}
