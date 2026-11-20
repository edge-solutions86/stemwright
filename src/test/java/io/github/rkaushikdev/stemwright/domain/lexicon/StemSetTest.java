package io.github.rkaushikdev.stemwright.domain.lexicon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.support.Problems;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StemSetTest {

    private static final StemSet GREEK_VERB = StemSet.of(
            StemInput.of("base", "leip"),
            StemInput.of("future", "leips"),
            StemInput.of("aorist", "lip").withNote("the second aorist takes the zero grade"),
            StemInput.of("perfect", "leloip"));

    @Nested
    class StemBasics {

        @Test
        void keepsTheRoleAndTheForm() {
            Stem built = Stem.of(StemInput.of("aorist", "lip"));
            assertThat(built).isEqualTo(new Stem("aorist", "lip", false, Optional.empty()));
        }

        @Test
        void lowerCasesTheRoleAndTrimsTheForm() {
            Stem built = Stem.of(StemInput.of("Aorist", "  lip  "));
            assertThat(built.role()).isEqualTo("aorist");
            assertThat(built.form()).isEqualTo("lip");
        }

        @Test
        void refusesABlankForm() {
            assertThatThrownBy(() -> Stem.of(StemInput.of("base", "   "))).isInstanceOf(ValidationException.class);
        }

        @Test
        void refusesARoleThatIsNotASlug() {
            assertThatThrownBy(() -> Stem.of(StemInput.of("second aorist", "lip")))
                    .hasMessageContaining("not a slug");
        }
    }

    @Nested
    class Of {

        @Test
        void holdsTheStemsInTheOrderTheyWereGiven() {
            assertThat(GREEK_VERB.roles()).containsExactly("base", "future", "aorist", "perfect");
        }

        @Test
        void refusesAnEmptySet() {
            assertThat(Problems.problemMessages(() -> StemSet.of(List.of())))
                    .containsExactly("stems must hold at least one stem");
        }

        @Test
        void refusesARepeatedRole() {
            assertThat(Problems.problemMessages(() -> StemSet.of(StemInput.of("base", "a"), StemInput.of("base", "b"))))
                    .containsExactly("stems.base is declared twice");
        }

        @Test
        void buildsAOneStemSetFromABareForm() {
            assertThat(StemSet.single("mens").roles()).containsExactly(Stem.DEFAULT_ROLE);
        }
    }

    @Nested
    class FindAndResolve {

        @Test
        void findsARoleThatIsThere() {
            assertThat(GREEK_VERB.find("perfect").map(Stem::form)).contains("leloip");
            assertThat(GREEK_VERB.find("pluperfect")).isEmpty();
        }

        @Test
        void fallsBackToTheBaseStemForARoleThatIsMissing() {
            assertThat(GREEK_VERB.resolve("pluperfect").role()).isEqualTo("base");
        }

        @Test
        void prefersTheExactRoleOverTheFallback() {
            assertThat(GREEK_VERB.resolve("aorist").form()).isEqualTo("lip");
        }

        @Test
        void refusesWhenThereIsNeitherTheRoleNorABase() {
            StemSet noBase = StemSet.of(StemInput.of("future", "leips"));
            assertThatThrownBy(() -> noBase.resolve("aorist"))
                    .hasMessageContaining("neither a aorist stem nor a base stem");
        }
    }

    @Nested
    class Suppletion {

        private final StemSet latinFero = StemSet.of(
                StemInput.of("base", "fer"),
                StemInput.suppletive("perfect", "tul"),
                StemInput.suppletive("supine", "lat"));

        @Test
        void reportsWhetherAnyStemIsMarkedSuppletive() {
            assertThat(latinFero.hasSuppletion()).isTrue();
            assertThat(GREEK_VERB.hasSuppletion()).isFalse();
        }

        @Test
        void namesTheSuppletiveRoles() {
            assertThat(latinFero.suppletiveRoles()).containsExactly("perfect", "supine");
        }

        @Test
        void findsNoneToBeSuspectWhenTheFormsReallyDoDiffer() {
            assertThat(latinFero.suspectSuppletion()).isEmpty();
        }

        @Test
        void flagsAStemMarkedSuppletiveThatSharesAnOpeningWithTheBase() {
            StemSet suspect = StemSet.of(StemInput.of("base", "leip"), StemInput.suppletive("aorist", "leipa"));
            assertThat(suspect.suspectSuppletion()).containsExactly("aorist");
        }

        @Test
        void flagsNothingWhenThereIsNoBaseStemToCompareAgainst() {
            StemSet noBase = StemSet.of(StemInput.suppletive("perfect", "tul"));
            assertThat(noBase.suspectSuppletion()).isEmpty();
        }
    }

    @Nested
    class LooksRelated {

        @Test
        void isTrueWhenTheOpeningIsShared() {
            assertThat(StemSet.looksRelated("leip", "leloip")).isFalse();
            assertThat(StemSet.looksRelated("leip", "leips")).isTrue();
        }

        @Test
        void isFalseWhenNothingIsShared() {
            assertThat(StemSet.looksRelated("fer", "tul")).isFalse();
        }

        @Test
        void takesTheShorterStringAsTheLimit() {
            assertThat(StemSet.looksRelated("ab", "abcdef")).isTrue();
            assertThat(StemSet.looksRelated("ax", "abcdef")).isFalse();
        }

        @Test
        void isFalseWhenEitherSideIsBlank() {
            assertThat(StemSet.looksRelated("", "abc")).isFalse();
        }
    }

    @Nested
    class WithAndWithout {

        @Test
        void addsARoleThatWasNotThere() {
            assertThat(GREEK_VERB.with(StemInput.of("passive", "leiphth")).roles())
                    .contains("passive");
        }

        @Test
        void replacesARoleThatWas() {
            StemSet changed = GREEK_VERB.with(StemInput.of("aorist", "elip"));
            assertThat(changed.find("aorist").map(Stem::form)).contains("elip");
            assertThat(changed.stems()).hasSize(4);
        }

        @Test
        void leavesTheOriginalAlone() {
            GREEK_VERB.with(StemInput.of("aorist", "elip"));
            assertThat(GREEK_VERB.find("aorist").map(Stem::form)).contains("lip");
        }

        @Test
        void dropsARoleAndRefusesToEmptyTheSet() {
            assertThat(GREEK_VERB.without("perfect").roles()).doesNotContain("perfect");
            assertThatThrownBy(() -> StemSet.single("mens").without("base")).hasMessageContaining("no stems at all");
        }
    }
}
