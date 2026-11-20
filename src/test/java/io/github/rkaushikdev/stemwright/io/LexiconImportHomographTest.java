package io.github.rkaushikdev.stemwright.io;

import static io.github.rkaushikdev.stemwright.support.Lexemes.LATIN_CONTEXT;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.domain.errors.ErrorDetail;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The homograph cell is read leniently as a number, and a lexeme's homograph is an {@code int}. A
 * cell an {@code int} cannot carry is reported in the lexeme's own words for a bad homograph, among
 * the row's other problems and in their order.
 */
class LexiconImportHomographTest {

    private static final String HEADER = "lemma,class,stem,gloss,tags,homograph\n";

    private static List<ErrorDetail> problemsOf(String row) {
        ImportReport report = LexiconImport.importCsv(LATIN_CONTEXT, HEADER + row + "\n");
        assertThat(report.accepted()).isEmpty();
        return report.rejected().get(0).problems();
    }

    @Test
    void refusesAFractionWithTheValueInItsShortestForm() {
        assertThat(problemsOf("malus,second-declension,mal,apple tree,core,2.50"))
                .containsExactly(new ErrorDetail("homograph", "must be a positive whole number: 2.5"));
    }

    @Test
    void refusesACellThatIsNotANumberAsNaNAndAnInfinityByName() {
        assertThat(problemsOf("malus,second-declension,mal,apple tree,core,two"))
                .containsExactly(new ErrorDetail("homograph", "must be a positive whole number: NaN"));
        assertThat(problemsOf("malus,second-declension,mal,apple tree,core,-Infinity"))
                .containsExactly(new ErrorDetail("homograph", "must be a positive whole number: -Infinity"));
    }

    @Test
    void refusesZeroAndANegativeIndexAsTheLexemeDoes() {
        assertThat(problemsOf("malus,second-declension,mal,apple tree,core,  "))
                .containsExactly(new ErrorDetail("homograph", "must be a positive whole number: 0"));
        assertThat(problemsOf("malus,second-declension,mal,apple tree,core,-3"))
                .containsExactly(new ErrorDetail("homograph", "must be a positive whole number: -3"));
    }

    @Test
    void reportsTheHomographInItsPlaceAmongTheOtherProblems() {
        assertThat(problemsOf("malus,fourth-declension,mal,apple tree,core,1.5"))
                .containsExactly(
                        new ErrorDetail("classKey", "is not a class of latin: fourth-declension"),
                        new ErrorDetail("homograph", "must be a positive whole number: 1.5"));
    }

    @Test
    void readsTheProblemOutInTheImportSummary() {
        String text = HEADER + "malus,second-declension,mal,apple tree,core,1e-7\n";
        assertThat(LexiconImport.importCsv(LATIN_CONTEXT, text).describe())
                .containsExactly(
                        "0 of 1 rows are usable", "line 2 malus: homograph must be a positive whole number: 1e-7");
    }

    @Test
    void refusesAnIndexNoIntCanHoldAndAcceptsTheLargestThatFits() {
        assertThat(problemsOf("malus,second-declension,mal,apple tree,core,3e9"))
                .containsExactly(new ErrorDetail("homograph", "must be at most 2147483647: 3000000000"));
        String largest = HEADER + "malus,second-declension,mal,apple tree,core,2147483647\n";
        assertThat(LexiconImport.importCsv(LATIN_CONTEXT, largest)
                        .accepted()
                        .get(0)
                        .homograph())
                .isEqualTo(Integer.MAX_VALUE);
    }
}
