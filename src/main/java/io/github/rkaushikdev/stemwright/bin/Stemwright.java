package io.github.rkaushikdev.stemwright.bin;

import io.github.rkaushikdev.stemwright.app.Application;
import io.github.rkaushikdev.stemwright.cli.Args;
import io.github.rkaushikdev.stemwright.cli.CommandContext;
import io.github.rkaushikdev.stemwright.cli.Commands;
import io.github.rkaushikdev.stemwright.cli.Program;
import io.github.rkaushikdev.stemwright.cli.RunResult;
import io.github.rkaushikdev.stemwright.infra.MemoryStores;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IModelTransformer;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * The cli entry point. It builds the program, runs one invocation and prints the result, and that
 * is the whole class.
 *
 * <p>Output goes to the output stream on success and to the error stream on failure, so a pipeline
 * can take the table without taking the error message with it. The exit code is whatever the
 * dispatcher decided, and nothing here throws.
 *
 * <p>picocli is the process boundary and nothing more. The program has an argument grammar of its
 * own, {@link Args}, whose messages, help text and exit codes are part of what it promises, so
 * picocli is told to accept every token and to expand no {@code @file}, and this command hands
 * {@link Program#run} the arguments exactly as they were given: a {@code --}, a lone {@code -},
 * {@code --help} and {@code --version} included.
 */
@Command(name = "stemwright", modelTransformer = Stemwright.ArgumentsAsGiven.class)
public final class Stemwright implements Callable<Integer> {

    /** The version the program reports. */
    public static final String VERSION = "1.0.0";

    private final Supplier<Program> program;

    @Spec
    private CommandSpec spec;

    /** The command over {@link #buildProgram()}, built when it runs. */
    public Stemwright() {
        this(Stemwright::buildProgram);
    }

    /** The command over whatever program the supplier builds when it runs. */
    Stemwright(Supplier<Program> program) {
        this.program = Objects.requireNonNull(program, "program");
    }

    /** The program with every command, over an application on fresh memory stores. */
    public static Program buildProgram() {
        return new Program("stemwright", VERSION, Commands.all(Application.create(MemoryStores.create())));
    }

    /**
     * Runs the program on the arguments as they were given, and prints its output as one line on
     * picocli's output writer when it exited {@link Program#EXIT_OK} and on its error writer
     * otherwise, printing nothing for an empty output. A line a command writes while it runs goes to
     * the output writer.
     *
     * @return the program's exit code
     */
    @Override
    public Integer call() {
        CommandLine commandLine = spec.commandLine();
        PrintWriter out = commandLine.getOut();
        List<String> argv = List.copyOf(commandLine.getParseResult().originalArgs());

        RunResult result = program.get().run(argv, new CommandContext(line -> printLine(out, line), Instant.now()));
        if (!result.output().isEmpty()) {
            printLine(result.code() == Program.EXIT_OK ? out : commandLine.getErr(), result.output());
        }
        return result.code();
    }

    /** Runs one invocation and exits with its code. */
    public static void main(String[] args) {
        System.exit(new CommandLine(new Stemwright()).execute(args));
    }

    /** The text and a line feed, flushed, whatever the platform's line separator is. */
    private static void printLine(PrintWriter writer, String text) {
        writer.print(text);
        writer.print('\n');
        writer.flush();
    }

    /**
     * Configures picocli to take any token as unmatched rather than refusing it, and to read a token
     * beginning with {@code @} as itself rather than as a file of further arguments.
     */
    static final class ArgumentsAsGiven implements IModelTransformer {

        @Override
        public CommandSpec transform(CommandSpec commandSpec) {
            commandSpec.parser().expandAtFiles(false).unmatchedArgumentsAllowed(true);
            return commandSpec;
        }
    }
}
