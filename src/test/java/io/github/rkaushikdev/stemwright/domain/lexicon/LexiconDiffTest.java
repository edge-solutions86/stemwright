package io.github.rkaushikdev.stemwright.domain.lexicon;

import static io.github.rkaushikdev.stemwright.support.Lexemes.BELLUM;
import static io.github.rkaushikdev.stemwright.support.Lexemes.PUELLA;
import static io.github.rkaushikdev.stemwright.support.Lexemes.makeLexeme;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LexiconDiffTest {

    private static final Lexeme CHANGED_GLOSS =
            makeLexeme(LexemeInput.builder("puella", "first-declension", StemSet.of(StemInput.of("base", "puell")))
                    .inherent(Map.of("gender", "fem"))
                    .glosses("girl", "maiden")
                    .tags("core", "chapter-one")
                    .build());

    private static final Lexeme CHANGED_CLASS =
            makeLexeme(LexemeInput.builder("puella", "third-declension", StemSet.of(StemInput.of("base", "puell")))
                    .inherent(Map.of("gender", "fem"))
                    .glosses("girl")
                    .tags("core", "chapter-one")
                    .build());

    private static final Lexeme CHANGED_STEM = makeLexeme(LexemeInput.builder(
                    "puella",
                    "first-declension",
                    StemSet.of(StemInput.of("base", "puell"), StemInput.of("oblique", "puella")))
            .inherent(Map.of("gender", "fem"))
            .glosses("girl")
            .tags("core", "chapter-one")
            .build());

    @Nested
    class Between {

        @Test
        void reportsNothingWhenTheTwoSidesAreTheSame() {
            assertThat(LexiconDiff.between(List.of(PUELLA, BELLUM), List.of(PUELLA, BELLUM)))
                    .isEmpty();
        }

        @Test
        void reportsAnAddition() {
            List<LexemeChange> changes = LexiconDiff.between(List.of(PUELLA), List.of(PUELLA, BELLUM));
            assertThat(changes).hasSize(1);
            assertThat(changes.get(0).kind()).isEqualTo(ChangeKind.ADDED);
            assertThat(changes.get(0).lemma()).isEqualTo("bellum");
        }

        @Test
        void reportsARemoval() {
            List<LexemeChange> changes = LexiconDiff.between(List.of(PUELLA, BELLUM), List.of(PUELLA));
            assertThat(changes.get(0).kind()).isEqualTo(ChangeKind.REMOVED);
            assertThat(changes.get(0).lemma()).isEqualTo("bellum");
        }

        @Test
        void reportsAChangeAtTheFieldThatChanged() {
            List<LexemeChange> changes = LexiconDiff.between(List.of(PUELLA), List.of(CHANGED_GLOSS));
            assertThat(changes).hasSize(1);
            assertThat(changes.get(0).kind()).isEqualTo(ChangeKind.CHANGED);
            assertThat(changes.get(0).fields()).containsExactly(new FieldChange("glosses", "girl", "girl; maiden"));
        }

        @Test
        void reportsAClassChange() {
            List<LexemeChange> changes = LexiconDiff.between(List.of(PUELLA), List.of(CHANGED_CLASS));
            assertThat(changes.get(0).fields().get(0))
                    .isEqualTo(new FieldChange("class", "first-declension", "third-declension"));
        }

        @Test
        void reportsAStemThatWasAdded() {
            List<LexemeChange> changes = LexiconDiff.between(List.of(PUELLA), List.of(CHANGED_STEM));
            assertThat(changes.get(0).fields()).containsExactly(new FieldChange("stem.oblique", "", "puella"));
        }

        @Test
        void listsTheFieldsInNameOrder() {
            Lexeme both = makeLexeme(
                    LexemeInput.builder("puella", "third-declension", StemSet.of(StemInput.of("base", "puell")))
                            .inherent(Map.of("gender", "fem"))
                            .glosses("girl", "maiden")
                            .tags("core", "chapter-one")
                            .build());
            assertThat(LexiconDiff.between(List.of(PUELLA), List.of(both)).get(0).fields().stream()
                            .map(FieldChange::field))
                    .containsExactly("class", "glosses");
        }

        @Test
        void reportsAdditionsAndRemovalsTogether() {
            List<LexemeChange> changes = LexiconDiff.between(List.of(PUELLA), List.of(BELLUM));
            assertThat(LexiconDiff.ofKind(changes, ChangeKind.ADDED)).hasSize(1);
            assertThat(LexiconDiff.ofKind(changes, ChangeKind.REMOVED)).hasSize(1);
        }
    }

    @Nested
    class SchedulingChanges {

        @Test
        void countsAnAdditionAndARemoval() {
            assertThat(LexiconDiff.schedulingChanges(LexiconDiff.between(List.of(PUELLA), List.of(BELLUM))))
                    .hasSize(2);
        }

        @Test
        void countsAClassChange() {
            assertThat(LexiconDiff.schedulingChanges(LexiconDiff.between(List.of(PUELLA), List.of(CHANGED_CLASS))))
                    .hasSize(1);
        }

        @Test
        void doesNotCountAGlossChange() {
            assertThat(LexiconDiff.schedulingChanges(LexiconDiff.between(List.of(PUELLA), List.of(CHANGED_GLOSS))))
                    .isEmpty();
        }

        @Test
        void doesNotCountAStemChange() {
            assertThat(LexiconDiff.schedulingChanges(LexiconDiff.between(List.of(PUELLA), List.of(CHANGED_STEM))))
                    .isEmpty();
        }
    }

    @Nested
    class Describe {

        @Test
        void readsAnAdditionAndARemovalOut() {
            assertThat(LexiconDiff.between(List.of(), List.of(PUELLA)).get(0).describe())
                    .isEqualTo("added puella");
            assertThat(LexiconDiff.between(List.of(PUELLA), List.of()).get(0).describe())
                    .isEqualTo("removed puella");
        }

        @Test
        void namesTheFieldsOfAChange() {
            assertThat(LexiconDiff.between(List.of(PUELLA), List.of(CHANGED_GLOSS))
                            .get(0)
                            .describe())
                    .isEqualTo("changed puella: glosses from girl to girl; maiden");
        }

        @Test
        void saysNothingWhereAFieldWasEmpty() {
            assertThat(LexiconDiff.between(List.of(PUELLA), List.of(CHANGED_STEM))
                            .get(0)
                            .describe())
                    .contains("from nothing to");
        }
    }

    @Nested
    class Summarise {

        @Test
        void saysSoWhenNothingChanged() {
            assertThat(LexiconDiff.summarise(LexiconDiff.between(List.of(PUELLA), List.of(PUELLA))))
                    .isEqualTo("nothing changed");
        }

        @Test
        void countsEachKindAndHowManyMoveASchedule() {
            List<LexemeChange> changes = LexiconDiff.between(List.of(PUELLA, BELLUM), List.of(CHANGED_CLASS));
            assertThat(LexiconDiff.summarise(changes))
                    .isEqualTo("0 added, 1 removed, 1 changed, 2 of which move a schedule");
        }
    }
}
