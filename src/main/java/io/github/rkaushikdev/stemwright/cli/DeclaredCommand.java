package io.github.rkaushikdev.stemwright.cli;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/** The command {@link Command#of} builds: its declaration, and a function that answers it. */
record DeclaredCommand(
        String name,
        String describe,
        String usage,
        Map<String, FlagSpec> flags,
        BiFunction<ParsedArgs, CommandContext, String> body)
        implements Command {

    DeclaredCommand {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(describe, "describe");
        Objects.requireNonNull(usage, "usage");
        flags = Collections.unmodifiableMap(new LinkedHashMap<>(flags));
        Objects.requireNonNull(body, "body");
    }

    @Override
    public String run(ParsedArgs args, CommandContext context) {
        return body.apply(args, context);
    }
}
