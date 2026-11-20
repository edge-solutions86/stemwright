package io.github.rkaushikdev.stemwright.cli;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * What a command is handed besides its arguments.
 *
 * @param write takes a line of output a command wants seen before it finishes; a command's result
 *     is still the text it returns
 * @param now the instant the invocation started
 */
public record CommandContext(Consumer<String> write, Instant now) {

    public CommandContext {
        Objects.requireNonNull(write, "write");
        Objects.requireNonNull(now, "now");
    }
}
