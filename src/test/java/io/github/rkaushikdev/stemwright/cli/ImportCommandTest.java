package io.github.rkaushikdev.stemwright.cli;

import static io.github.rkaushikdev.stemwright.cli.Program.EXIT_FAILED;
import static io.github.rkaushikdev.stemwright.cli.Program.EXIT_MISUSED;
import static io.github.rkaushikdev.stemwright.cli.Program.EXIT_OK;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.app.Application;
import io.github.rkaushikdev.stemwright.app.ApplicationOptions;
import io.github.rkaushikdev.stemwright.app.Stores;
import io.github.rkaushikdev.stemwright.support.App;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImportCommandTest {

    private static final CommandContext CONTEXT = new CommandContext(line -> {}, App.NOW);

    private static final String HEADER = "lemma,class,stem,gloss,tags";
    private static final String GOOD =
            HEADER + "\nnauta,first-declension,naut,sailor,core\nvia,first-declension,vi,road,core\n";
    private static final String MIXED =
            HEADER + "\nnauta,first-declension,naut,sailor,core\nrex,fourth-declension,reg,king,core\n";

    @TempDir
    Path root;

    private String freshFile(String name, String contents) throws IOException {
        Path path = root.resolve(name);
        Files.writeString(path, contents, StandardCharsets.UTF_8);
        return path.toString();
    }

    /** The stores the command writes to, and a program holding only {@code import}. */
    private record Built(Stores stores, Program program) {}

    private static Built build() {
        Stores stores = App.seededStores();
        App.Harness harness = App.testHarness();
        Application app = Application.create(stores, ApplicationOptions.of(harness.bus(), harness.clock()));
        return new Built(stores, new Program("stemwright", "1.0.0", List.of(ImportCommand.of(app))));
    }

    private static RunResult run(Program program, String... argv) {
        return program.run(List.of(argv), CONTEXT);
    }

    @Nested
    class ADryRun {

        @Test
        void reportsWhatItWouldAddAndWritesNothing() throws IOException {
            Built built = build();
            String file = freshFile("words.csv", GOOD);
            RunResult result = run(built.program(), "import", "--language", "latin", "--file", file);

            assertThat(result.code()).isEqualTo(EXIT_OK);
            assertThat(result.output()).contains("2 of 2 rows are usable");
            assertThat(result.output()).contains("This was a dry run");
            assertThat(built.stores().lexemes().listByLanguage("latin")).hasSize(4);
        }

        @Test
        void namesTheRowsItWouldReject() throws IOException {
            String file = freshFile("words.csv", MIXED);
            RunResult result = run(build().program(), "import", "--language", "latin", "--file", file);
            assertThat(result.output()).contains("1 of 2 rows are usable");
            assertThat(result.output()).contains("line 3 rex:");
        }

        @Test
        void namesARowThatIsAlreadyInTheLexicon() throws IOException {
            String file = freshFile("words.csv", HEADER + "\npuella,first-declension,puell,girl,core\n");
            RunResult result = run(build().program(), "import", "--language", "latin", "--file", file);
            assertThat(result.output()).contains("Already in the lexicon");
            assertThat(result.output()).contains("puella");
        }
    }

    @Nested
    class Applying {

        @Test
        void writesTheAcceptedRows() throws IOException {
            Built built = build();
            String file = freshFile("words.csv", GOOD);
            RunResult result = run(built.program(), "import", "--language", "latin", "--file", file, "--apply");

            assertThat(result.code()).isEqualTo(EXIT_OK);
            assertThat(result.output()).contains("Wrote 2 lexemes to latin");
            assertThat(built.stores().lexemes().listByLanguage("latin")).hasSize(6);
        }

        @Test
        void writesOnlyTheRowsThatParsed() throws IOException {
            Built built = build();
            String file = freshFile("words.csv", MIXED);
            run(built.program(), "import", "--language", "latin", "--file", file, "--apply");
            assertThat(built.stores().lexemes().listByLanguage("latin")).hasSize(5);
        }

        @Test
        void isIdempotentWhenRunTwice() throws IOException {
            Built built = build();
            String file = freshFile("words.csv", GOOD);
            run(built.program(), "import", "--language", "latin", "--file", file, "--apply");
            run(built.program(), "import", "--language", "latin", "--file", file, "--apply");
            assertThat(built.stores().lexemes().listByLanguage("latin")).hasSize(6);
        }
    }

    @Nested
    class Failures {

        @Test
        void refusesACallWithNoFile() {
            RunResult result = run(build().program(), "import", "--language", "latin");
            assertThat(result.code()).isEqualTo(EXIT_MISUSED);
            assertThat(result.output()).contains("file is required");
        }

        @Test
        void failsForALanguageNobodyConfigured() throws IOException {
            String file = freshFile("words.csv", GOOD);
            RunResult result = run(build().program(), "import", "--language", "greek", "--file", file);
            assertThat(result.code()).isEqualTo(EXIT_FAILED);
        }

        @Test
        void failsForAFileThatIsNotThere() {
            RunResult result = run(build().program(), "import", "--language", "latin", "--file", "nowhere.csv");
            assertThat(result.code()).isEqualTo(EXIT_FAILED);
        }

        @Test
        void failsForAFileMissingARequiredColumn() throws IOException {
            String file = freshFile("words.csv", "lemma,gloss\npuella,girl\n");
            RunResult result = run(build().program(), "import", "--language", "latin", "--file", file);
            assertThat(result.code()).isEqualTo(EXIT_FAILED);
            assertThat(result.output()).contains("missing the columns");
        }
    }
}
