package io.github.rkaushikdev.stemwright.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The help texts line for line, a failure with no message, and what a command built by {@link
 * Command#of} keeps and is handed.
 */
class ProgramTextTest {

    private static final Command ECHO = Command.of(
            "echo",
            "print what it was given",
            "stemwright echo --text <text>",
            Map.of("text", FlagSpec.of(FlagKind.STRING, "what to print").asRequired()),
            (args, context) -> args.requireFlag("text"));

    private static final Command EXPLODE =
            Command.of("explode", "always fails", "stemwright explode", Map.of(), (args, context) -> "");

    private static final Program PROGRAM = new Program("stemwright", "1.2.3", List.of(EXPLODE, ECHO));

    @Test
    void laysTheProgramHelpOutWithTheCommandsPaddedAndInOrder() {
        assertThat(PROGRAM.helpText())
                .isEqualTo(String.join(
                        "\n",
                        "stemwright 1.2.3",
                        "",
                        "Usage:",
                        "  stemwright <command> [options]",
                        "",
                        "Commands:",
                        "  echo     print what it was given",
                        "  explode  always fails",
                        "",
                        "Run stemwright help <command> for the options of one command."));
    }

    @Test
    void laysACommandsHelpOutWithItsOptionsOnlyWhenItHasAny() {
        assertThat(PROGRAM.commandHelp(ECHO))
                .isEqualTo(String.join(
                        "\n",
                        "stemwright echo",
                        "",
                        "print what it was given",
                        "",
                        "Usage:",
                        "  stemwright echo --text <text>",
                        "",
                        "Options:",
                        "  --text  what to print (required)"));
        assertThat(PROGRAM.commandHelp(EXPLODE))
                .isEqualTo(String.join(
                        "\n", "stemwright explode", "", "always fails", "", "Usage:", "  stemwright explode"));
    }

    @Test
    void printsAFailureThatCarriesNoMessageAsItself() {
        assertThat(Program.messageOf(new IllegalStateException())).isEqualTo("java.lang.IllegalStateException");
    }

    @Test
    void keepsTheFlagsOfADeclaredCommandInOrderAndOutOfReach() {
        Map<String, FlagSpec> flags = new LinkedHashMap<>();
        flags.put("since", FlagSpec.of(FlagKind.STRING, "the first day"));
        flags.put("deck", FlagSpec.of(FlagKind.STRING, "the deck"));
        Command command = Command.of("stats", "count things", "stemwright stats", flags, (args, context) -> "");
        flags.remove("since");

        assertThat(command.flags().keySet()).containsExactly("since", "deck");
        assertThatThrownBy(() -> command.flags().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void handsTheCommandItsArgumentsAndTheContext() {
        List<String> written = new ArrayList<>();
        Instant now = Instant.parse("2025-03-03T09:00:00Z");
        Command clock = Command.of(
                "clock",
                "say when",
                "stemwright clock",
                Map.of("label", FlagSpec.of(FlagKind.STRING, "a label")),
                (args, context) -> {
                    context.write().accept("working");
                    return args.stringFlag("label").orElse("") + " " + context.now();
                });
        Program program = new Program("stemwright", "1.2.3", List.of(clock));

        RunResult result = program.run(List.of("clock", "--label", "at"), new CommandContext(written::add, now));

        assertThat(result).isEqualTo(new RunResult(Program.EXIT_OK, "at 2025-03-03T09:00:00Z"));
        assertThat(written).containsExactly("working");
    }
}
