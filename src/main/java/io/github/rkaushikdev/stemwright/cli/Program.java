package io.github.rkaushikdev.stemwright.cli;

import io.github.rkaushikdev.stemwright.domain.errors.StemwrightException;
import io.github.rkaushikdev.stemwright.domain.text.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The command table and the dispatcher.
 *
 * <p>A command returns its output rather than printing it, and the exit code comes from whether it
 * threw. That is what makes every command testable without capturing a stream, and it is why
 * nothing below this line writes to a stream of its own.
 *
 * @param name the program's name, as help and the version line print it
 * @param version the program's version
 * @param commands the commands, by the name each declares
 */
public record Program(String name, String version, List<Command> commands) {

    /** The command ran and succeeded. */
    public static final int EXIT_OK = 0;

    /** The command ran and failed. */
    public static final int EXIT_FAILED = 1;

    /** The program was called wrongly: no command, an unknown one, or arguments that do not parse. */
    public static final int EXIT_MISUSED = 2;

    private static final Set<String> HELP_WORDS = Set.of("help", "--help", "-h");
    private static final Set<String> VERSION_WORDS = Set.of("--version", "version");

    public Program {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        commands = List.copyOf(commands);
    }

    /** The first command declaring the name, or nothing when none does. */
    public Optional<Command> commandNamed(String commandName) {
        return commands.stream()
                .filter(command -> command.name().equals(commandName))
                .findFirst();
    }

    /** The program's help: its version line, how it is called, and every command with its description. */
    public String helpText() {
        int width = commands.stream()
                .mapToInt(command -> command.name().length())
                .max()
                .orElse(0);
        List<String> lines = commands.stream()
                .map(command -> "  " + Text.padEnd(command.name(), width) + "  " + command.describe())
                .sorted()
                .toList();
        List<String> text = new ArrayList<>();
        text.add(name + " " + version);
        text.add("");
        text.add("Usage:");
        text.add("  " + name + " <command> [options]");
        text.add("");
        text.add("Commands:");
        text.addAll(lines);
        text.add("");
        text.add("Run " + name + " help <command> for the options of one command.");
        return String.join("\n", text);
    }

    /** One command's help: its description, its usage, and its options when it has any. */
    public String commandHelp(Command command) {
        List<String> flags = Args.formatFlags(command.flags());
        List<String> text = new ArrayList<>(
                List.of(name + " " + command.name(), "", command.describe(), "", "Usage:", "  " + command.usage()));
        if (!flags.isEmpty()) {
            text.add("");
            text.add("Options:");
            text.addAll(flags);
        }
        return String.join("\n", text);
    }

    /**
     * Runs one invocation. Every failure becomes an exit code and a line on the error stream, and the
     * two codes are kept apart: a command that was called wrongly exits 2, and a command that ran and
     * failed exits 1. A caller that cannot tell those apart cannot retry safely.
     *
     * <p>No arguments, {@code help}, {@code --help} and {@code -h} print the help, or with a command
     * name after them that command's help; only no arguments at all exits misused. {@code --version}
     * and {@code version} print the version line.
     *
     * @param argv the arguments after the program's name
     */
    public RunResult run(List<String> argv, CommandContext context) {
        Optional<String> first = argv.stream().findFirst();
        List<String> rest = argv.isEmpty() ? List.of() : argv.subList(1, argv.size());

        if (first.isEmpty() || HELP_WORDS.contains(first.get())) {
            if (!rest.isEmpty()) {
                String target = rest.get(0);
                return commandNamed(target)
                        .map(command -> new RunResult(EXIT_OK, commandHelp(command)))
                        .orElseGet(() -> new RunResult(EXIT_MISUSED, "there is no command named " + target));
            }
            return new RunResult(first.isEmpty() ? EXIT_MISUSED : EXIT_OK, helpText());
        }

        String commandName = first.get();
        if (VERSION_WORDS.contains(commandName)) {
            return new RunResult(EXIT_OK, name + " " + version);
        }

        Optional<Command> found = commandNamed(commandName);
        if (found.isEmpty()) {
            return new RunResult(EXIT_MISUSED, "there is no command named " + commandName);
        }
        Command command = found.get();

        ParsedArgs parsed;
        try {
            parsed = Args.parse(rest, command.flags());
        } catch (RuntimeException error) {
            return new RunResult(EXIT_MISUSED, messageOf(error));
        }

        try {
            return new RunResult(EXIT_OK, command.run(parsed, context));
        } catch (RuntimeException error) {
            return new RunResult(EXIT_FAILED, messageOf(error));
        }
    }

    /**
     * The text a failure prints as. A stemwright failure prints its message and then each detail on
     * a line of its own, indented by two spaces as {@code path message}. Anything else prints its
     * message, or itself as text when it carries none.
     */
    public static String messageOf(Throwable error) {
        if (error instanceof StemwrightException domain) {
            List<String> lines = new ArrayList<>();
            lines.add(domain.getMessage());
            domain.details().forEach(detail -> lines.add("  " + detail.path() + " " + detail.message()));
            return String.join("\n", lines);
        }
        return error.getMessage() == null ? error.toString() : error.getMessage();
    }
}
