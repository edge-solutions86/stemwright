package io.github.rkaushikdev.stemwright.tooling;

import static io.github.rkaushikdev.stemwright.tooling.Surface.exportsIn;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.tooling.Surface.Export;
import org.junit.jupiter.api.Test;

/**
 * Which declarations count as public. A declaration line says public on its own; whether anybody
 * outside can reach it depends on every type around it, which is what these pin down.
 */
class SurfaceScopeTest {

    private static String lines(String... lines) {
        return String.join("\n", lines);
    }

    @Test
    void hidesThePublicMembersOfANestedTypeThatIsNotPublic() {
        String text = lines(
                "public final class Stores {",
                "    public static Stores create() {}",
                "",
                "    private static final class Hidden implements Store {",
                "        @Override",
                "        public void put(String value) {}",
                "    }",
                "",
                "    public static final class Shown {",
                "        public void take() {}",
                "    }",
                "}");
        assertThat(exportsIn(text))
                .containsExactly(
                        new Export("class", "Shown"),
                        new Export("method", "Shown.take"),
                        new Export("class", "Stores"),
                        new Export("method", "create"));
    }

    @Test
    void readsTheMembersOfAnInterfaceAsPublicWithoutAModifier() {
        String text = lines(
                "public interface Store {",
                "    int LIMIT = 3;",
                "",
                "    Optional<String> get(String key);",
                "",
                "    default boolean has(String key) {",
                "        return get(key).isPresent();",
                "    }",
                "",
                "    <T> List<T> all(Class<T> type);",
                "",
                "    private void helper() {}",
                "",
                "    record Entry(String key) {}",
                "}");
        assertThat(exportsIn(text))
                .containsExactly(
                        new Export("record", "Entry"),
                        new Export("field", "LIMIT"),
                        new Export("interface", "Store"),
                        new Export("method", "all"),
                        new Export("method", "get"),
                        new Export("method", "has"));
    }

    @Test
    void hidesEverythingInAFileWhoseTopLevelTypeIsNotPublic() {
        String text = lines(
                "final class Internal {",
                "    public static Internal create() {}",
                "",
                "    public record Part(int value) {}",
                "}",
                "",
                "public final class Outside {",
                "    public static void open() {}",
                "}");
        assertThat(exportsIn(text)).containsExactly(new Export("class", "Outside"), new Export("method", "open"));
    }

    @Test
    void hidesWhatIsDeclaredInsideAMethodBody() {
        String text = lines(
                "public final class Sorting {",
                "    public static Comparator<String> order() {",
                "        record Pair(String left, String right) {",
                "            public String joined() {}",
                "        }",
                "        return new Comparator<>() {",
                "            @Override",
                "            public int compare(String left, String right) {}",
                "        };",
                "    }",
                "}");
        assertThat(exportsIn(text)).containsExactly(new Export("class", "Sorting"), new Export("method", "order"));
    }

    @Test
    void namesANestedDeclarationAfterTheTypesAroundIt() {
        String text = lines(
                "public record Policy(int threshold) {",
                "    public static final class Builder {",
                "        public Builder threshold(int value) {}",
                "",
                "        public static final class Step {",
                "            public static final int FIRST = 1;",
                "        }",
                "    }",
                "}");
        assertThat(exportsIn(text))
                .containsExactly(
                        new Export("class", "Builder"),
                        new Export("class", "Builder.Step"),
                        new Export("field", "Builder.Step.FIRST"),
                        new Export("method", "Builder.threshold"),
                        new Export("record", "Policy"));
    }

    @Test
    void listsEachOverloadOnceAndSkipsACompactConstructor() {
        String text = lines(
                "public record Range(int from, int to) {",
                "    public Range {",
                "        check(from, to);",
                "    }",
                "",
                "    public Range(int only) {",
                "        this(only, only);",
                "    }",
                "",
                "    public static Range of(int only) {}",
                "",
                "    public static Range of(int from, int to) {}",
                "}");
        assertThat(exportsIn(text))
                .containsExactly(
                        new Export("record", "Range"), new Export("constructor", "Range"), new Export("method", "of"));
    }
}
