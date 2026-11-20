package io.github.rkaushikdev.stemwright.domain.drill;

import static io.github.rkaushikdev.stemwright.support.Drill.AGER;
import static io.github.rkaushikdev.stemwright.support.Drill.BELLUM;
import static io.github.rkaushikdev.stemwright.support.Drill.DRILL_CONTEXT;
import static io.github.rkaushikdev.stemwright.support.Drill.PUELLA;
import static io.github.rkaushikdev.stemwright.support.Drill.REX;
import static io.github.rkaushikdev.stemwright.support.Profiles.LATIN_NOUN;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.domain.lexicon.Lexeme;
import io.github.rkaushikdev.stemwright.domain.paradigm.Cell;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MembershipTest {

    private static final LocalDate TERM = d("2024-10-01");

    private static LocalDate d(String value) {
        return CivilDates.parseCivilDate(value);
    }

    private static Deck build(DeckInput input) {
        return Deck.of(LATIN_NOUN, input);
    }

    private static Deck build(String key, DeckScopeInput.Builder scope) {
        return build(DeckInput.builder(key).scope(scope.build()).build());
    }

    private static DeckScopeInput.Builder scope() {
        return DeckScopeInput.builder();
    }

    private static Membership resolve(Deck deck, LocalDate on) {
        return Membership.resolve(DRILL_CONTEXT, deck, on);
    }

    private static Membership everything(LocalDate on) {
        return resolve(build(DeckInput.of("all")), on);
    }

    private static List<String> lemmasOf(List<Lexeme> lexemes) {
        return lexemes.stream().map(Lexeme::lemma).toList();
    }

    @Nested
    class Resolve {

        @Test
        void takesEveryBoundLexemeWhenTheDeckConstrainsNothing() {
            Membership membership = everything(TERM);
            assertThat(lemmasOf(membership.lexemes())).containsExactly("ager", "bellum", "puella");
            assertThat(membership.size()).isEqualTo(30);
        }

        @Test
        void skipsALexemeWhoseClassHasNoProfileBound() {
            Membership membership = everything(TERM);
            assertThat(membership.skippedFor(SkipReason.UNBOUND_CLASS).stream()
                            .map(SkippedLexeme::lemma)
                            .toList())
                    .containsExactly("rex");
            assertThat(membership.lexemes()).doesNotContain(REX);
        }

        @Test
        void narrowsByClassAndTakesDescendantsWithIt() {
            Membership membership = resolve(build("second", scope().classKeys("second-declension")), TERM);
            assertThat(lemmasOf(membership.lexemes())).containsExactly("ager", "bellum");
        }

        @Test
        void narrowsByTag() {
            Membership membership = resolve(build("core", scope().tags("core")), TERM);
            assertThat(lemmasOf(membership.lexemes())).containsExactly("bellum", "puella");
        }

        @Test
        void intersectsClassAndTagRatherThanUnitingThem() {
            Membership membership =
                    resolve(build("both", scope().classKeys("second-declension").tags("core")), TERM);
            assertThat(lemmasOf(membership.lexemes())).containsExactly("bellum");
        }

        @Test
        void narrowsByNamedLexeme() {
            Membership membership = resolve(build("named", scope().lexemeIds(PUELLA.id(), AGER.id())), TERM);
            assertThat(lemmasOf(membership.lexemes())).containsExactly("ager", "puella");
        }

        @Test
        void narrowsTheCellsWithoutDroppingTheLexeme() {
            Membership membership = resolve(build("plural", scope().cells("number=pl")), TERM);
            assertThat(membership.lexemes()).hasSize(3);
            assertThat(membership.size()).isEqualTo(15);
            assertThat(membership.bundlesCovered()).hasSize(5);
        }

        @Test
        void skipsALexemeWhoseCellsAreAllOutsideTheSelector() {
            Membership membership = resolve(build("none", scope().cells("case=nom,case!=nom")), TERM);
            assertThat(membership.skippedFor(SkipReason.NO_CELL_IN_SCOPE)).hasSize(3);
            assertThat(membership.isEmpty()).isTrue();
        }

        @Test
        void skipsEverythingBeforeTheProfilesWerePublished() {
            Membership membership = everything(d("2024-01-01"));
            assertThat(membership.skippedFor(SkipReason.PROFILE_NOT_PUBLISHED)).hasSize(3);
            assertThat(membership.skippedFor(SkipReason.UNBOUND_CLASS)).hasSize(1);
            assertThat(membership.isEmpty()).isTrue();
        }

        @Test
        void recordsTheDayItWasResolvedFor() {
            assertThat(everything(TERM).on()).isEqualTo(d("2024-10-01"));
        }
    }

    @Nested
    class CellsByLexeme {

        @Test
        void groupsTheCellsUnderTheirLexeme() {
            Map<String, List<Cell>> grouped = everything(TERM).cellsByLexeme();
            assertThat(grouped).hasSize(3);
            assertThat(grouped.get(BELLUM.id())).hasSize(10);
        }

        @Test
        void isEmptyWhenNothingWasSelected() {
            assertThat(everything(d("2024-01-01")).cellsByLexeme()).isEmpty();
        }
    }

    @Nested
    class Warnings {

        @Test
        void saysNothingAboutLexemesThatAreMerelyOutOfScope() {
            Membership membership = resolve(build("named", scope().lexemeIds(PUELLA.id())), TERM);
            assertThat(membership.warnings()).isEmpty();
        }

        @Test
        void warnsAboutAnUnboundClass() {
            assertThat(everything(TERM).warnings()).containsExactly("1 lexemes have no paradigm bound to their class");
        }

        @Test
        void warnsWhenTheDeckSelectsNothingAtAll() {
            Membership membership = resolve(build("none", scope().cells("case=nom,case!=nom")), TERM);
            assertThat(membership.warnings()).contains("the deck selects nothing at all");
        }

        @Test
        void warnsAboutAProfileWithNoRevisionInForce() {
            assertThat(everything(d("2024-01-01")).warnings())
                    .containsExactly(
                            "1 lexemes have no paradigm bound to their class",
                            "3 lexemes use a profile with no revision in force on 2024-01-01",
                            "the deck selects nothing at all");
        }
    }
}
