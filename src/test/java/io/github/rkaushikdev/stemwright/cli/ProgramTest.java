package io.github.rkaushikdev.stemwright.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.domain.errors.ErrorDetail;
import io.github.rkaushikdev.stemwright.domain.errors.NotFoundException;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProgramTest {

    private static final CommandContext CONTEXT = new CommandContext(line -> {}, Instant.parse("2025-03-03T09:00:00Z"));

    private static final Command ECHO = Command.of(
            "echo",
            "print what it was given",
            "stemwright echo --text <text>",
            Map.of("text", FlagSpec.of(FlagKind.STRING, "what to print").asRequired()),
            (args, context) -> args.requireFlag("text"));

    private static final Command EXPLODE =
            Command.of("explode", "always fails", "stemwright explode", Map.of(), (args, context) -> {
                throw new NotFoundException("deck", "all");
            });

    private static final Program PROGRAM = new Program("stemwright", "1.2.3", List.of(ECHO, EXPLODE));

    private static RunResult run(String... argv) {
        return PROGRAM.run(List.of(argv), CONTEXT);
    }

    @Nested
    class CommandNamed {

        @Test
        void findsACommandAndReportsAMiss() {
            assertThat(PROGRAM.commandNamed("echo")).containsSame(ECHO);
            assertThat(PROGRAM.commandNamed("nowhere")).isEmpty();
        }
    }

    @Nested
    class Help {

        @Test
        void listsEveryCommand() {
            String text = PROGRAM.helpText();
            assertThat(text).contains("stemwright 1.2.3");
            assertThat(text).contains("echo");
            assertThat(text).contains("explode");
        }

        @Test
        void printsTheOptionsOfOneCommand() {
            String text = PROGRAM.commandHelp(ECHO);
            assertThat(text).contains("stemwright echo --text <text>");
            assertThat(text).contains("--text");
            assertThat(text).contains("(required)");
        }

        @Test
        void leavesTheOptionsBlockOutWhenACommandHasNone() {
            assertThat(PROGRAM.commandHelp(EXPLODE)).doesNotContain("Options:");
        }
    }

    @Nested
    class Run {

        @Test
        void runsACommandAndReturnsItsOutput() {
            assertThat(run("echo", "--text", "hello")).isEqualTo(new RunResult(Program.EXIT_OK, "hello"));
        }

        @Test
        void printsHelpForNoArgumentsAndExitsMisused() {
            RunResult result = run();
            assertThat(result.code()).isEqualTo(Program.EXIT_MISUSED);
            assertThat(result.output()).contains("Usage:");
        }

        @Test
        void printsHelpOnRequestAndExitsCleanly() {
            assertThat(run("help").code()).isEqualTo(Program.EXIT_OK);
            assertThat(run("--help").code()).isEqualTo(Program.EXIT_OK);
        }

        @Test
        void printsHelpForOneCommand() {
            RunResult result = run("help", "echo");
            assertThat(result.code()).isEqualTo(Program.EXIT_OK);
            assertThat(result.output()).contains("--text");
        }

        @Test
        void refusesHelpForACommandNobodyDeclared() {
            RunResult result = run("help", "nowhere");
            assertThat(result.code()).isEqualTo(Program.EXIT_MISUSED);
            assertThat(result.output()).contains("there is no command named nowhere");
        }

        @Test
        void printsTheVersion() {
            assertThat(run("--version").output()).isEqualTo("stemwright 1.2.3");
            assertThat(run("version").output()).isEqualTo("stemwright 1.2.3");
        }

        @Test
        void exitsMisusedForAnUnknownCommand() {
            assertThat(run("nowhere").code()).isEqualTo(Program.EXIT_MISUSED);
        }

        @Test
        void exitsMisusedWhenTheArgumentsDoNotParse() {
            RunResult result = run("echo");
            assertThat(result.code()).isEqualTo(Program.EXIT_MISUSED);
            assertThat(result.output()).contains("text is required");
        }

        @Test
        void exitsFailedWhenTheCommandItselfThrows() {
            RunResult result = run("explode");
            assertThat(result.code()).isEqualTo(Program.EXIT_FAILED);
            assertThat(result.output()).contains("deck all was not found");
        }

        @Test
        void keepsTheTwoFailureCodesApart() {
            RunResult misused = run("echo");
            RunResult failed = run("explode");
            assertThat(misused.code()).isNotEqualTo(failed.code());
        }
    }

    @Nested
    class MessageOf {

        @Test
        void listsTheDetailsOfAValidationFailure() {
            ValidationException error = new ValidationException(
                    "two problems", List.of(new ErrorDetail("a", "is wrong"), new ErrorDetail("b", "is wrong")));
            assertThat(Program.messageOf(error)).isEqualTo("two problems\n  a is wrong\n  b is wrong");
        }

        @Test
        void takesTheMessageOfAnOrdinaryError() {
            assertThat(Program.messageOf(new IllegalStateException("boom"))).isEqualTo("boom");
        }

        @Test
        void stringifiesAnythingElse() {
            assertThat(Program.messageOf(new Throwable("a string"))).isEqualTo("a string");
        }
    }
}
