package io.github.rkaushikdev.stemwright.domain.ortho;

import static io.github.rkaushikdev.stemwright.domain.ortho.FoldStep.FINAL_FORMS;
import static io.github.rkaushikdev.stemwright.domain.ortho.FoldStep.LOWERCASE;
import static io.github.rkaushikdev.stemwright.domain.ortho.FoldStep.MARKS;
import static io.github.rkaushikdev.stemwright.support.Ortho.GREEK;
import static io.github.rkaushikdev.stemwright.support.Ortho.LATIN;
import static io.github.rkaushikdev.stemwright.support.Problems.problemMessages;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.ortho.OrthoProfileInput.Level;
import io.github.rkaushikdev.stemwright.domain.ortho.ResolvedTolerance.Source;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrthoProfileTest {

    @Nested
    class Of {

        @Test
        void keepsTheLevelsInTheOrderTheyWereDeclared() {
            assertThat(GREEK.levelKeys()).containsExactly("exact", "standard", "lenient", "unaccented");
        }

        @Test
        void sortsTheFoldsOfEachLevelIntoTheCanonicalOrder() {
            assertThat(GREEK.requireLevel("lenient").folds()).containsExactly(LOWERCASE, FINAL_FORMS, MARKS);
        }

        @Test
        void defaultsALabelToTheKey() {
            assertThat(LATIN.requireLevel("exact").label()).isEqualTo("exact");
        }

        @Test
        void refusesARepeatedLevelKey() {
            assertThat(problemMessages(() -> OrthoProfile.of(
                            OrthoProfileInput.of("x", "a", Level.of("a", List.of()), Level.of("a", List.of("marks"))))))
                    .containsExactly("levels.1 repeats the level key a");
        }

        @Test
        void refusesAFoldStepNobodyDeclared() {
            assertThat(problemMessages(() -> OrthoProfile.of(OrthoProfileInput.of(
                            "x", "b", Level.of("a", List.of("vowels")), Level.of("b", List.of("marks"))))))
                    .containsExactly("levels.0 folds is not a fold step: vowels");
        }

        @Test
        void refusesADefaultThatNamesNoLevel() {
            assertThat(problemMessages(() -> OrthoProfile.of(OrthoProfileInput.of("x", "b", Level.of("a", List.of())))))
                    .containsExactly("defaultLevel names a level that is not declared: b");
        }

        @Test
        void refusesAProfileWithNoLevels() {
            assertThat(problemMessages(() -> OrthoProfile.of(OrthoProfileInput.of("x", "a"))))
                    .containsExactly("levels must declare at least one level");
        }
    }

    @Nested
    class Lookups {

        @Test
        void findsALevelAndReportsAMiss() {
            assertThat(GREEK.levelOf("exact"))
                    .hasValueSatisfying(level -> assertThat(level.folds()).isEmpty());
            assertThat(GREEK.levelOf("sloppy")).isEmpty();
            assertThatThrownBy(() -> GREEK.requireLevel("sloppy")).hasMessageContaining("is not a level of greek");
        }

        @Test
        void readsTheDefaultLevelBack() {
            assertThat(GREEK.defaultToleranceLevel().key()).isEqualTo("standard");
        }
    }

    @Nested
    class IsStricterThan {

        private final ToleranceLevel exact = GREEK.requireLevel("exact");
        private final ToleranceLevel standard = GREEK.requireLevel("standard");
        private final ToleranceLevel lenient = GREEK.requireLevel("lenient");
        private final ToleranceLevel unaccented = GREEK.requireLevel("unaccented");

        @Test
        void callsASubsetOfFoldsStricter() {
            assertThat(exact.isStricterThan(standard)).isTrue();
            assertThat(standard.isStricterThan(lenient)).isTrue();
        }

        @Test
        void isNotTrueInTheOtherDirection() {
            assertThat(lenient.isStricterThan(standard)).isFalse();
        }

        @Test
        void isNotTrueOfALevelWithItself() {
            assertThat(standard.isStricterThan(standard)).isFalse();
        }

        @Test
        void leavesLevelsThatFoldDifferentThingsIncomparable() {
            assertThat(standard.isStricterThan(unaccented)).isFalse();
            assertThat(unaccented.isStricterThan(standard)).isFalse();
            assertThat(standard.isComparableTo(unaccented)).isFalse();
        }

        @Test
        void callsALevelComparableWithItself() {
            assertThat(standard.isComparableTo(standard)).isTrue();
        }
    }

    @Nested
    class ResolveTolerance {

        @Test
        void fallsBackToTheLanguageDefault() {
            assertThat(GREEK.resolveTolerance())
                    .isEqualTo(new ResolvedTolerance(GREEK.requireLevel("standard"), Source.LANGUAGE));
        }

        @Test
        void takesTheDeckLevelOverTheDefault() {
            ToleranceRequest request =
                    ToleranceRequest.builder().deckLevel("lenient").build();
            assertThat(GREEK.resolveTolerance(request).source()).isEqualTo(Source.DECK);
        }

        @Test
        void takesTheCellLevelOverTheDeck() {
            ResolvedTolerance resolved = GREEK.resolveTolerance(ToleranceRequest.builder()
                    .deckLevel("lenient")
                    .cellLevel("exact")
                    .build());
            assertThat(resolved.level().key()).isEqualTo("exact");
            assertThat(resolved.source()).isEqualTo(Source.CELL);
        }

        @Test
        void takesTheAssignmentLevelOverEverything() {
            ResolvedTolerance resolved = GREEK.resolveTolerance(ToleranceRequest.builder()
                    .deckLevel("lenient")
                    .cellLevel("exact")
                    .assignmentLevel("standard")
                    .build());
            assertThat(resolved.level().key()).isEqualTo("standard");
            assertThat(resolved.source()).isEqualTo(Source.ASSIGNMENT);
        }

        @Test
        void refusesALevelTheLanguageDoesNotDeclareWhereverItCameFrom() {
            ToleranceRequest request =
                    ToleranceRequest.builder().cellLevel("sloppy").build();
            assertThatThrownBy(() -> GREEK.resolveTolerance(request)).hasMessageContaining("not a level");
        }
    }

    @Nested
    class Describe {

        @Test
        void saysSoWhenNothingIsFolded() {
            ResolvedTolerance resolved = GREEK.resolveTolerance(
                    ToleranceRequest.builder().deckLevel("exact").build());
            assertThat(resolved.describe()).isEqualTo("Exact marks exactly as written, set by the deck");
        }

        @Test
        void readsTheFoldsOutAndNamesWhereTheLevelCameFrom() {
            ResolvedTolerance resolved = GREEK.resolveTolerance(
                    ToleranceRequest.builder().cellLevel("lenient").build());
            assertThat(resolved.describe())
                    .isEqualTo("Lenient will ignore capitals and accept a medial letter at the end of a word and ignore"
                            + " accents and breathings, set by the cell");
        }
    }
}
