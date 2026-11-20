package io.github.rkaushikdev.stemwright.domain.paradigm;

import static io.github.rkaushikdev.stemwright.support.Profiles.FIRST_DECLENSION;
import static io.github.rkaushikdev.stemwright.support.Profiles.LATIN_NOUN;
import static io.github.rkaushikdev.stemwright.support.Profiles.SECOND_NEUTER;
import static io.github.rkaushikdev.stemwright.support.TestMaps.ordered;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.IdKind;
import io.github.rkaushikdev.stemwright.domain.Identifiers;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.features.FeatureBundle;
import io.github.rkaushikdev.stemwright.domain.features.FeatureSchema;
import io.github.rkaushikdev.stemwright.domain.features.FeatureSchemaInput;
import io.github.rkaushikdev.stemwright.domain.features.FeatureSchemaInput.Dimension;
import io.github.rkaushikdev.stemwright.domain.lexicon.Lexeme;
import io.github.rkaushikdev.stemwright.domain.lexicon.LexemeContext;
import io.github.rkaushikdev.stemwright.domain.lexicon.LexemeInput;
import io.github.rkaushikdev.stemwright.domain.lexicon.StemInput;
import io.github.rkaushikdev.stemwright.domain.lexicon.StemSet;
import io.github.rkaushikdev.stemwright.support.Classes;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CellTest {

    private static final FeatureSchema GENDERED = FeatureSchema.of(FeatureSchemaInput.of(
            "latin",
            Dimension.of("case", "nom", "gen", "dat", "acc", "abl"),
            Dimension.of("number", "sg", "pl"),
            Dimension.optional("gender", "masc", "fem", "neut")));

    private static final LexemeContext CONTEXT = new LexemeContext(GENDERED, Classes.LATIN);

    private static final Lexeme PUELLA = Lexeme.of(
            CONTEXT,
            LexemeInput.builder("puella", "first-declension", StemSet.of(StemInput.of("base", "puell")))
                    .inherent(Map.of("gender", "fem"))
                    .build());

    private static final Lexeme BELLUM = Lexeme.of(
            CONTEXT,
            LexemeInput.builder("bellum", "second-declension.neuter", StemSet.of(StemInput.of("base", "bell")))
                    .inherent(Map.of("gender", "neut"))
                    .build());

    private static final ParadigmProfile GENDERED_PROFILE = ParadigmProfile.of(
            GENDERED,
            ParadigmProfileInput.of(
                    "gendered",
                    "first-declension",
                    ParadigmSlotInput.of("nom.sg.fem", "a"),
                    ParadigmSlotInput.of("gen.sg.fem", "ae"),
                    ParadigmSlotInput.of("nom.sg.masc", "us"),
                    ParadigmSlotInput.of("gen.sg.masc", "i")));

    @Nested
    class IdFor {

        @Test
        void isStableAndWellFormed() {
            String id = Cell.idFor(PUELLA.id(), "case=gen;number=sg");
            assertThat(id).isEqualTo(Cell.idFor(PUELLA.id(), "case=gen;number=sg"));
            assertThat(Identifiers.isId(IdKind.CELL, id)).isTrue();
        }

        @Test
        void separatesBundlesAndLexemes() {
            assertThat(Cell.idFor(PUELLA.id(), "case=gen;number=sg"))
                    .isNotEqualTo(Cell.idFor(PUELLA.id(), "case=dat;number=sg"));
            assertThat(Cell.idFor(PUELLA.id(), "case=gen;number=sg"))
                    .isNotEqualTo(Cell.idFor(BELLUM.id(), "case=gen;number=sg"));
        }
    }

    @Nested
    class Expand {

        @Test
        void makesOneCellPerSlotWhenNothingIsRuledOut() {
            assertThat(Cell.expand(LATIN_NOUN, PUELLA, FIRST_DECLENSION)).hasSize(10);
        }

        @Test
        void carriesTheProfileKeyOntoEveryCell() {
            List<Cell> cells = Cell.expand(LATIN_NOUN, BELLUM, SECOND_NEUTER);
            assertThat(cells.stream().map(Cell::profileKey).collect(toSet())).isEqualTo(Set.of("second-neuter"));
        }

        @Test
        void skipsASlotTheInherentGenderRulesOut() {
            List<Cell> cells = Cell.expand(GENDERED, PUELLA, GENDERED_PROFILE);
            assertThat(cells).hasSize(2);
            assertThat(cells)
                    .extracting(Cell::bundle)
                    .containsExactly("case=nom;number=sg;gender=fem", "case=gen;number=sg;gender=fem");
        }

        @Test
        void namesWhatItSkipped() {
            assertThat(Cell.skippedBundles(GENDERED, PUELLA, GENDERED_PROFILE))
                    .containsExactly("case=nom;number=sg;gender=masc", "case=gen;number=sg;gender=masc");
        }

        @Test
        void skipsNothingForALexemeWithNoInherentFeatures() {
            Lexeme bare = Lexeme.of(
                    CONTEXT, LexemeInput.of("res", "third-declension", StemSet.of(StemInput.of("base", "r"))));
            assertThat(Cell.skippedBundles(GENDERED, bare, GENDERED_PROFILE)).isEmpty();
            assertThat(Cell.expand(GENDERED, bare, GENDERED_PROFILE)).hasSize(4);
        }

        @Test
        void givesTheSameIdsOnASecondExpansion() {
            List<String> first = Cell.expand(LATIN_NOUN, PUELLA, FIRST_DECLENSION).stream()
                    .map(Cell::id)
                    .toList();
            List<String> second = Cell.expand(LATIN_NOUN, PUELLA, FIRST_DECLENSION).stream()
                    .map(Cell::id)
                    .toList();
            assertThat(second).isEqualTo(first);
        }
    }

    @Nested
    class CellReferences {

        @Test
        void printsALemmaAndADottedBundle() {
            FeatureBundle bundle = FeatureBundle.of(LATIN_NOUN, ordered("case", "gen", "number", "sg"));
            assertThat(CellRef.format(LATIN_NOUN, PUELLA, bundle)).isEqualTo("puella/gen.sg");
        }

        @Test
        void readsAPlainReferenceBack() {
            assertThat(CellRef.parse("puella/gen.sg")).isEqualTo(new CellRef("puella", 1, "gen.sg"));
        }

        @Test
        void readsAHomographIndex() {
            assertThat(CellRef.parse("malus(2)/nom.pl")).isEqualTo(new CellRef("malus", 2, "nom.pl"));
        }

        @Test
        void toleratesSpaceAroundTheHomographMarker() {
            assertThat(CellRef.parse("malus (2) / nom.pl").lemma()).isEqualTo("malus");
        }

        @Test
        void refusesAReferenceWithNoBundle() {
            assertThatThrownBy(() -> CellRef.parse("puella")).hasMessageContaining("lemma and a bundle");
            assertThatThrownBy(() -> CellRef.parse("puella/")).hasMessageContaining("lemma and a bundle");
            assertThatThrownBy(() -> CellRef.parse("/gen.sg")).hasMessageContaining("lemma and a bundle");
        }

        @Test
        void refusesAHomographIndexBelowOne() {
            assertThatThrownBy(() -> CellRef.parse("malus(0)/nom.pl"))
                    .hasMessageContaining("homograph index below one");
        }

        /** Java only: the original read any run of digits as a number; an int cannot hold every run. */
        @Test
        void refusesAHomographIndexTooLargeForAnyLexeme() {
            assertThat(CellRef.parse("malus(2147483647)/nom.pl").homograph()).isEqualTo(Integer.MAX_VALUE);
            assertThatThrownBy(() -> CellRef.parse("malus(2147483648)/nom.pl"))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("cell has a homograph index no lexeme can have: malus(2147483648)/nom.pl");
        }
    }

    @Nested
    class CellCollections {

        private final List<Cell> cells = Stream.concat(
                        Cell.expand(LATIN_NOUN, PUELLA, FIRST_DECLENSION).stream(),
                        Cell.expand(LATIN_NOUN, BELLUM, SECOND_NEUTER).stream())
                .toList();

        @Test
        void filtersByLexeme() {
            assertThat(Cell.cellsOfLexeme(cells, BELLUM.id())).hasSize(10);
        }

        @Test
        void filtersByBundle() {
            FeatureBundle bundle = FeatureBundle.of(LATIN_NOUN, ordered("case", "gen", "number", "pl"));
            assertThat(Cell.cellsInBundle(cells, bundle)).hasSize(2);
        }

        @Test
        void indexesById() {
            Map<String, Cell> index = Cell.index(cells);
            assertThat(index).hasSize(20);
            assertThat(index.get(cells.get(0).id())).isSameAs(cells.get(0));
        }

        @Test
        void comparesByLexemeThenBundle() {
            Cell a = Cell.of("lex_aaaaaa", "case=nom;number=sg", "p");
            Cell b = Cell.of("lex_aaaaaa", "case=nom;number=pl", "p");
            Cell c = Cell.of("lex_bbbbbb", "case=nom;number=sg", "p");
            assertThat(Cell.compare(a, c)).isLessThan(0);
            assertThat(Cell.compare(a, b)).isGreaterThan(0);
            assertThat(Cell.compare(a, a)).isZero();
        }

        @Test
        void callsTwoCellsTheSameWhenTheLexemeAndBundleMatch() {
            assertThat(Cell.of("lex_a", "b", "p1").sameAs(Cell.of("lex_a", "b", "p2")))
                    .isTrue();
            assertThat(Cell.of("lex_a", "b", "p1").sameAs(Cell.of("lex_a", "c", "p1")))
                    .isFalse();
        }
    }
}
