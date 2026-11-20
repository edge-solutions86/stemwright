package io.github.rkaushikdev.stemwright.cli;

import io.github.rkaushikdev.stemwright.app.Application;
import io.github.rkaushikdev.stemwright.app.LanguageBundle;
import io.github.rkaushikdev.stemwright.app.Loaders;
import io.github.rkaushikdev.stemwright.domain.course.Actor;
import io.github.rkaushikdev.stemwright.domain.errors.NotFoundException;
import io.github.rkaushikdev.stemwright.domain.lexicon.Lexeme;
import io.github.rkaushikdev.stemwright.domain.lexicon.LexemeContext;
import io.github.rkaushikdev.stemwright.domain.lexicon.LexemeInput;
import io.github.rkaushikdev.stemwright.infra.DataFiles;
import io.github.rkaushikdev.stemwright.io.ImportReport;
import io.github.rkaushikdev.stemwright.io.LexiconImport;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Importing a word list from the command line.
 *
 * <p>It is a dry run unless told otherwise. An import is the one operation here that can put four
 * hundred wrong records into a lexicon in one go, and the shape of the mistake is nearly always in
 * the file rather than in the tool, so the default is to say what would happen and stop.
 */
public final class ImportCommand {

    private ImportCommand() {}

    /**
     * {@code import}: reads the csv file as utf-8 and reports the rows it would add, the rows it
     * would refuse and the lemmas already in the lexicon. With {@code --apply} it also writes every
     * accepted row, as the instructor {@code --actor} names.
     *
     * <p>The command fails with an {@link UncheckedIOException} when the file cannot be read.
     */
    public static Command of(Application app) {
        return Command.of(
                "import",
                "read a csv word list and report what it would add",
                "stemwright import --language latin --file words.csv --apply",
                FlagSpecs.of(
                        Map.entry("language", Flags.LANGUAGE),
                        Map.entry(
                                "file",
                                FlagSpec.of(FlagKind.STRING, "the csv file to read")
                                        .withAlias("f")
                                        .asRequired()),
                        Map.entry("apply", FlagSpec.of(FlagKind.BOOLEAN, "actually write the accepted rows")),
                        Map.entry("actor", Flags.ACTOR)),
                (args, context) -> run(app, args));
    }

    private static String run(Application app, ParsedArgs args) {
        String language = args.requireFlag("language");
        LanguageBundle bundle = Loaders.requireLanguage(app.stores(), language);
        String text = readText(args.requireFlag("file"));

        ImportReport report = LexiconImport.importCsv(new LexemeContext(bundle.schema(), bundle.classes()), text);
        List<String> collisions = report.collisionsWith(app.stores().lexemes().listByLanguage(language));

        List<String> lines = new ArrayList<>();
        lines.add(Render.section("Import", String.join("\n", report.describe())));
        if (!collisions.isEmpty()) {
            lines.add("");
            lines.add(Render.section("Already in the lexicon", String.join("\n", collisions)));
        }

        if (!args.booleanFlag("apply")) {
            lines.add("");
            lines.add("This was a dry run. Pass --apply to write the accepted rows.");
            return String.join("\n", lines);
        }

        Actor actor = Flags.actorFrom(args);
        for (Lexeme entry : report.accepted()) {
            write(app, actor, language, entry);
        }

        lines.add("");
        lines.add("Wrote " + report.accepted().size() + " lexemes to " + language + ".");
        return String.join("\n", lines);
    }

    /**
     * Stores the entry in place of the lexeme already under its id, or as a new lexeme when nothing
     * is stored there yet, which is what makes a second run of the same file change nothing.
     */
    private static void write(Application app, Actor actor, String language, Lexeme entry) {
        LexemeInput input = LexemeInput.builder(entry.lemma(), entry.classKey(), entry.stems())
                .inherent(entry.inherent().toInput())
                .glosses(entry.glosses())
                .tags(entry.tags())
                .build();
        try {
            app.lexicon().replace(actor, language, entry.id(), input);
        } catch (NotFoundException absent) {
            app.lexicon().add(actor, language, input);
        }
    }

    /**
     * The file's text, read as utf-8 with any malformed bytes read as U+FFFD, so a damaged row is
     * reported by the import at the line where it is.
     */
    private static String readText(String file) {
        return DataFiles.readIfPresent(Path.of(file))
                .orElseThrow(() -> new UncheckedIOException("no such file: " + file, new NoSuchFileException(file)));
    }
}
