package io.github.rkaushikdev.stemwright.io;

import static io.github.rkaushikdev.stemwright.support.Lexemes.LATIN_CONTEXT;
import static io.github.rkaushikdev.stemwright.support.Lexemes.PUELLA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.features.FeaturePair;
import io.github.rkaushikdev.stemwright.domain.lexicon.Lexeme;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LexiconImportTest {

    private static final String HEADER = "lemma,class,stem,gloss,tags";
    private static final String GOOD = HEADER
            + "\npuella,first-declension,puell,girl;maiden,core\nbellum,second-declension.neuter,bell,war,core\n";

    private static ImportReport importCsv(String text) {
        return LexiconImport.importCsv(LATIN_CONTEXT, text);
    }

    private static Lexeme firstAccepted(String text) {
        return importCsv(text).accepted().get(0);
    }

    @Nested
    class ImportCsv {

        @Test
        void acceptsEveryGoodRow() {
            ImportReport report = importCsv(GOOD);
            assertThat(report.total()).isEqualTo(2);
            assertThat(report.accepted()).hasSize(2);
            assertThat(report.rejected()).isEmpty();
            assertThat(report.isClean()).isTrue();
        }

        @Test
        void splitsGlossesAndTagsOnSemicolons() {
            Lexeme entry = firstAccepted(GOOD);
            assertThat(entry.glosses()).containsExactly("girl", "maiden");
            assertThat(entry.tags()).containsExactly("core");
        }

        @Test
        void readsExtraStemColumns() {
            String text = HEADER + ",stem.oblique\nrex,third-declension,reg,king,core,regi\n";
            assertThat(firstAccepted(text).stems().roles()).containsExactly("base", "oblique");
        }

        @Test
        void ignoresAnEmptyExtraStemColumn() {
            String text = HEADER + ",stem.oblique\nrex,third-declension,reg,king,core,\n";
            assertThat(firstAccepted(text).stems().roles()).containsExactly("base");
        }

        @Test
        void readsInherentColumns() {
            String text = HEADER + ",inherent.gender\npuella,first-declension,puell,girl,core,fem\n";
            assertThat(firstAccepted(text).inherent().pairs()).containsExactly(new FeaturePair("gender", "fem"));
        }

        @Test
        void readsAHomographIndex() {
            String text = HEADER + ",homograph\nmalus,second-declension,mal,apple tree,core,2\n";
            assertThat(firstAccepted(text).homograph()).isEqualTo(2);
        }

        @Test
        void readsAHeaderWithNoRows() {
            ImportReport report = importCsv(HEADER + "\n");
            assertThat(report.total()).isZero();
            assertThat(report.isClean()).isTrue();
        }

        @Test
        void refusesAFileMissingARequiredColumn() {
            assertThatThrownBy(() -> importCsv("lemma,gloss\npuella,girl\n"))
                    .hasMessageContaining("missing the columns class, stem");
        }
    }

    @Nested
    class RejectedRows {

        private static final String MIXED = HEADER
                + "\npuella,first-declension,puell,girl,core\nnauta,fourth-declension,naut,sailor,core\n"
                + ",first-declension,x,nothing,core\n";

        @Test
        void keepsGoingPastABadRow() {
            ImportReport report = importCsv(MIXED);
            assertThat(report.total()).isEqualTo(3);
            assertThat(report.accepted()).hasSize(1);
            assertThat(report.rejected()).hasSize(2);
        }

        @Test
        void namesTheLineOfEachRejection() {
            assertThat(importCsv(MIXED).rejected()).extracting(ImportRow::line).containsExactly(3, 4);
        }

        @Test
        void carriesTheProblemsOfEachRejection() {
            ImportRow rejected = importCsv(MIXED).rejected().get(0);
            assertThat(rejected.lemma()).isEqualTo("nauta");
            assertThat(rejected.problems().get(0).message()).contains("is not a class of latin");
        }

        @Test
        void recordsARowWithNoLemmaAtAll() {
            ImportRow rejected = importCsv(MIXED).rejected().get(1);
            assertThat(rejected.lemma()).isEmpty();
            assertThat(rejected.problems()).hasSize(1);
        }
    }

    @Nested
    class RequireClean {

        @Test
        void givesTheAcceptedRowsWhenNothingWasRejected() {
            assertThat(importCsv(GOOD).requireClean()).hasSize(2);
        }

        @Test
        void refusesWhenAnythingWasRejectedAndNamesTheLines() {
            String mixed = HEADER + "\nnauta,fourth-declension,naut,sailor,core\n";
            assertThatThrownBy(() -> importCsv(mixed).requireClean()).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> importCsv(mixed).requireClean()).hasMessageContaining("1 of 1 rows were rejected");
        }
    }

    @Nested
    class DescribeImport {

        @Test
        void countsWhatIsUsable() {
            assertThat(importCsv(GOOD).describe().get(0)).isEqualTo("2 of 2 rows are usable");
        }

        @Test
        void readsEachRejectionOutWithItsLine() {
            String mixed = HEADER + "\nnauta,fourth-declension,naut,sailor,core\n";
            List<String> lines = importCsv(mixed).describe();
            assertThat(lines.get(1)).contains("line 2 nauta:");
        }

        @Test
        void marksARowWithNoLemma() {
            String mixed = HEADER + "\n,first-declension,x,nothing,core\n";
            assertThat(importCsv(mixed).describe().get(1)).contains("(no lemma)");
        }
    }

    @Nested
    class CollisionsWith {

        @Test
        void namesARowThatWouldCollideWithSomethingStored() {
            assertThat(importCsv(GOOD).collisionsWith(List.of(PUELLA))).containsExactly("puella");
        }

        @Test
        void namesNothingWhenTheLexiconIsEmpty() {
            assertThat(importCsv(GOOD).collisionsWith(List.of())).isEmpty();
        }

        @Test
        void ignoresCaseWhenComparingLemmas() {
            String text = HEADER + "\nPUELLA,first-declension,puell,girl,core\n";
            assertThat(importCsv(text).collisionsWith(List.of(PUELLA))).containsExactly("PUELLA");
        }
    }
}
