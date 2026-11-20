package io.github.rkaushikdev.stemwright.domain.paradigm;

import static io.github.rkaushikdev.stemwright.support.Book.FIRST_HISTORY;
import static io.github.rkaushikdev.stemwright.support.Book.LATIN_BOOK;
import static io.github.rkaushikdev.stemwright.support.Book.NEUTER_HISTORY;
import static io.github.rkaushikdev.stemwright.support.Profiles.FIRST_DECLENSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.errors.NotFoundException;
import io.github.rkaushikdev.stemwright.domain.lexicon.ClassRegistry;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import io.github.rkaushikdev.stemwright.support.Classes;
import io.github.rkaushikdev.stemwright.support.Problems;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProfileBookTest {

    private static final ClassRegistry CLASSES = Classes.LATIN;

    private static LocalDate d(String value) {
        return CivilDates.parseCivilDate(value);
    }

    @Nested
    class Of {

        @Test
        void holdsEveryHistoryByKey() {
            assertThat(LATIN_BOOK.historyFor("first-declension")).containsSame(FIRST_HISTORY);
            assertThat(LATIN_BOOK.historyFor("third-declension")).isEmpty();
        }

        @Test
        void refusesABindingNamingAProfileItDoesNotHold() {
            ProfileBookInput input = new ProfileBookInput(
                    "latin", List.of(FIRST_HISTORY), Map.of("third-declension", "third-declension"));
            assertThat(Problems.problemMessages(() -> ProfileBook.of(input)))
                    .containsExactly(
                            "bindings.third-declension names a profile the book does not hold: third-declension");
        }

        @Test
        void refusesTheSameHistoryTwice() {
            ProfileBookInput input = new ProfileBookInput("latin", List.of(FIRST_HISTORY, FIRST_HISTORY), Map.of());
            assertThat(Problems.problemMessages(() -> ProfileBook.of(input)))
                    .containsExactly("histories.first-declension appears twice");
        }

        @Test
        void acceptsABookWithNoBindingsAtAll() {
            ProfileBook bare = ProfileBook.of(new ProfileBookInput("latin", List.of(FIRST_HISTORY), Map.of()));
            assertThat(bare.bindings()).isEmpty();
        }
    }

    @Nested
    class BindingFor {

        @Test
        void findsABindingOnTheClassItself() {
            assertThat(LATIN_BOOK.bindingFor(CLASSES, "first-declension"))
                    .contains(new ProfileBook.Binding("first-declension", "first-declension"));
        }

        @Test
        void inheritsABindingFromAnAncestor() {
            assertThat(LATIN_BOOK.bindingFor(CLASSES, "second-declension.neuter"))
                    .contains(new ProfileBook.Binding("second-neuter", "second-declension"));
        }

        @Test
        void givesNothingForAClassWithNoBindingAnywhereUpTheChain() {
            assertThat(LATIN_BOOK.bindingFor(CLASSES, "third-declension")).isEmpty();
        }
    }

    @Nested
    class ProfileFor {

        @Test
        void resolvesTheRevisionInForceOnTheDay() {
            Optional<ResolvedProfile> resolved =
                    LATIN_BOOK.profileFor(CLASSES, "second-declension.neuter", d("2024-10-01"));
            assertThat(resolved.map(found -> found.revision().ordinal())).contains(1);
            assertThat(resolved.map(ResolvedProfile::boundThrough)).contains("second-declension");
        }

        @Test
        void movesToTheLaterRevisionOnceItTakesEffect() {
            Optional<ResolvedProfile> resolved =
                    LATIN_BOOK.profileFor(CLASSES, "second-declension.r-stem", d("2025-03-01"));
            assertThat(resolved.map(found -> found.revision().ordinal())).contains(2);
            assertThat(resolved)
                    .hasValueSatisfying(found ->
                            assertThat(found.revision().syncretism().groups()).hasSize(2));
        }

        @Test
        void givesNothingBeforeTheProfileWasPublished() {
            assertThat(LATIN_BOOK.profileFor(CLASSES, "first-declension", d("2024-01-01")))
                    .isEmpty();
        }

        @Test
        void givesNothingForAnUnboundClass() {
            assertThat(LATIN_BOOK.profileFor(CLASSES, "third-declension", d("2025-01-01")))
                    .isEmpty();
        }

        @Test
        void throwsWithTheClassAndTheDayWhenACallerDemandsOne() {
            assertThatThrownBy(() -> LATIN_BOOK.requireProfileFor(CLASSES, "third-declension", d("2025-01-01")))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("third-declension on 2025-01-01");
        }
    }

    @Nested
    class UnboundClasses {

        @Test
        void namesEveryClassWithNoBindingUpItsChain() {
            assertThat(LATIN_BOOK.unboundClasses(CLASSES))
                    .containsExactly(
                            "first-conjugation",
                            "third-conjugation",
                            "third-conjugation.io",
                            "third-declension",
                            "third-declension.i-stem");
        }

        @Test
        void shrinksAsBindingsAreAdded() {
            ProfileBook full = LATIN_BOOK.withBinding("third-declension", "first-declension");
            assertThat(full.unboundClasses(CLASSES))
                    .containsExactly("first-conjugation", "third-conjugation", "third-conjugation.io");
        }
    }

    @Nested
    class WithBindingAndWithHistory {

        @Test
        void addsABindingAndLeavesTheOriginalAlone() {
            ProfileBook grown = LATIN_BOOK.withBinding("third-declension", "first-declension");
            assertThat(grown.bindingFor(CLASSES, "third-declension.i-stem").map(ProfileBook.Binding::profileKey))
                    .contains("first-declension");
            assertThat(LATIN_BOOK.bindingFor(CLASSES, "third-declension.i-stem"))
                    .isEmpty();
        }

        @Test
        void replacesAHistoryUnderTheSameKey() {
            RevisionHistory shorter = RevisionHistory.of(
                    "second-neuter",
                    List.of(ProfileRevisionInput.of(
                            d("2024-09-01"), NEUTER_HISTORY.revisions().get(0).profile())));
            ProfileBook changed = LATIN_BOOK.withHistory(shorter);
            assertThat(changed.historyFor("second-neuter"))
                    .hasValueSatisfying(
                            history -> assertThat(history.revisions()).hasSize(1));
            assertThat(LATIN_BOOK.historyFor("second-neuter"))
                    .hasValueSatisfying(
                            history -> assertThat(history.revisions()).hasSize(2));
        }

        @Test
        void addsAHistoryTheBookDidNotHold() {
            RevisionHistory third = RevisionHistory.of(
                    "first-declension", List.of(ProfileRevisionInput.of(d("2024-09-01"), FIRST_DECLENSION)));
            assertThat(LATIN_BOOK.withHistory(third).historyFor("first-declension"))
                    .hasValueSatisfying(
                            history -> assertThat(history.revisions()).hasSize(1));
        }
    }
}
