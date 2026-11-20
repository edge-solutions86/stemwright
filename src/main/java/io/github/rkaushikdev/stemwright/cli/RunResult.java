package io.github.rkaushikdev.stemwright.cli;

import java.util.Objects;

/**
 * How one invocation ended.
 *
 * @param code the exit code: {@link Program#EXIT_OK}, {@link Program#EXIT_FAILED} or {@link
 *     Program#EXIT_MISUSED}
 * @param output what to print, to the output stream on success and to the error stream otherwise
 */
public record RunResult(int code, String output) {

    public RunResult {
        Objects.requireNonNull(output, "output");
    }
}
