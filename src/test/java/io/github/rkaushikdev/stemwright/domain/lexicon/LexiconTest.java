package io.github.rkaushikdev.stemwright.domain.lexicon;

import static io.github.rkaushikdev.stemwright.support.Lexemes.AGER;
import static io.github.rkaushikdev.stemwright.support.Lexemes.BELLUM;
import static io.github.rkaushikdev.stemwright.support.Lexemes.PUELLA;
import static io.github.rkaushikdev.stemwright.support.Lexemes.makeLexeme;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.errors.ConflictException;
import io.github.rkaushikdev.stemwright.domain.errors.NotFoundException;
import io.github.rkaushikdev.stemwright.support.Classes;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LexiconTest {

    private static final Lexicon BASE = Lexicon.build("latin", List.of(PUELLA, BELLUM, AGER));

    private static final Lexeme NAUTA = makeLexeme(LexemeInput.builder("nauta", "first-declension", baseStem("naut"))
            .inherent(Map.of("gender", "masc"))
            .glosses("sailor")
            .tags("core")
            .build());

    private static StemSet baseStem(String form) {
        return StemSet.of(StemInput.of("base", form));
    }

    private static List<String> lemmasOf(List<Lexeme> entries) {
        return entries.stream().map(Lexeme::lemma).toList();
    }

    @Nested
    class Build {

        @Test
        void indexesEveryEntryById() {
            assertThat(BASE.size()).isEqualTo(3);
            assertThat(BASE.lexemeById(PUELLA.id()).map(Lexeme::lemma)).contains("puella");
        }

        @Test
        void refusesTheSameIdTwice() {
            assertThatThrownBy(() -> Lexicon.build("latin", List.of(PUELLA, PUELLA)))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void buildsAnEmptyOne() {
            assertThat(Lexicon.empty("latin").size()).isZero();
            assertThat(Lexicon.empty("latin").allLexemes()).isEmpty();
        }

        @Test
        void returnsEntriesInLemmaOrder() {
            assertThat(lemmasOf(BASE.allLexemes())).containsExactly("ager", "bellum", "puella");
        }
    }

    @Nested
    class Lookups {

        @Test
        void findsByIdAndReportsAMiss() {
            assertThat(BASE.lexemeById("lex_zzzzzz")).isEmpty();
            assertThatThrownBy(() -> BASE.requireLexeme("lex_zzzzzz")).isInstanceOf(NotFoundException.class);
        }

        @Test
        void findsEveryHomographOfALemmaInOrder() {
            Lexeme second = makeLexeme(LexemeInput.builder("ager", "second-declension", baseStem("ager"))
                    .homograph(2)
                    .build());
            Lexicon grown = BASE.add(second);
            assertThat(grown.lexemesByLemma("ager").stream().map(Lexeme::homograph))
                    .containsExactly(1, 2);
        }

        @Test
        void isCaseInsensitiveOnTheLemma() {
            assertThat(BASE.lexemesByLemma("PUELLA")).hasSize(1);
        }

        @Test
        void givesAnEmptyListForALemmaNobodyHas() {
            assertThat(BASE.lexemesByLemma("vita")).isEmpty();
        }

        @Test
        void indexesByClassAndByTag() {
            assertThat(lemmasOf(BASE.lexemesByClass("first-declension"))).containsExactly("puella");
            assertThat(lemmasOf(BASE.lexemesByTag("core"))).containsExactly("puella");
            assertThat(BASE.lexemesByTag("rare")).isEmpty();
            assertThat(BASE.tags()).containsExactly("chapter-one", "core");
        }
    }

    @Nested
    class NextHomograph {

        @Test
        void startsAtOneForALemmaNobodyHas() {
            assertThat(BASE.nextHomograph("vita")).isEqualTo(1);
        }

        @Test
        void stepsPastTheHighestOneAlreadyThere() {
            assertThat(BASE.nextHomograph("ager")).isEqualTo(2);
        }
    }

    @Nested
    class LexemesUnderClass {

        @Test
        void includesTheClassItself() {
            assertThat(lemmasOf(BASE.lexemesUnderClass(Classes.LATIN, "first-declension")))
                    .containsExactly("puella");
        }

        @Test
        void includesEveryDescendantClass() {
            assertThat(lemmasOf(BASE.lexemesUnderClass(Classes.LATIN, "second-declension")))
                    .containsExactly("ager", "bellum");
        }

        @Test
        void isEmptyForAClassNothingUses() {
            assertThat(BASE.lexemesUnderClass(Classes.LATIN, "first-conjugation"))
                    .isEmpty();
        }
    }

    @Nested
    class AddReplaceAndRemove {

        @Test
        void addsAndLeavesTheOriginalAlone() {
            Lexicon grown = BASE.add(NAUTA);
            assertThat(grown.size()).isEqualTo(4);
            assertThat(BASE.size()).isEqualTo(3);
        }

        @Test
        void refusesToAddAnIdThatIsAlreadyThere() {
            assertThatThrownBy(() -> BASE.add(PUELLA)).isInstanceOf(ConflictException.class);
        }

        @Test
        void replacesAnEntryInPlace() {
            Lexeme changed = makeLexeme(LexemeInput.builder("puella", "first-declension", baseStem("puell"))
                    .inherent(Map.of("gender", "fem"))
                    .glosses("girl", "maiden")
                    .tags("core")
                    .build());
            Lexicon updated = BASE.replace(changed);
            assertThat(updated.size()).isEqualTo(3);
            assertThat(updated.requireLexeme(PUELLA.id()).glosses()).containsExactly("girl", "maiden");
            assertThat(updated.lexemesByTag("chapter-one")).isEmpty();
        }

        @Test
        void refusesToReplaceSomethingThatIsNotThere() {
            assertThatThrownBy(() -> BASE.replace(NAUTA)).isInstanceOf(NotFoundException.class);
        }

        @Test
        void removesByIdAndRefusesAnIdItDoesNotHold() {
            assertThat(BASE.remove(BELLUM.id()).size()).isEqualTo(2);
            assertThatThrownBy(() -> BASE.remove(NAUTA.id())).isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class Stats {

        @Test
        void countsByPartOfSpeechThroughTheClassChain() {
            LexiconStats stats = BASE.stats(Classes.LATIN);
            assertThat(stats.total()).isEqualTo(3);
            assertThat(stats.byPartOfSpeech().get("noun")).isEqualTo(3);
            assertThat(stats.classesUsed()).isEqualTo(3);
        }

        @Test
        void countsEntriesWithNoTagsAndNoGlosses() {
            Lexeme bare = makeLexeme(LexemeInput.of("res", "third-declension", baseStem("r")));
            LexiconStats stats = BASE.add(bare).stats(Classes.LATIN);
            assertThat(stats.untagged()).isEqualTo(3);
            assertThat(stats.withoutGloss()).isEqualTo(1);
        }

        @Test
        void reportsZerosForAnEmptyLexicon() {
            LexiconStats stats = Lexicon.empty("latin").stats(Classes.LATIN);
            assertThat(stats.total()).isZero();
            assertThat(stats.byPartOfSpeech()).isEmpty();
        }
    }
}
