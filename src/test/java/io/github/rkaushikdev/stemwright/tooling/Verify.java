package io.github.rkaushikdev.stemwright.tooling;

import io.github.rkaushikdev.stemwright.domain.text.Text;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Everything that has to pass before a change is worth pushing, behind one command, in the order
 * that fails fastest.
 *
 * <p>Each stage is named, and a single stage can be run on its own while a change is being worked
 * on. The order is not decorative: formatting and the compiler's lint are seconds and catch the
 * most, compiling the tests against the library is next, packaging the jars follows, and the suite
 * is last because it is the slowest and the least likely to be the thing that is wrong.
 *
 * <p>Every stage runs the Maven wrapper from the repository root. The compiler lints only what it
 * compiles, so the {@code lint} stage proves the most on a tree that has not been built yet.
 *
 * <p>Run it with {@code java -cp target/classes:target/test-classes} and this class after {@code
 * ./mvnw -q test-compile}, followed by the names of the stages to run, or by nothing to run all
 * five.
 */
public final class Verify {

    /** The wrapper every stage runs, by the name this platform starts it under. */
    public static final String WRAPPER =
            Text.lower(System.getProperty("os.name", "")).startsWith("windows") ? "mvnw.cmd" : "./mvnw";

    /** The stages, in the order a full run takes them. */
    public static final List<Stage> STAGES = List.of(
            new Stage("format", WRAPPER, List.of("-B", "spotless:check")),
            new Stage("lint", WRAPPER, List.of("-B", "compile", "-Dmaven.compiler.failOnWarning=true")),
            new Stage("types", WRAPPER, List.of("-B", "test-compile")),
            new Stage("build", WRAPPER, List.of("-B", "package", "-DskipTests")),
            new Stage("test", WRAPPER, List.of("-B", "test")));

    private Verify() {}

    /**
     * One step of the run.
     *
     * @param name what it is called on the command line and in the summary
     * @param command the program it runs, relative to the repository root
     * @param args the arguments it passes
     */
    public record Stage(String name, String command, List<String> args) {

        public Stage {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(command, "command");
            args = List.copyOf(args);
        }
    }

    /**
     * How one stage went.
     *
     * @param name the stage
     * @param ok whether its command exited zero
     * @param ms how long it took, in milliseconds
     */
    public record Result(String name, boolean ok, long ms) {

        public Result {
            Objects.requireNonNull(name, "name");
        }
    }

    /** The stage of that name, if one is declared. */
    public static Optional<Stage> stageNamed(String name) {
        return STAGES.stream().filter(stage -> stage.name().equals(name)).findFirst();
    }

    /**
     * The stages to run: every one when nothing is named, otherwise the ones named in the order
     * given.
     *
     * @throws IllegalArgumentException naming the first stage nobody declared, and listing the real
     *     ones
     */
    public static List<Stage> planFor(List<String> wanted) {
        if (wanted.isEmpty()) {
            return STAGES;
        }
        List<Stage> plan = new ArrayList<>();
        for (String name : wanted) {
            plan.add(stageNamed(name)
                    .orElseThrow(() -> new IllegalArgumentException("there is no stage named " + name + ", try one of "
                            + STAGES.stream().map(Stage::name).collect(Collectors.joining(", ")))));
        }
        return List.copyOf(plan);
    }

    /**
     * Runs one stage from the repository root with this process's input and output, and waits for
     * it. A command that cannot be started counts as a failure, and says why on the error stream.
     */
    public static Result runStage(Stage stage) {
        Path root = Workspace.root();
        List<String> command = new ArrayList<>();
        command.add(root.resolve(stage.command()).normalize().toString());
        command.addAll(stage.args());

        long started = System.nanoTime();
        boolean ok;
        try {
            Process process = new ProcessBuilder(command)
                    .directory(root.toFile())
                    .inheritIO()
                    .start();
            ok = process.waitFor() == 0;
        } catch (IOException e) {
            System.err.print("could not start " + stage.command() + ": " + e.getMessage() + "\n");
            ok = false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ok = false;
        }
        return new Result(stage.name(), ok, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
    }

    /**
     * Runs the planned stages until one fails, then prints a line per stage that ran and how many
     * were never reached.
     *
     * @return zero when every stage that ran passed, one otherwise
     */
    public static int run(List<String> wanted, PrintStream out) {
        List<Stage> plan = planFor(wanted);
        List<Result> results = new ArrayList<>();

        for (Stage stage : plan) {
            out.print("\n--- " + stage.name() + " ---\n");
            out.flush();
            Result result = runStage(stage);
            results.add(result);
            if (!result.ok()) {
                break;
            }
        }

        out.print("\n");
        for (Result result : results) {
            out.print((result.ok() ? "ok  " : "FAIL") + " " + result.name() + " " + result.ms() + "ms\n");
        }

        long failed = results.stream().filter(result -> !result.ok()).count();
        int skipped = plan.size() - results.size();
        if (skipped > 0) {
            out.print(skipped + " stages were not reached\n");
        }
        out.flush();
        return failed == 0 ? 0 : 1;
    }

    /** Runs the stages named on the command line, or all of them, and exits with the outcome. */
    public static void main(String[] args) {
        System.exit(run(List.of(args), System.out));
    }
}
