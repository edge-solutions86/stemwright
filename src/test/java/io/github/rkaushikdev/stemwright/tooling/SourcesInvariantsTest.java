package io.github.rkaushikdev.stemwright.tooling;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.domain.text.Text;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Byte level checks over every Java source file in the repository.
 *
 * <p>The reason these exist is that a checkout on Windows with the default git settings rewrites
 * every line ending, and a file that then carries a carriage return breaks a text block that is
 * compared against a fixture and any test that asserts on exact output. A {@code .gitattributes}
 * fixes the cause; this class is what proves the fix is still in place.
 *
 * <p>Every check counts what it read and refuses on zero, because a walker that finds no files
 * reports total success and announces nothing. Every checker is also run against a planted offender
 * first, so that a predicate that can never fire cannot pass for a clean tree.
 */
class SourcesInvariantsTest {

    private static final List<String> ROOTS = List.of("src/main/java", "src/test/java");
    private static final String MAIN = "src/main/java/";
    private static final String ENTRY_POINTS = MAIN + "io/github/rkaushikdev/stemwright/bin/";

    private static final String CARRIAGE_RETURN = Character.toString(0x0d);
    private static final String BOM = Character.toString(0xfeff);
    private static final String TAB = Character.toString(0x09);
    private static final String NEWLINE = Character.toString(0x0a);

    private static final Pattern PACKAGE_LINE = Pattern.compile("^package ([\\w.]+);$", Pattern.MULTILINE);
    private static final Pattern STANDARD_STREAM =
            Pattern.compile("\\bSystem\\.(?:out|err)\\b|\\.printStackTrace\\(\\)");

    private static final Map<String, String> CONTENTS = readSources();

    private static Map<String, String> readSources() {
        Path root = Workspace.root();
        Map<String, String> contents = new LinkedHashMap<>();
        for (String name : ROOTS) {
            for (Path path : Workspace.javaFiles(root, root.resolve(name))) {
                contents.put(Workspace.key(root, path), Workspace.read(path));
            }
        }
        return contents;
    }

    private static List<Integer> offendingLines(String text, Predicate<String> predicate) {
        List<String> lines = Text.split(text, NEWLINE);
        List<Integer> offending = new ArrayList<>();
        for (int index = 0; index < lines.size(); index += 1) {
            if (predicate.test(lines.get(index))) {
                offending.add(index + 1);
            }
        }
        return offending;
    }

    private static boolean hasCarriageReturn(String text) {
        return text.contains(CARRIAGE_RETURN);
    }

    private static boolean hasByteOrderMark(String text) {
        return text.startsWith(BOM);
    }

    private static boolean endsWithOneNewline(String text) {
        return text.endsWith(NEWLINE) && !text.endsWith(NEWLINE + NEWLINE);
    }

    private static boolean isTabIndented(String line) {
        return line.startsWith(TAB);
    }

    private static boolean hasTrailingSpace(String line) {
        return !line.equals(Text.trimEnd(line));
    }

    private static boolean hasStrayControl(String text) {
        return text.codePoints().anyMatch(point -> point < 0x20 && point != 0x0a && point != 0x09);
    }

    /** The package the file declares, when it declares one. */
    private static Optional<String> declaredPackage(String text) {
        Matcher match = PACKAGE_LINE.matcher(text);
        return match.find() ? Optional.of(match.group(1)) : Optional.empty();
    }

    /** The package a file under {@code src/main/java} has to declare, read from its directory. */
    private static String packageOfDirectory(String key) {
        String relative = key.substring(MAIN.length());
        int slash = relative.lastIndexOf('/');
        return slash < 0 ? "" : relative.substring(0, slash).replace('/', '.');
    }

    private static boolean declaresItsDirectory(String key, String text) {
        return declaredPackage(text).equals(Optional.of(packageOfDirectory(key)));
    }

    private static boolean writesToAStandardStream(String text) {
        return STANDARD_STREAM.matcher(text).find();
    }

    @Nested
    class TheSourceWalker {

        @Test
        void foundFilesToCheck() {
            assertThat(CONTENTS.size()).isGreaterThan(80);
        }

        @Test
        void foundFilesUnderEveryRootItWasToldAbout() {
            for (String name : ROOTS) {
                assertThat(CONTENTS.keySet())
                        .anySatisfy(path -> assertThat(path).startsWith(name + "/"));
            }
        }

        @Test
        void wouldNoticeACarriageReturnIfOneWereThere() {
            String planted = "final int a = 1;" + CARRIAGE_RETURN + NEWLINE;
            assertThat(hasCarriageReturn(planted)).isTrue();
            assertThat(hasCarriageReturn("final int a = 1;" + NEWLINE)).isFalse();
        }

        @Test
        void wouldNoticeAFileThatDoesNotEndWithANewline() {
            assertThat(endsWithOneNewline("final int a = 1;")).isFalse();
            assertThat(endsWithOneNewline("final int a = 1;" + NEWLINE + NEWLINE))
                    .isFalse();
            assertThat(endsWithOneNewline("final int a = 1;" + NEWLINE)).isTrue();
        }
    }

    @Nested
    class EverySourceFile {

        @Test
        void hasNoCarriageReturnInIt() {
            List<String> offenders = CONTENTS.entrySet().stream()
                    .filter(entry -> hasCarriageReturn(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .toList();
            assertThat(offenders).isEmpty();
        }

        @Test
        void hasNoByteOrderMark() {
            assertThat(hasByteOrderMark(BOM + "package planted;" + NEWLINE)).isTrue();

            List<String> offenders = CONTENTS.entrySet().stream()
                    .filter(entry -> hasByteOrderMark(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .toList();
            assertThat(offenders).isEmpty();
        }

        @Test
        void endsWithExactlyOneNewline() {
            List<String> offenders = CONTENTS.entrySet().stream()
                    .filter(entry -> !endsWithOneNewline(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .toList();
            assertThat(offenders).isEmpty();
        }

        @Test
        void hasNoTabUsedForIndentation() {
            String planted = "class Planted {" + NEWLINE + TAB + "int a;" + NEWLINE + "}" + NEWLINE;
            assertThat(offendingLines(planted, SourcesInvariantsTest::isTabIndented))
                    .containsExactly(2);

            List<String> offenders = new ArrayList<>();
            for (Map.Entry<String, String> entry : CONTENTS.entrySet()) {
                if (!offendingLines(entry.getValue(), SourcesInvariantsTest::isTabIndented)
                        .isEmpty()) {
                    offenders.add(entry.getKey());
                }
            }
            assertThat(offenders).isEmpty();
        }

        @Test
        void hasNoTrailingWhitespaceOnAnyLine() {
            String noBreakSpace = Character.toString(0xa0);
            String planted = "int a; " + NEWLINE + "int b;" + NEWLINE + "int c;" + noBreakSpace + NEWLINE;
            assertThat(offendingLines(planted, SourcesInvariantsTest::hasTrailingSpace))
                    .containsExactly(1, 3);

            List<String> offenders = new ArrayList<>();
            for (Map.Entry<String, String> entry : CONTENTS.entrySet()) {
                List<Integer> lines = offendingLines(entry.getValue(), SourcesInvariantsTest::hasTrailingSpace);
                if (!lines.isEmpty()) {
                    offenders.add(entry.getKey() + ":"
                            + String.join(
                                    ",", lines.stream().map(String::valueOf).toList()));
                }
            }
            assertThat(offenders).isEmpty();
        }

        @Test
        void hasNoStrayControlCharacter() {
            assertThat(hasStrayControl("int a;" + Character.toString(0x07) + NEWLINE))
                    .isTrue();
            assertThat(hasStrayControl(TAB + "int a;" + NEWLINE)).isFalse();

            List<String> offenders = CONTENTS.entrySet().stream()
                    .filter(entry -> hasStrayControl(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .toList();
            assertThat(offenders).isEmpty();
        }
    }

    @Nested
    class EverySourceFileUnderMain {

        private final List<Map.Entry<String, String>> sources = CONTENTS.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(MAIN))
                .toList();

        @Test
        void declaresThePackageItsDirectoryNames() {
            String key = MAIN + "io/github/rkaushikdev/stemwright/planted/Planted.java";
            assertThat(declaresItsDirectory(key, "package io.github.rkaushikdev.stemwright.planted;" + NEWLINE))
                    .isTrue();
            assertThat(declaresItsDirectory(key, "package io.github.rkaushikdev.stemwright.elsewhere;" + NEWLINE))
                    .isFalse();
            assertThat(declaresItsDirectory(key, "final class Planted {}" + NEWLINE))
                    .isFalse();

            List<String> offenders = sources.stream()
                    .filter(entry -> !declaresItsDirectory(entry.getKey(), entry.getValue()))
                    .map(Map.Entry::getKey)
                    .toList();
            assertThat(offenders).isEmpty();
        }

        @Test
        void doesNotWriteToTheStandardStreamsOutsideTheEntryPoints() {
            assertThat(writesToAStandardStream("System.out.print(line);")).isTrue();
            assertThat(writesToAStandardStream("System.err.println(line);")).isTrue();
            assertThat(writesToAStandardStream("failure.printStackTrace();")).isTrue();
            assertThat(writesToAStandardStream("Systems.output.print(line);")).isFalse();

            List<String> offenders = sources.stream()
                    .filter(entry -> !entry.getKey().startsWith(ENTRY_POINTS))
                    .filter(entry -> writesToAStandardStream(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .toList();
            assertThat(offenders).isEmpty();
        }

        @Test
        void foundEnoughFilesForThoseTwoChecksToMeanSomething() {
            assertThat(sources.size()).isGreaterThan(50);
        }
    }
}
