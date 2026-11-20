package io.github.rkaushikdev.stemwright.bin;

import static io.github.rkaushikdev.stemwright.cli.Program.EXIT_FAILED;
import static io.github.rkaushikdev.stemwright.cli.Program.EXIT_MISUSED;
import static io.github.rkaushikdev.stemwright.cli.Program.EXIT_OK;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.cli.Command;
import io.github.rkaushikdev.stemwright.cli.Program;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class StemwrightTest {

    /**
     * What one invocation through picocli left behind.
     *
     * @param code the exit code {@link CommandLine#execute} returned
     * @param out everything written to the output writer
     * @param err everything written to the error writer
     */
    private record Captured(int code, String out, String err) {}

    private static Captured main(String... argv) {
        return main(new Stemwright(), argv);
    }

    private static Captured main(Stemwright command, String... argv) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        int code = new CommandLine(command)
                .setOut(new PrintWriter(out))
                .setErr(new PrintWriter(err))
                .execute(argv);
        return new Captured(code, out.toString(), err.toString());
    }

    /** A command over a program holding only the one command. */
    private static Stemwright over(Command only) {
        return new Stemwright(() -> new Program("stemwright", Stemwright.VERSION, List.of(only)));
    }

    @Nested
    class BuildProgram {

        @Test
        void namesItselfAndCarriesTheVersion() {
            Program program = Stemwright.buildProgram();
            assertThat(program.name()).isEqualTo("stemwright");
            assertThat(program.version()).isEqualTo(Stemwright.VERSION);
        }

        @Test
        void carriesEveryCommandTheTableDeclares() {
            List<String> names = Stemwright.buildProgram().commands().stream()
                    .map(Command::name)
                    .toList();
            assertThat(names.size()).isGreaterThanOrEqualTo(5);
            assertThat(names).contains("queue");
            assertThat(names).contains("routes");
        }
    }

    @Nested
    class Main {

        @Test
        void printsHelpAndExitsMisusedWithNoArguments() {
            Captured result = main();
            assertThat(result.code()).isEqualTo(EXIT_MISUSED);
            assertThat(result.err()).contains("Usage:");
            assertThat(result.out()).isEmpty();
        }

        @Test
        void printsHelpToStdoutWhenItWasAskedFor() {
            Captured result = main("help");
            assertThat(result.code()).isEqualTo(EXIT_OK);
            assertThat(result.out()).contains("Commands:");
        }

        @Test
        void printsTheVersion() {
            assertThat(main("--version").out()).isEqualTo("stemwright " + Stemwright.VERSION + "\n");
        }

        @Test
        void sendsAFailureToStderrAndNothingToStdout() {
            Captured result = main("lexemes", "--language", "latin");
            assertThat(result.code()).isNotEqualTo(EXIT_OK);
            assertThat(result.out()).isEmpty();
            assertThat(result.err()).contains("latin");
        }

        @Test
        void sendsAMisuseToStderr() {
            Captured result = main("nowhere");
            assertThat(result.code()).isEqualTo(EXIT_MISUSED);
            assertThat(result.err()).contains("there is no command named nowhere");
        }
    }

    /** The program's own grammar sees every token as it was typed; picocli reads none of them. */
    @Nested
    class ArgumentsAsGiven {

        @Test
        void leavesTheHelpFlagsToTheProgram() {
            String help = Stemwright.buildProgram().helpText() + "\n";
            for (String asked : List.of("--help", "-h", "help")) {
                assertThat(main(asked)).isEqualTo(new Captured(EXIT_OK, help, ""));
            }
        }

        @Test
        void leavesTheVersionFlagToTheProgram() {
            assertThat(main("--version")).isEqualTo(new Captured(EXIT_OK, "stemwright 1.0.0\n", ""));
        }

        @Test
        void passesADoubleDashThroughWhereverItStands() {
            assertThat(main("--")).isEqualTo(new Captured(EXIT_MISUSED, "", "there is no command named --\n"));
            assertThat(main("--", "routes"))
                    .isEqualTo(new Captured(EXIT_MISUSED, "", "there is no command named --\n"));
        }

        @Test
        void handsTheCommandTheDoubleDashThatEndsItsFlags() {
            // With the double dash dropped, --search would be a flag missing its value and exit 2.
            Captured result = main("lexemes", "--language", "latin", "--", "--search");
            assertThat(result).isEqualTo(new Captured(EXIT_FAILED, "", "language latin was not found\n"));
        }

        @Test
        void passesALoneDashAndUnknownFlagsThrough() {
            assertThat(main("-")).isEqualTo(new Captured(EXIT_MISUSED, "", "there is no command named -\n"));
            assertThat(main("--nonsense"))
                    .isEqualTo(new Captured(EXIT_MISUSED, "", "there is no command named --nonsense\n"));
            assertThat(main("-x")).isEqualTo(new Captured(EXIT_MISUSED, "", "there is no command named -x\n"));
        }

        @Test
        void readsNoArgumentFile(@TempDir Path root) throws IOException {
            Path file = root.resolve("arguments.txt");
            Files.writeString(file, "help\n", StandardCharsets.UTF_8);

            for (Path named : List.of(file, root)) {
                String token = "@" + named;
                assertThat(main(token))
                        .isEqualTo(new Captured(EXIT_MISUSED, "", "there is no command named " + token + "\n"));
            }
        }

        @Test
        void printsNothingForAnEmptyOutput() {
            Command quiet = Command.of("quiet", "say nothing", "stemwright quiet", Map.of(), (args, context) -> "");
            assertThat(main(over(quiet), "quiet")).isEqualTo(new Captured(EXIT_OK, "", ""));
        }

        @Test
        void writesWhatACommandWritesAheadOfItsResult() {
            Command early =
                    Command.of("early", "write before answering", "stemwright early", Map.of(), (args, context) -> {
                        context.write().accept("started");
                        return "one\ntwo";
                    });
            assertThat(main(over(early), "early")).isEqualTo(new Captured(EXIT_OK, "started\none\ntwo\n", ""));
        }
    }
}
