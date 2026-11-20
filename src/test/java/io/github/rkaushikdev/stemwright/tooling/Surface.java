package io.github.rkaushikdev.stemwright.tooling;

import io.github.rkaushikdev.stemwright.domain.text.Text;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes the public surface of the library to {@code docs/surface.md}, so that a change to what it
 * exports shows up in a diff rather than in somebody else's build.
 *
 * <p>Nothing here runs when the class loads. A tool that regenerates its own output as a side
 * effect of being loaded rewrites the file the test is about to compare against, and the check then
 * passes no matter what.
 *
 * <p>The surface is read from declaration lines, one line at a time. A declaration counts when it is
 * public and so is every type around it: public by its own modifier, or public without saying so as
 * a member of an interface or an annotation. Comment lines, imports and the package line are never
 * read as declarations. The formatter indents each level by four spaces, and that indentation is
 * what tells a member of a type from a statement inside one of its methods.
 *
 * <p>Run it with {@code java -cp target/classes:target/test-classes} and this class after {@code
 * ./mvnw -q test-compile}; {@code check} compares instead of writing.
 */
public final class Surface {

    /** The command that regenerates the file, as the file and the check name it. */
    public static final String COMMAND = "java -cp target/classes:target/test-classes " + Surface.class.getName();

    private static final String OUTPUT = "docs/surface.md";
    private static final String SOURCES = "src/main/java";
    private static final String ENTRY_POINTS = "src/main/java/io/github/rkaushikdev/stemwright/bin/";
    private static final int LEVEL = 4;

    private static final Pattern MODIFIERS = Pattern.compile("(?:(?:public|protected|private|static|final|abstract"
            + "|sealed|non-sealed|strictfp|default|synchronized|native|transient|volatile) +)*");
    private static final Pattern TYPE =
            Pattern.compile("(class|interface|record|enum|@interface) +([A-Za-z_$][\\w$]*)");
    private static final Pattern LAST_NAME = Pattern.compile("([A-Za-z_$][\\w$]*)$");

    private Surface() {}

    /**
     * One public declaration.
     *
     * @param kind {@code class}, {@code interface}, {@code record}, {@code enum} or {@code annotation}
     *     for a type, {@code constructor}, {@code method} or {@code field} for a member
     * @param name the simple name, prefixed by the nested types between it and the file's top level
     *     type, so {@code Builder.build} is the {@code build} method of a nested {@code Builder}
     */
    public record Export(String kind, String name) {

        public Export {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(name, "name");
        }
    }

    /**
     * The public declarations of one source file.
     *
     * @param module the file's path from the repository root, separated by forward slashes
     * @param names what it declares, as {@link #exportsIn(String)} lists them
     */
    public record ModuleExports(String module, List<Export> names) {

        public ModuleExports {
            Objects.requireNonNull(module, "module");
            names = List.copyOf(names);
        }
    }

    /**
     * What a check found.
     *
     * @param ok whether the checked in file is what the sources would generate
     * @param reason one line saying so, or saying what to run
     */
    public record Check(boolean ok, String reason) {

        public Check {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * A type whose body is being read.
     *
     * @param indent the indentation of its declaration line
     * @param name its simple name, which a constructor repeats
     * @param prefix what its members' names are prefixed by: nothing for the top level type
     * @param exposed whether it is public all the way out
     * @param membersPublic whether its members are public without a modifier, as in an interface
     */
    private record Scope(int indent, String name, String prefix, boolean exposed, boolean membersPublic) {}

    /**
     * The public declarations in one source file, each overload once, ordered by name a character at
     * a time.
     */
    public static List<Export> exportsIn(String text) {
        Set<Export> found = new LinkedHashSet<>();
        Deque<Scope> scopes = new ArrayDeque<>();
        for (String line : Text.split(text, "\n")) {
            int indent = indentOf(line);
            String code = line.substring(indent);
            if (isNotADeclaration(code)) {
                continue;
            }
            while (!scopes.isEmpty() && scopes.peek().indent() >= indent) {
                scopes.pop();
            }
            Scope parent = scopes.peek();
            boolean member = parent == null ? indent == 0 : indent == parent.indent() + LEVEL;

            // The modifiers pattern always matches, if only the empty string.
            Matcher modifiers = MODIFIERS.matcher(code);
            modifiers.lookingAt();
            Set<String> words = Set.copyOf(Text.split(modifiers.group(), " "));
            String rest = code.substring(modifiers.end());

            Matcher type = TYPE.matcher(rest);
            if (type.lookingAt()) {
                String keyword = type.group(1);
                String name = parent == null ? type.group(2) : parent.prefix() + type.group(2);
                boolean exposed = member && isPublic(words, parent) && (parent == null || parent.exposed());
                if (exposed) {
                    found.add(new Export(kindOf(keyword), name));
                }
                boolean membersPublic = keyword.equals("interface") || keyword.equals("@interface");
                scopes.push(new Scope(indent, type.group(2), parent == null ? "" : name + ".", exposed, membersPublic));
                continue;
            }
            if (parent != null && member && parent.exposed() && !code.startsWith("@") && isPublic(words, parent)) {
                memberIn(parent, rest).ifPresent(found::add);
            }
        }

        // Ordered by character rather than by locale. A generated file that is checked in has to
        // come out byte identical on every machine, and a locale's collation puts Fourth before
        // first on one runner and after it on another, which turns the check into a coin toss.
        List<Export> names = new ArrayList<>(found);
        names.sort(Comparator.comparing(Export::name));
        return List.copyOf(names);
    }

    /**
     * Every source file under {@code src/main/java} that declares something public, in path order.
     * The entry points in the {@code bin} package are left out: they are what runs the library, not
     * part of what it offers.
     */
    public static List<ModuleExports> collectSurface() {
        Path root = Workspace.root();
        List<ModuleExports> modules = new ArrayList<>();
        for (Path path : Workspace.javaFiles(root, root.resolve(SOURCES))) {
            String key = Workspace.key(root, path);
            if (key.startsWith(ENTRY_POINTS)) {
                continue;
            }
            List<Export> names = exportsIn(Workspace.read(path));
            if (!names.isEmpty()) {
                modules.add(new ModuleExports(key, names));
            }
        }
        return List.copyOf(modules);
    }

    /** The markdown for a surface, ending with exactly one newline. */
    public static String renderSurface(List<ModuleExports> modules) {
        int total = modules.stream().mapToInt(entry -> entry.names().size()).sum();
        List<String> lines = new ArrayList<>(List.of(
                "# Public surface",
                "",
                "Generated by `" + COMMAND + "`. Do not edit by hand.",
                "",
                modules.size() + " modules export " + total + " names.",
                ""));
        for (ModuleExports entry : modules) {
            lines.add("## " + entry.module());
            lines.add("");
            for (Export name : entry.names()) {
                lines.add("- `" + name.kind() + " " + name.name() + "`");
            }
            lines.add("");
        }
        return Text.trimEnd(String.join("\n", lines)) + "\n";
    }

    /** Regenerates {@code docs/surface.md} and returns what it wrote. */
    public static String writeSurface() {
        String text = renderSurface(collectSurface());
        Path output = Workspace.root().resolve(OUTPUT);
        try {
            Files.writeString(output, text);
        } catch (IOException e) {
            throw new UncheckedIOException("could not write " + output, e);
        }
        return text;
    }

    /** Compares {@code docs/surface.md} with what the sources would generate now. */
    public static Check checkSurface() {
        String wanted = renderSurface(collectSurface());
        Path output = Workspace.root().resolve(OUTPUT);
        String current;
        try {
            current = Files.readString(output);
        } catch (NoSuchFileException e) {
            return new Check(false, OUTPUT + " has not been generated");
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + output, e);
        }
        return current.equals(wanted)
                ? new Check(true, "the surface file matches the source")
                : new Check(false, OUTPUT + " is out of date, run " + COMMAND);
    }

    /**
     * Writes the file, or with {@code check} compares it and exits non zero when it is out of date.
     */
    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "write";
        if (mode.equals("check")) {
            Check result = checkSurface();
            System.out.print(result.reason() + "\n");
            System.out.flush();
            if (!result.ok()) {
                System.exit(1);
            }
            return;
        }
        writeSurface();
        System.out.print("wrote " + OUTPUT + "\n");
        System.out.flush();
    }

    private static int indentOf(String line) {
        int indent = 0;
        while (indent < line.length() && line.charAt(indent) == ' ') {
            indent += 1;
        }
        return indent;
    }

    private static boolean isNotADeclaration(String code) {
        return code.isEmpty()
                || code.startsWith("//")
                || code.startsWith("/*")
                || code.startsWith("*")
                || code.startsWith("import ")
                || code.startsWith("package ");
    }

    private static boolean isPublic(Set<String> words, Scope parent) {
        if (words.contains("public")) {
            return true;
        }
        return parent != null && parent.membersPublic() && !words.contains("private") && !words.contains("protected");
    }

    private static String kindOf(String keyword) {
        return keyword.equals("@interface") ? "annotation" : keyword;
    }

    /**
     * Reads a member from the part of its line after the modifiers. What ends the head decides the
     * kind: an opening parenthesis is a method, or a constructor when the head is the type's own name
     * and nothing else; an equals sign or a semicolon is a field. A head that runs into a brace is a
     * compact record constructor, which adds nothing the record does not already say.
     */
    private static Optional<Export> memberIn(Scope parent, String rest) {
        int end = 0;
        while (end < rest.length() && "(=;{".indexOf(rest.charAt(end)) < 0) {
            end += 1;
        }
        if (end == rest.length() || rest.charAt(end) == '{') {
            return Optional.empty();
        }
        String head = Text.trimEnd(rest.substring(0, end));
        Matcher last = LAST_NAME.matcher(head);
        if (!last.find()) {
            return Optional.empty();
        }
        String name = last.group(1);
        boolean nameAlone = head.equals(name);
        if (rest.charAt(end) == '(') {
            if (nameAlone) {
                return name.equals(parent.name())
                        ? Optional.of(new Export("constructor", parent.prefix() + name))
                        : Optional.empty();
            }
            return Optional.of(new Export("method", parent.prefix() + name));
        }
        return nameAlone ? Optional.empty() : Optional.of(new Export("field", parent.prefix() + name));
    }
}
