package io.github.rkaushikdev.stemwright.tooling;

import static io.github.rkaushikdev.stemwright.tooling.Surface.checkSurface;
import static io.github.rkaushikdev.stemwright.tooling.Surface.collectSurface;
import static io.github.rkaushikdev.stemwright.tooling.Surface.exportsIn;
import static io.github.rkaushikdev.stemwright.tooling.Surface.renderSurface;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.tooling.Surface.Check;
import io.github.rkaushikdev.stemwright.tooling.Surface.Export;
import io.github.rkaushikdev.stemwright.tooling.Surface.ModuleExports;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SurfaceTest {

    private static final String ENTRY_POINTS = "src/main/java/io/github/rkaushikdev/stemwright/bin/";

    @Nested
    class ExportsIn {

        @Test
        void findsEveryKindOfExport() {
            String text = String.join(
                    "\n",
                    "public final class Third {",
                    "    public static final int SECOND = 1;",
                    "    public Third() {}",
                    "    public static void first() {}",
                    "    public interface Fourth {}",
                    "    public record Fifth(int value) {}",
                    "    public enum Sixth {",
                    "        ONE",
                    "    }",
                    "    public @interface Seventh {}",
                    "}");
            List<Export> exports = exportsIn(text);
            assertThat(exports)
                    .extracting(Export::name)
                    .containsExactly("Fifth", "Fourth", "SECOND", "Seventh", "Sixth", "Third", "Third", "first");
            assertThat(exports)
                    .extracting(Export::kind)
                    .containsExactly(
                            "record", "interface", "field", "annotation", "enum", "class", "constructor", "method");
        }

        @Test
        void sortsByCodePointSoTheFileIsTheSameOnEveryMachine() {
            List<String> lines = List.of(
                    "public final class Names {",
                    "    public static final int beta = 1;",
                    "    public static final int Alpha = 2;",
                    "}");
            assertThat(exportsIn(String.join(Character.toString(0x0a), lines)))
                    .extracting(Export::name)
                    .containsExactly("Alpha", "Names", "beta");
        }

        @Test
        void ignoresALineThatOnlyMentionsTheWordPublic() {
            assertThat(exportsIn("// this class is not public at all")).isEmpty();
            assertThat(exportsIn("final int publicCount = 1;")).isEmpty();
            String commented = String.join(
                    "\n",
                    "public final class Quiet {",
                    "    /** public static void documented() {} */",
                    "    // public static void remarked() {}",
                    "     * public static void continued() {}",
                    "}");
            assertThat(exportsIn(commented)).containsExactly(new Export("class", "Quiet"));
        }

        @Test
        void ignoresAnImportAndThePackageLine() {
            assertThat(exportsIn("package io.github.rkaushikdev.stemwright.other;"))
                    .isEmpty();
            assertThat(exportsIn("import io.github.rkaushikdev.stemwright.other.Other;"))
                    .isEmpty();
            assertThat(exportsIn("import static io.github.rkaushikdev.stemwright.other.Other.build;"))
                    .isEmpty();
        }

        @Test
        void readsTheKindAlongsideTheName() {
            assertThat(exportsIn("public record Build(int value) {}")).containsExactly(new Export("record", "Build"));
        }
    }

    @Nested
    class CollectSurface {

        @Test
        void findsASurfaceWorthChecking() {
            List<ModuleExports> modules = collectSurface();
            assertThat(modules.size()).isGreaterThan(40);
            assertThat(modules).allSatisfy(entry -> assertThat(entry.names()).isNotEmpty());
        }

        @Test
        void leavesTheEntryPointsOut() {
            Path entryPoint = Workspace.root().resolve(ENTRY_POINTS).resolve("Stemwright.java");
            assertThat(entryPoint).isRegularFile();
            assertThat(exportsIn(Workspace.read(entryPoint))).isNotEmpty();

            List<ModuleExports> modules = collectSurface();
            assertThat(modules).noneSatisfy(entry -> assertThat(entry.module()).startsWith(ENTRY_POINTS));
        }

        @Test
        void namesModulesByAForwardSlashedPathWhateverThePlatform() {
            List<ModuleExports> modules = collectSurface();
            String backslash = Character.toString(0x5c);
            assertThat(modules).allSatisfy(entry -> assertThat(entry.module())
                    .startsWith("src/main/java/")
                    .doesNotContain(backslash));
        }
    }

    @Nested
    class RenderSurface {

        @Test
        void countsTheModulesAndTheNames() {
            String text = renderSurface(List.of(
                    new ModuleExports("src/main/java/A.java", List.of(new Export("method", "one"))),
                    new ModuleExports("src/main/java/B.java", List.of(new Export("field", "two")))));
            assertThat(text).contains("2 modules export 2 names.");
            assertThat(text).contains("## src/main/java/A.java");
            assertThat(text).contains("- `method one`");
        }

        @Test
        void endsWithExactlyOneNewline() {
            String text = renderSurface(
                    List.of(new ModuleExports("src/main/java/A.java", List.of(new Export("field", "x")))));
            assertThat(text).endsWith("\n");
            assertThat(text).doesNotEndWith("\n\n");
        }
    }

    @Nested
    class TheCheckedInSurface {

        @Test
        void matchesWhatTheSourceExports() {
            Check result = checkSurface();
            assertThat(result.reason()).isEqualTo("the surface file matches the source");
            assertThat(result.ok()).isTrue();
        }

        @Test
        void isAFileSomebodyCanRead() {
            String text = Workspace.read(Workspace.root().resolve("docs").resolve("surface.md"));
            assertThat(text).startsWith("# Public surface");
            assertThat(text).contains("Do not edit by hand.");
        }
    }
}
