package io.github.rkaushikdev.stemwright.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.domain.text.Text;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RenderTest {

    @Nested
    class PadTo {

        @Test
        void padsToTheRightByDefault() {
            assertThat(Render.padTo("ab", 5)).isEqualTo("ab   ");
        }

        @Test
        void padsToTheLeftWhenAsked() {
            assertThat(Render.padTo("ab", 5, Align.RIGHT)).isEqualTo("   ab");
        }

        @Test
        void leavesAValueAlreadyAtOrOverTheWidthAlone() {
            assertThat(Render.padTo("abcdef", 3)).isEqualTo("abcdef");
        }

        @Test
        void countsCodePointsRatherThanUtf16Units() {
            String astral = Character.toString(0x1d7d9);
            assertThat(Render.padTo(astral, 3)).isEqualTo(astral + "  ");
        }
    }

    @Nested
    class ColumnWidths {

        @Test
        void takesTheWidestCellOfEachColumn() {
            assertThat(Render.columnWidths(List.of(List.of("a", "bbb"), List.of("cc", "d"))))
                    .containsExactly(2, 3);
        }

        @Test
        void toleratesRaggedRows() {
            assertThat(Render.columnWidths(List.of(List.of("a"), List.of("bb", "ccc"))))
                    .containsExactly(2, 3);
        }

        @Test
        void isEmptyForNoRows() {
            assertThat(Render.columnWidths(List.of())).isEmpty();
        }
    }

    @Nested
    class Table {

        private final List<Column> columns = List.of(Column.of("cell"), Column.of("due", Align.RIGHT));

        @Test
        void putsARuleUnderTheHeader() {
            List<String> lines = Text.split(Render.table(columns, List.of(List.of("gen.sg", "3"))), "\n");
            assertThat(lines.get(0)).isEqualTo("cell    due");
            assertThat(lines.get(1)).isEqualTo("------  ---");
            assertThat(lines.get(2)).isEqualTo("gen.sg    3");
        }

        @Test
        void padsAShortRowOutRatherThanRefusingIt() {
            List<String> lines = Text.split(Render.table(columns, List.of(List.of("gen.sg"))), "\n");
            assertThat(lines.get(2)).isEqualTo("gen.sg");
        }

        @Test
        void printsJustTheHeaderAndRuleForNoRows() {
            assertThat(Text.split(Render.table(columns, List.of()), "\n")).hasSize(2);
        }

        @Test
        void neverLeavesTrailingSpacesOnALine() {
            List<String> lines = Text.split(Render.table(columns, List.of(List.of("a", "b"))), "\n");
            assertThat(lines).allSatisfy(line -> assertThat(line).isEqualTo(line.stripTrailing()));
        }
    }

    @Nested
    class KeyValues {

        @Test
        void alignsTheValues() {
            String rendered = Render.keyValues(List.of(Map.entry("deck", "all"), Map.entry("learner", "lrn_one")));
            assertThat(rendered).isEqualTo("deck     all\nlearner  lrn_one");
        }

        @Test
        void printsNothingForNoPairs() {
            assertThat(Render.keyValues(List.of())).isEmpty();
        }
    }

    @Nested
    class Bar {

        @Test
        void fillsInProportion() {
            assertThat(Render.bar(0, 4)).isEqualTo("....");
            assertThat(Render.bar(0.5, 4)).isEqualTo("##..");
            assertThat(Render.bar(1, 4)).isEqualTo("####");
        }

        @Test
        void clampsOutOfRangeShares() {
            assertThat(Render.bar(-1, 4)).isEqualTo("....");
            assertThat(Render.bar(9, 4)).isEqualTo("####");
            assertThat(Render.bar(Double.NaN, 4)).isEqualTo("....");
        }

        @Test
        void takesTheCharactersItIsGiven() {
            assertThat(Render.bar(0.5, 4, "=", " ")).isEqualTo("==  ");
        }
    }

    @Nested
    class Percent {

        @Test
        void printsAShareAsAPercentage() {
            assertThat(Render.percent(0.5)).isEqualTo("50%");
            assertThat(Render.percent(0.333, 1)).isEqualTo("33.3%");
        }

        @Test
        void printsNothingSensibleAsZero() {
            assertThat(Render.percent(Double.NaN)).isEqualTo("0%");
        }
    }

    @Nested
    class Pluralise {

        @Test
        void usesTheSingularForOne() {
            assertThat(Render.pluralise(1, "cell")).isEqualTo("1 cell");
            assertThat(Render.pluralise(2, "cell")).isEqualTo("2 cells");
            assertThat(Render.pluralise(0, "cell")).isEqualTo("0 cells");
        }

        @Test
        void takesAnIrregularPlural() {
            assertThat(Render.pluralise(2, "lexis", "lexeis")).isEqualTo("2 lexeis");
        }
    }

    @Nested
    class IndentAndSection {

        @Test
        void indentsEveryNonEmptyLine() {
            assertThat(Render.indent("a\n\nb")).isEqualTo("  a\n\n  b");
        }

        @Test
        void saysSoWhenASectionHasNothingInIt() {
            assertThat(Render.section("Weakest", "")).isEqualTo("Weakest\n  nothing to show");
        }

        @Test
        void indentsTheBodyOfASection() {
            assertThat(Render.section("Weakest", "gen.sg")).isEqualTo("Weakest\n  gen.sg");
        }
    }

    @Nested
    class Truncate {

        @Test
        void leavesAShortValueAlone() {
            assertThat(Render.truncate("abc", 5)).isEqualTo("abc");
        }

        @Test
        void marksAValueItCut() {
            assertThat(Render.truncate("abcdef", 4)).isEqualTo("abc~");
        }

        @Test
        void handlesAWidthOfOneOrZero() {
            assertThat(Render.truncate("abcdef", 1)).isEqualTo("a");
            assertThat(Render.truncate("abcdef", 0)).isEmpty();
        }
    }
}
