package io.github.rkaushikdev.stemwright.cli;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * One command of the cli. A command returns its output rather than printing it, and it fails by
 * throwing, which {@link Program#run} turns into an exit code.
 */
public interface Command {

    /** The name typed after the program's, such as {@code queue}. */
    String name();

    /** The line the help text prints for the command. */
    String describe();

    /** The flags the command accepts, by name, in the order they are checked. */
    Map<String, FlagSpec> flags();

    /** An example invocation, such as {@code stemwright queue --language latin}. */
    String usage();

    /**
     * Runs the command on arguments already parsed against {@link #flags()}.
     *
     * @return the output, printed as the invocation's result
     */
    String run(ParsedArgs args, CommandContext context);

    /**
     * A command that answers through the function, the way most commands are written.
     *
     * @param flags the flags, kept in the order the map iterates in
     * @param body what {@link #run} answers, given the parsed arguments and the context
     */
    static Command of(
            String name,
            String describe,
            String usage,
            Map<String, FlagSpec> flags,
            BiFunction<ParsedArgs, CommandContext, String> body) {
        return new DeclaredCommand(name, describe, usage, flags, body);
    }
}
