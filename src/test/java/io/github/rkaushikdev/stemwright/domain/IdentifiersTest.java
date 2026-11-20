package io.github.rkaushikdev.stemwright.domain;

import static io.github.rkaushikdev.stemwright.domain.Identifiers.asId;
import static io.github.rkaushikdev.stemwright.domain.Identifiers.asSlug;
import static io.github.rkaushikdev.stemwright.domain.Identifiers.createAllocator;
import static io.github.rkaushikdev.stemwright.domain.Identifiers.derivedId;
import static io.github.rkaushikdev.stemwright.domain.Identifiers.digest;
import static io.github.rkaushikdev.stemwright.domain.Identifiers.isId;
import static io.github.rkaushikdev.stemwright.domain.Identifiers.isSlug;
import static io.github.rkaushikdev.stemwright.domain.Identifiers.kindOf;
import static io.github.rkaushikdev.stemwright.domain.Identifiers.slugify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IdentifiersTest {

    @Nested
    class IsId {

        @Test
        void acceptsAWellFormedIdOfTheAskedForKind() {
            assertThat(isId(IdKind.LEXEME, "lex_9fx2ab")).isTrue();
            assertThat(isId(IdKind.DECK, "deck_00zz11")).isTrue();
        }

        @Test
        void rejectsTheRightShapeUnderTheWrongPrefix() {
            assertThat(isId(IdKind.LEXEME, "deck_9fx2ab")).isFalse();
        }

        @Test
        void rejectsABodyThatIsTooShortTooLongOrNotLowerAlphanumeric() {
            assertThat(isId(IdKind.LEXEME, "lex_9fx2a")).isFalse();
            assertThat(isId(IdKind.LEXEME, "lex_" + "a".repeat(33))).isFalse();
            assertThat(isId(IdKind.LEXEME, "lex_9FX2AB")).isFalse();
            assertThat(isId(IdKind.LEXEME, "lex_9fx2-ab")).isFalse();
        }

        @Test
        void rejectsAnythingThatIsNotAString() {
            assertThat(isId(IdKind.LEXEME, 42)).isFalse();
            assertThat(isId(IdKind.LEXEME, null)).isFalse();
            assertThat(isId(IdKind.LEXEME, List.of("lex_9fx2ab"))).isFalse();
        }
    }

    @Nested
    class AsId {

        @Test
        void returnsTheValueWhenItParses() {
            assertThat(asId(IdKind.PROFILE, "prof_abc123")).isEqualTo("prof_abc123");
        }

        @Test
        void namesTheKindInTheFailure() {
            assertThatThrownBy(() -> asId(IdKind.PROFILE, "lex_abc123")).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> asId(IdKind.PROFILE, "lex_abc123")).hasMessageContaining("well formed profile id");
        }
    }

    @Nested
    class KindOf {

        @Test
        void readsTheKindBackOffThePrefix() {
            assertThat(kindOf("cell_aabbcc")).contains(IdKind.CELL);
            assertThat(kindOf("asg_aabbcc")).contains(IdKind.ASSIGNMENT);
        }

        @Test
        void givesNothingForAnUnknownPrefixOrAMalformedBody() {
            assertThat(kindOf("what_aabbcc")).isEmpty();
            assertThat(kindOf("cell_AA")).isEmpty();
            assertThat(kindOf("nounderscore")).isEmpty();
            assertThat(kindOf("_leading")).isEmpty();
        }

        @Test
        void coversEveryPrefixTheModuleDeclares() {
            for (IdKind kind : IdKind.values()) {
                String id = derivedId(kind, "sample");
                assertThat(kindOf(id)).contains(kind);
            }
        }
    }

    @Nested
    class Digest {

        @Test
        void isStableForTheSameParts() {
            assertThat(digest("a", "b")).isEqualTo(digest("a", "b"));
        }

        @Test
        void separatesInputsThatDiffer() {
            assertThat(digest("nom.sg")).isNotEqualTo(digest("nom.pl"));
            assertThat(digest("ab", "c")).isNotEqualTo(digest("a", "bd"));
        }

        @Test
        void isTwelveLowerAlphanumericCharacters() {
            assertThat(digest("anything at all")).matches("^[0-9a-z]{12}$");
            assertThat(digest("")).matches("^[0-9a-z]{12}$");
        }
    }

    /**
     * Derived ids are stored in every journal the original wrote, so the digest has to give the same
     * text it gave there. The expected values were computed by an independent emulation of the
     * original's 32 bit arithmetic over utf-16 code units, not by this implementation.
     */
    @Nested
    class StoredIdCompatibility {

        @Test
        void digestsAsTheOriginalDid() {
            assertThat(digest("a", "b")).isEqualTo("07hw33l130bq");
            assertThat(digest("")).isEqualTo("0ztntfp009zl");
            assertThat(digest("lex_aaaaaa", "case=nom;number=sg")).isEqualTo("1vx89a4143zb");
            assertThat(digest("greek", "ἄνθρωπος", "2")).isEqualTo("0vczjk90a5j2");
            assertThat(digest("x".repeat(300))).isEqualTo("15wcelx192qd");
        }

        @Test
        void derivesTheLexemeIdTheOriginalStored() {
            assertThat(derivedId(IdKind.LEXEME, "latin", "puella", "1")).isEqualTo("lex_08bkzeb0i0al");
        }
    }

    @Nested
    class DerivedId {

        @Test
        void dependsOnlyOnTheParts() {
            String first = derivedId(IdKind.CELL, "lex_aaaaaa", "nom.sg");
            String second = derivedId(IdKind.CELL, "lex_aaaaaa", "nom.sg");
            assertThat(first).isEqualTo(second);
            assertThat(isId(IdKind.CELL, first)).isTrue();
        }

        @Test
        void refusesToDeriveFromNothing() {
            assertThatThrownBy(() -> derivedId(IdKind.CELL)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class CreateAllocator {

        @Test
        void handsOutDistinctIdsOfTheAskedForKind() {
            IdAllocator allocator = createAllocator("run-1");
            List<String> ids =
                    List.of(allocator.next(IdKind.LEXEME), allocator.next(IdKind.LEXEME), allocator.next(IdKind.DECK));
            assertThat(Set.copyOf(ids)).hasSize(3);
            assertThat(isId(IdKind.LEXEME, ids.get(0))).isTrue();
            assertThat(isId(IdKind.DECK, ids.get(2))).isTrue();
        }

        @Test
        void separatesTwoRunsWithDifferentSeeds() {
            String a = createAllocator("run-a").next(IdKind.ATTEMPT);
            String b = createAllocator("run-b").next(IdKind.ATTEMPT);
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void resumesFromAStartCounter() {
            IdAllocator fresh = createAllocator("run-c");
            fresh.next(IdKind.SESSION);
            String expected = fresh.next(IdKind.SESSION);
            assertThat(createAllocator("run-c", 1).next(IdKind.SESSION)).isEqualTo(expected);
        }
    }

    @Nested
    class Slugs {

        @Test
        void acceptsDottedAndDashedLowerCaseKeys() {
            assertThat(asSlug("class", "o-stem")).isEqualTo("o-stem");
            assertThat(asSlug("feature", "case.nom")).isEqualTo("case.nom");
            assertThat(isSlug("a1")).isTrue();
        }

        @Test
        void trimsAndLowerCasesBeforeChecking() {
            assertThat(asSlug("class", "  A-Stem  ")).isEqualTo("a-stem");
        }

        @Test
        void refusesLeadingDigitsDoubledSeparatorsAndEmptyParts() {
            assertThat(isSlug("1stem")).isFalse();
            assertThat(isSlug("o--stem")).isFalse();
            assertThat(isSlug("o-")).isFalse();
            assertThat(isSlug("")).isFalse();
            assertThatThrownBy(() -> asSlug("class", "o stem")).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class Slugify {

        @Test
        void foldsDiacriticsAndCollapsesSeparators() {
            assertThat(slugify("Ǎ-stem  (Attic)")).isEqualTo("a-stem-attic");
            assertThat(slugify("genitive/dative")).isEqualTo("genitive-dative");
        }

        @Test
        void alwaysReturnsSomethingASlugCheckAccepts() {
            for (String input : List.of("", "   ", "123", "???", "Ω", "2nd declension")) {
                assertThat(isSlug(slugify(input))).as(input).isTrue();
            }
        }
    }
}
