package io.github.rkaushikdev.stemwright.domain.lexicon;

import static io.github.rkaushikdev.stemwright.domain.lexicon.PartOfSpeech.NOUN;
import static io.github.rkaushikdev.stemwright.domain.lexicon.PartOfSpeech.VERB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.support.Classes;
import io.github.rkaushikdev.stemwright.support.Problems;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ClassRegistryTest {

    private static List<String> keysOf(List<InflectionClass> classes) {
        return classes.stream().map(InflectionClass::key).toList();
    }

    @Nested
    class IsPartOfSpeech {

        @Test
        void acceptsEveryDeclaredPartAndNothingElse() {
            for (PartOfSpeech part : PartOfSpeech.values()) {
                assertThat(PartOfSpeech.isPartOfSpeech(part.key())).isTrue();
            }
            assertThat(PartOfSpeech.isPartOfSpeech("adverb")).isFalse();
        }
    }

    @Nested
    class Of {

        @Test
        void keysEveryClassItWasGiven() {
            assertThat(Classes.LATIN.classOf("third-declension.i-stem").map(InflectionClass::label))
                    .contains("Third declension i stem");
            assertThat(Classes.LATIN.classOf("fourth-declension")).isEmpty();
        }

        @Test
        void defaultsALabelToTheKey() {
            ClassRegistry registry = ClassRegistry.of("x", List.of(InflectionClassInput.of("plain", NOUN)));
            assertThat(registry.requireClass("plain").label()).isEqualTo("plain");
        }

        @Test
        void refusesADuplicateKey() {
            List<InflectionClassInput> inputs =
                    List.of(InflectionClassInput.of("a", NOUN), InflectionClassInput.of("a", NOUN));
            assertThat(Problems.problemMessages(() -> ClassRegistry.of("x", inputs)))
                    .containsExactly("classes.a is declared twice");
        }

        @Test
        void refusesAParentThatWasNeverDeclared() {
            List<InflectionClassInput> inputs =
                    List.of(InflectionClassInput.builder("a", NOUN).parent("b").build());
            assertThat(Problems.problemMessages(() -> ClassRegistry.of("x", inputs)))
                    .containsExactly("classes.a.parent names an unknown class: b");
        }

        @Test
        void refusesAChildWhosePartOfSpeechDiffersFromItsParent() {
            List<InflectionClassInput> inputs = List.of(
                    InflectionClassInput.of("a", NOUN),
                    InflectionClassInput.builder("b", VERB).parent("a").build());
            assertThat(Problems.problemMessages(() -> ClassRegistry.of("x", inputs)))
                    .containsExactly("classes.b.partOfSpeech is verb but its parent a is noun");
        }

        @Test
        void refusesACycleAndNamesEveryClassInIt() {
            List<InflectionClassInput> inputs = List.of(
                    InflectionClassInput.builder("a", NOUN).parent("b").build(),
                    InflectionClassInput.builder("b", NOUN).parent("a").build());
            List<String> messages = Problems.problemMessages(() -> ClassRegistry.of("x", inputs));
            assertThat(messages)
                    .containsExactly(
                            "classes.a.parent takes part in a cycle", "classes.b.parent takes part in a cycle");
        }

        @Test
        void refusesAClassThatIsItsOwnParent() {
            List<InflectionClassInput> inputs =
                    List.of(InflectionClassInput.builder("a", NOUN).parent("a").build());
            assertThat(Problems.problemMessages(() -> ClassRegistry.of("x", inputs)))
                    .containsExactly("classes.a.parent takes part in a cycle");
        }

        @Test
        void refusesAPartOfSpeechThatIsNotOneOfTheSix() {
            List<InflectionClassInput> inputs =
                    List.of(InflectionClassInput.builder("a", "adverb").build());
            assertThat(Problems.problemMessages(() -> ClassRegistry.of("x", inputs)))
                    .containsExactly("classes.a.partOfSpeech is not a part of speech: adverb");
        }
    }

    @Nested
    class Chain {

        @Test
        void startsAtTheClassAndWalksToTheRoot() {
            assertThat(keysOf(Classes.LATIN.chain("third-declension.i-stem")))
                    .containsExactly("third-declension.i-stem", "third-declension");
        }

        @Test
        void isOneLongForARootClass() {
            assertThat(Classes.LATIN.chain("first-declension")).hasSize(1);
        }

        @Test
        void isEmptyForAClassThatIsNotThere() {
            assertThat(Classes.LATIN.chain("nowhere")).isEmpty();
        }
    }

    @Nested
    class IsDescendantOf {

        @Test
        void isTrueForARealAncestor() {
            assertThat(Classes.LATIN.isDescendantOf("second-declension.neuter", "second-declension"))
                    .isTrue();
        }

        @Test
        void isFalseForTheClassItself() {
            assertThat(Classes.LATIN.isDescendantOf("second-declension", "second-declension"))
                    .isFalse();
        }

        @Test
        void isFalseAcrossBranches() {
            assertThat(Classes.LATIN.isDescendantOf("second-declension.neuter", "third-declension"))
                    .isFalse();
        }
    }

    @Nested
    class ChildrenOfAndRoots {

        @Test
        void listsDirectChildrenInKeyOrder() {
            assertThat(keysOf(Classes.LATIN.childrenOf("second-declension")))
                    .containsExactly("second-declension.neuter", "second-declension.r-stem");
        }

        @Test
        void givesNothingForALeaf() {
            assertThat(Classes.LATIN.childrenOf("first-declension")).isEmpty();
        }

        @Test
        void listsTheClassesWithNoParent() {
            assertThat(keysOf(Classes.LATIN.roots()))
                    .containsExactly(
                            "first-conjugation",
                            "first-declension",
                            "second-declension",
                            "third-conjugation",
                            "third-declension");
        }
    }

    @Nested
    class ClassesFor {

        @Test
        void splitsTheRegistryByPartOfSpeech() {
            assertThat(keysOf(Classes.LATIN.classesFor(VERB)))
                    .containsExactly("first-conjugation", "third-conjugation", "third-conjugation.io");
            assertThat(Classes.LATIN.classesFor(NOUN)).hasSize(6);
            assertThat(Classes.LATIN.classesFor(PartOfSpeech.PRONOUN)).isEmpty();
        }
    }

    @Nested
    class ResolveThroughChain {

        private final Map<String, String> profiles =
                Map.of("second-declension", "prof-second", "third-declension", "prof-third");

        private Optional<String> lookup(String key) {
            return Optional.ofNullable(profiles.get(key));
        }

        @Test
        void takesTheClassOwnValueWhenItHasOne() {
            assertThat(Classes.LATIN.resolveThroughChain("second-declension", this::lookup))
                    .contains(new ClassRegistry.Resolved<>("prof-second", "second-declension"));
        }

        @Test
        void inheritsFromTheNearestAncestorThatHasOne() {
            assertThat(Classes.LATIN.resolveThroughChain("second-declension.neuter", this::lookup))
                    .contains(new ClassRegistry.Resolved<>("prof-second", "second-declension"));
        }

        @Test
        void givesNothingWhenNoClassInTheChainHasOne() {
            assertThat(Classes.LATIN.resolveThroughChain("first-declension", this::lookup))
                    .isEmpty();
        }

        @Test
        void reportsWhichClassAnsweredSoAReportCanSayWhereItCameFrom() {
            Optional<ClassRegistry.Resolved<String>> resolved =
                    Classes.LATIN.resolveThroughChain("third-declension.i-stem", this::lookup);
            assertThat(resolved.map(ClassRegistry.Resolved::through)).contains("third-declension");
        }
    }

    @Nested
    class RequireClass {

        @Test
        void namesTheLanguageWhenTheClassIsMissing() {
            assertThatThrownBy(() -> Classes.LATIN.requireClass("fourth"))
                    .hasMessageContaining("is not a class of latin: fourth");
        }
    }
}
