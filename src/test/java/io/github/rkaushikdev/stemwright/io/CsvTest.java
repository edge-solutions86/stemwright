package io.github.rkaushikdev.stemwright.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.support.TestMaps;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CsvTest {

    @Nested
    class FormatField {

        @Test
        void leavesAPlainValueAlone() {
            assertThat(Csv.formatField("puella")).isEqualTo("puella");
        }

        @Test
        void quotesAValueWithACommaAQuoteOrANewline() {
            assertThat(Csv.formatField("a,b")).isEqualTo("\"a,b\"");
            assertThat(Csv.formatField("a\nb")).isEqualTo("\"a\nb\"");
            assertThat(Csv.formatField("say \"hello\"")).isEqualTo("\"say \"\"hello\"\"\"");
        }

        @Test
        void leavesAnEmptyValueUnquoted() {
            assertThat(Csv.formatField("")).isEmpty();
        }
    }

    @Nested
    class FormatRowAndFormatCsv {

        @Test
        void joinsFieldsWithCommas() {
            assertThat(Csv.formatRow(List.of("a", "b,c"))).isEqualTo("a,\"b,c\"");
        }

        @Test
        void endsTheFileWithANewline() {
            assertThat(Csv.formatCsv(List.of(List.of("a"), List.of("b")))).isEqualTo("a\nb\n");
        }

        @Test
        void writesNothingForNoRows() {
            assertThat(Csv.formatCsv(List.of())).isEmpty();
        }
    }

    @Nested
    class ParseCsv {

        @Test
        void readsPlainRows() {
            assertThat(Csv.parseCsv("a,b\nc,d\n")).isEqualTo(List.of(List.of("a", "b"), List.of("c", "d")));
        }

        @Test
        void doesNotMakeARowOutOfTheTrailingNewline() {
            assertThat(Csv.parseCsv("a,b\n")).hasSize(1);
        }

        @Test
        void readsAFileWithNoTrailingNewline() {
            assertThat(Csv.parseCsv("a,b")).isEqualTo(List.of(List.of("a", "b")));
        }

        @Test
        void readsAQuotedFieldWithACommaInIt() {
            assertThat(Csv.parseCsv("\"a,b\",c")).isEqualTo(List.of(List.of("a,b", "c")));
        }

        @Test
        void readsADoubledQuoteAsOneQuote() {
            assertThat(Csv.parseCsv("\"say \"\"hello\"\"\",c")).isEqualTo(List.of(List.of("say \"hello\"", "c")));
        }

        @Test
        void readsANewlineInsideAQuotedField() {
            assertThat(Csv.parseCsv("\"a\nb\",c")).isEqualTo(List.of(List.of("a\nb", "c")));
        }

        @Test
        void normalisesCarriageReturns() {
            assertThat(Csv.parseCsv("a,b\r\nc,d\r\n")).isEqualTo(List.of(List.of("a", "b"), List.of("c", "d")));
        }

        @Test
        void keepsAnEmptyTrailingField() {
            assertThat(Csv.parseCsv("a,")).isEqualTo(List.of(List.of("a", "")));
        }

        @Test
        void keepsARowThatIsOnlyAnEmptyQuotedField() {
            assertThat(Csv.parseCsv("\"\"")).isEqualTo(List.of(List.of("")));
        }

        @Test
        void readsNothingFromAnEmptyFile() {
            assertThat(Csv.parseCsv("")).isEmpty();
        }

        @Test
        void refusesAFileThatEndsInsideAQuotedField() {
            assertThatThrownBy(() -> Csv.parseCsv("\"a,b")).hasMessageContaining("ends inside a quoted field");
        }

        @Test
        void refusesAQuoteThatOpensInTheMiddleOfAField() {
            assertThatThrownBy(() -> Csv.parseCsv("a\"b\"")).isInstanceOf(ValidationException.class);
        }

        @Test
        void roundTripsWhateverItFormatted() {
            List<List<String>> rows = List.of(
                    List.of("lemma", "gloss"),
                    List.of("puella", "girl, maiden"),
                    List.of("bellum", "war\nor campaign"),
                    List.of("malus", "say \"bad\""));
            assertThat(Csv.parseCsv(Csv.formatCsv(rows))).isEqualTo(rows);
        }
    }

    @Nested
    class ReadTable {

        private static final String TEXT = "lemma,gloss\npuella,girl\nbellum,war\n";

        @Test
        void readsNamedRecords() {
            CsvTable table = Csv.readTable(TEXT);
            assertThat(table.headers()).containsExactly("lemma", "gloss");
            assertThat(table.rows())
                    .isEqualTo(List.of(
                            TestMaps.ordered("lemma", "puella", "gloss", "girl"),
                            TestMaps.ordered("lemma", "bellum", "gloss", "war")));
        }

        @Test
        void readsAHeaderWithNoRowsUnderIt() {
            assertThat(Csv.readTable("lemma,gloss\n").rows()).isEmpty();
        }

        @Test
        void refusesAFileWithNoHeader() {
            assertThatThrownBy(() -> Csv.readTable("")).hasMessageContaining("has no header row");
        }

        @Test
        void refusesAHeaderNamingAColumnTwice() {
            assertThatThrownBy(() -> Csv.readTable("a,a\n1,2\n")).hasMessageContaining("names a column twice");
        }

        @Test
        void namesTheLineOfARowOfTheWrongWidth() {
            assertThatThrownBy(() -> Csv.readTable("lemma,gloss\npuella\n"))
                    .hasMessageContaining("line 2 has 1 fields where the header has 2");
        }
    }

    @Nested
    class WriteTable {

        @Test
        void writesTheColumnsInHeaderOrder() {
            String text = Csv.writeTable(
                    List.of("lemma", "gloss"), List.of(TestMaps.ordered("gloss", "girl", "lemma", "puella")));
            assertThat(text).isEqualTo("lemma,gloss\npuella,girl\n");
        }

        @Test
        void writesAnEmptyCellForAMissingColumn() {
            assertThat(Csv.writeTable(List.of("lemma", "gloss"), List.of(Map.of("lemma", "puella"))))
                    .isEqualTo("lemma,gloss\npuella,\n");
        }

        @Test
        void roundTripsThroughReadTable() {
            List<Map<String, String>> rows = List.of(TestMaps.ordered("lemma", "puella", "gloss", "girl, maiden"));
            assertThat(Csv.readTable(Csv.writeTable(List.of("lemma", "gloss"), rows))
                            .rows())
                    .isEqualTo(rows);
        }
    }

    @Nested
    class RequireColumns {

        @Test
        void passesWhenEveryColumnIsPresent() {
            assertThatCode(() -> Csv.readTable("a,b\n1,2\n").requireColumns(List.of("a")))
                    .doesNotThrowAnyException();
        }

        @Test
        void namesEveryMissingColumn() {
            assertThatThrownBy(() -> Csv.readTable("a,b\n1,2\n").requireColumns(List.of("c", "d")))
                    .hasMessageContaining("missing the columns c, d");
        }
    }
}
