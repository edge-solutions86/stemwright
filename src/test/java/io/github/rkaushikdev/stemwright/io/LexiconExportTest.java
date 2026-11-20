package io.github.rkaushikdev.stemwright.io;

import static io.github.rkaushikdev.stemwright.support.Lexemes.AGER;
import static io.github.rkaushikdev.stemwright.support.Lexemes.BELLUM;
import static io.github.rkaushikdev.stemwright.support.Lexemes.LATIN_CONTEXT;
import static io.github.rkaushikdev.stemwright.support.Lexemes.PUELLA;
import static io.github.rkaushikdev.stemwright.support.Lexemes.makeLexeme;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.domain.features.FeaturePair;
import io.github.rkaushikdev.stemwright.domain.lexicon.Lexeme;
import io.github.rkaushikdev.stemwright.domain.lexicon.LexemeInput;
import io.github.rkaushikdev.stemwright.domain.lexicon.Stem;
import io.github.rkaushikdev.stemwright.domain.lexicon.StemInput;
import io.github.rkaushikdev.stemwright.domain.lexicon.StemSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LexiconExportTest {

    private static final List<Lexeme> ENTRIES = List.of(PUELLA, BELLUM, AGER);

    private static final Lexeme WITH_STEMS = makeLexeme(LexemeInput.builder(
                    "fero",
                    "third-conjugation",
                    StemSet.of(StemInput.of("base", "fer"), StemInput.suppletive("perfect", "tul")))
            .glosses("carry")
            .build());

    private static List<Lexeme> plusWithStems() {
        return List.of(PUELLA, BELLUM, AGER, WITH_STEMS);
    }

    private static Lexeme accepted(ImportReport report, String lemma) {
        return report.accepted().stream()
                .filter(entry -> entry.lemma().equals(lemma))
                .findFirst()
                .orElseThrow();
    }

    @Nested
    class ColumnsFor {

        @Test
        void alwaysStartsWithTheBaseColumns() {
            assertThat(LexiconExport.columnsFor(ENTRIES).subList(0, LexiconExport.BASE_COLUMNS.size()))
                    .isEqualTo(LexiconExport.BASE_COLUMNS);
        }

        @Test
        void addsAColumnForEveryExtraStemRoleInUse() {
            assertThat(LexiconExport.stemColumnsFor(List.of(WITH_STEMS))).containsExactly("stem.perfect");
            assertThat(LexiconExport.stemColumnsFor(ENTRIES)).isEmpty();
        }

        @Test
        void addsAColumnForEveryInherentDimensionInUse() {
            assertThat(LexiconExport.inherentColumnsFor(ENTRIES)).containsExactly("inherent.gender");
            assertThat(LexiconExport.inherentColumnsFor(List.of(WITH_STEMS))).isEmpty();
        }

        @Test
        void sortsTheExtraColumnsSoTwoExportsOfOneLexiconMatch() {
            List<String> columns = LexiconExport.columnsFor(plusWithStems());
            assertThat(columns).contains("stem.perfect");
            assertThat(columns).contains("inherent.gender");
        }
    }

    @Nested
    class RowFor {

        @Test
        void writesTheBaseFields() {
            Map<String, String> row = LexiconExport.rowFor(PUELLA, LexiconExport.columnsFor(ENTRIES));
            assertThat(row.get("lemma")).isEqualTo("puella");
            assertThat(row.get("class")).isEqualTo("first-declension");
            assertThat(row.get("stem")).isEqualTo("puell");
            assertThat(row.get("homograph")).isEqualTo("1");
        }

        @Test
        void joinsGlossesAndTagsWithSemicolons() {
            List<String> columns = LexiconExport.columnsFor(ENTRIES);
            assertThat(LexiconExport.rowFor(BELLUM, columns).get("gloss")).isEqualTo("war; campaign");
            assertThat(LexiconExport.rowFor(PUELLA, columns).get("tags")).isEqualTo("core; chapter-one");
        }

        @Test
        void writesTheInherentValueIntoItsColumn() {
            assertThat(LexiconExport.rowFor(PUELLA, LexiconExport.columnsFor(ENTRIES))
                            .get("inherent.gender"))
                    .isEqualTo("fem");
        }

        @Test
        void writesAnEmptyCellForARoleOrDimensionThisLexemeLacks() {
            List<String> columns = LexiconExport.columnsFor(plusWithStems());
            assertThat(LexiconExport.rowFor(PUELLA, columns).get("stem.perfect"))
                    .isEmpty();
            assertThat(LexiconExport.rowFor(WITH_STEMS, columns).get("inherent.gender"))
                    .isEmpty();
        }
    }

    @Nested
    class ExportLexemes {

        @Test
        void writesAHeaderAndOneRowPerLexeme() {
            CsvTable table = Csv.readTable(LexiconExport.exportLexemes(ENTRIES));
            assertThat(table.rows()).hasSize(3);
            assertThat(table.headers()).contains("lemma");
        }

        @Test
        void sortsTheRowsByLemma() {
            CsvTable table = Csv.readTable(LexiconExport.exportLexemes(ENTRIES));
            assertThat(table.rows()).extracting(row -> row.get("lemma")).containsExactly("ager", "bellum", "puella");
        }

        @Test
        void writesAnEmptyFileWithOnlyAHeaderForNoLexemes() {
            assertThat(Csv.readTable(LexiconExport.exportLexemes(List.of())).rows())
                    .isEmpty();
        }

        @Test
        void roundTripsThroughTheImporter() {
            ImportReport report = LexiconImport.importCsv(LATIN_CONTEXT, LexiconExport.exportLexemes(ENTRIES));
            assertThat(report.rejected()).isEmpty();
            assertThat(report.accepted().stream().map(Lexeme::id).sorted().toList())
                    .isEqualTo(ENTRIES.stream().map(Lexeme::id).sorted().toList());
        }

        @Test
        void roundTripsTheExtraStemsAndTheInherentFeatures() {
            ImportReport report =
                    LexiconImport.importCsv(LATIN_CONTEXT, LexiconExport.exportLexemes(List.of(PUELLA, WITH_STEMS)));
            Lexeme carried = accepted(report, "fero");
            assertThat(carried.stems().stems()).extracting(Stem::role).containsExactly("base", "perfect");
            Lexeme girl = accepted(report, "puella");
            assertThat(girl.inherent().pairs()).containsExactly(new FeaturePair("gender", "fem"));
        }

        @Test
        void roundTripsAHomographIndex() {
            Lexeme second = makeLexeme(
                    LexemeInput.builder("malus", "second-declension", StemSet.of(StemInput.of("base", "mal")))
                            .homograph(2)
                            .build());
            ImportReport report = LexiconImport.importCsv(LATIN_CONTEXT, LexiconExport.exportLexemes(List.of(second)));
            assertThat(report.accepted()).first().extracting(Lexeme::homograph).isEqualTo(2);
        }
    }

    @Nested
    class DescribeLexeme {

        @Test
        void namesTheClassAndTheInherentFeatures() {
            assertThat(LexiconExport.describeLexeme(PUELLA)).isEqualTo("puella first-declension (gender=fem): girl");
        }

        @Test
        void leavesTheBracketsOutWhenThereAreNone() {
            assertThat(LexiconExport.describeLexeme(WITH_STEMS)).isEqualTo("fero third-conjugation: carry");
        }
    }
}
