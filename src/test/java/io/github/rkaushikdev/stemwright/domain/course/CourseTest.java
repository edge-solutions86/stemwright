package io.github.rkaushikdev.stemwright.domain.course;

import static io.github.rkaushikdev.stemwright.support.Problems.problemMessages;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.domain.IdKind;
import io.github.rkaushikdev.stemwright.domain.Identifiers;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import io.github.rkaushikdev.stemwright.domain.time.DayRange;
import java.time.LocalDate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CourseTest {

    private static final Course MICHAELMAS =
            Course.of(CourseInput.builder("latin-1a", "latin", d("2024-10-07"), d("2024-12-13"))
                    .title("Latin 1A")
                    .deckKeys("first-year", "core-vocabulary")
                    .instructorIds("lrn_teacher")
                    .note("first term, two contact hours a week")
                    .build());

    private static LocalDate d(String value) {
        return CivilDates.parseCivilDate(value);
    }

    /** A bare greek course with one instructor and nothing else. */
    private static Course greekTwo() {
        return Course.of(CourseInput.builder("greek-2", "greek", d("2025-01-06"), d("2025-03-14"))
                .instructorIds("lrn_teacher")
                .build());
    }

    @Nested
    class Of {

        @Test
        void keepsTheKeyTitleLanguageAndTerm() {
            assertThat(MICHAELMAS.key()).isEqualTo("latin-1a");
            assertThat(MICHAELMAS.title()).isEqualTo("Latin 1A");
            assertThat(MICHAELMAS.language()).isEqualTo("latin");
            assertThat(MICHAELMAS.term()).isEqualTo(new DayRange(d("2024-10-07"), d("2024-12-13")));
            assertThat(Identifiers.isId(IdKind.COURSE, MICHAELMAS.id())).isTrue();
        }

        @Test
        void derivesTheIdFromTheLanguageAndTheKey() {
            assertThat(MICHAELMAS.id()).isEqualTo(Course.idFor("latin", "latin-1a"));
        }

        @Test
        void defaultsTheTitleToTheKey() {
            Course bare = greekTwo();
            assertThat(bare.title()).isEqualTo("greek-2");
            assertThat(bare.deckKeys()).isEmpty();
        }

        @Test
        void refusesATermThatEndsBeforeItStarts() {
            assertThat(problemMessages(
                            () -> Course.of(CourseInput.builder("x", "latin", d("2024-12-13"), d("2024-10-07"))
                                    .instructorIds("lrn_teacher")
                                    .build())))
                    .containsExactly("term range starts after it ends: 2024-12-13 to 2024-10-07");
        }

        @Test
        void refusesACourseWithNoInstructor() {
            assertThat(problemMessages(
                            () -> Course.of(CourseInput.builder("x", "latin", d("2024-10-07"), d("2024-12-13"))
                                    .build())))
                    .containsExactly("instructorIds must name at least one instructor");
        }

        @Test
        void refusesARepeatedDeckOrInstructor() {
            assertThat(problemMessages(
                            () -> Course.of(CourseInput.builder("x", "latin", d("2024-10-07"), d("2024-12-13"))
                                    .deckKeys("a", "a")
                                    .instructorIds("lrn_teacher")
                                    .build())))
                    .containsExactly("deckKeys names the same deck twice");
            assertThat(problemMessages(
                            () -> Course.of(CourseInput.builder("x", "latin", d("2024-10-07"), d("2024-12-13"))
                                    .instructorIds("lrn_teacher", "lrn_teacher")
                                    .build())))
                    .containsExactly("instructorIds names the same instructor twice");
        }
    }

    @Nested
    class IsRunning {

        @Test
        void isTrueOnBothEndpoints() {
            assertThat(MICHAELMAS.isRunning(d("2024-10-07"))).isTrue();
            assertThat(MICHAELMAS.isRunning(d("2024-12-13"))).isTrue();
        }

        @Test
        void isFalseEitherSide() {
            assertThat(MICHAELMAS.isRunning(d("2024-10-06"))).isFalse();
            assertThat(MICHAELMAS.isRunning(d("2024-12-14"))).isFalse();
        }
    }

    @Nested
    class TermLength {

        @Test
        void countsBothEndsIn() {
            assertThat(MICHAELMAS.termLength()).isEqualTo(68);
        }
    }

    @Nested
    class TeachesAndIsInstructor {

        @Test
        void answersWhetherADeckBelongsToTheCourse() {
            assertThat(MICHAELMAS.teaches("first-year")).isTrue();
            assertThat(MICHAELMAS.teaches("third-year")).isFalse();
        }

        @Test
        void answersWhetherSomebodyTeachesIt() {
            assertThat(MICHAELMAS.isInstructor("lrn_teacher")).isTrue();
            assertThat(MICHAELMAS.isInstructor("lrn_student")).isFalse();
        }
    }

    @Nested
    class WithDeckAndWithoutDeck {

        @Test
        void addsADeckAndLeavesTheOriginalAlone() {
            Course grown = MICHAELMAS.withDeck("inscriptions");
            assertThat(grown.teaches("inscriptions")).isTrue();
            assertThat(MICHAELMAS.teaches("inscriptions")).isFalse();
        }

        @Test
        void refusesToAddADeckThatIsAlreadyThere() {
            assertThat(problemMessages(() -> MICHAELMAS.withDeck("first-year")))
                    .containsExactly("deckKeys names the same deck twice");
        }

        @Test
        void removesADeckAndToleratesOneThatWasNeverThere() {
            assertThat(MICHAELMAS.withoutDeck("first-year").deckKeys()).containsExactly("core-vocabulary");
            assertThat(MICHAELMAS.withoutDeck("nowhere").deckKeys()).hasSize(2);
        }

        @Test
        void keepsTheNoteThroughAnEdit() {
            assertThat(MICHAELMAS.withDeck("inscriptions").note()).contains("first term, two contact hours a week");
        }
    }

    @Nested
    class Describe {

        @Test
        void readsTheTermAndTheDeckCountOut() {
            assertThat(MICHAELMAS.describe()).isEqualTo("Latin 1A runs 2024-10-07 to 2024-12-13 with 2 decks");
        }

        @Test
        void saysSoWhenThereAreNoDecks() {
            assertThat(greekTwo().describe()).isEqualTo("greek-2 runs 2025-01-06 to 2025-03-14 with no decks yet");
        }
    }
}
