package io.github.rkaushikdev.stemwright.domain.paradigm;

import static io.github.rkaushikdev.stemwright.support.Profiles.FIRST_DECLENSION;
import static io.github.rkaushikdev.stemwright.support.Profiles.LATIN_NOUN;
import static io.github.rkaushikdev.stemwright.support.Profiles.SECOND_NEUTER;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.support.Problems;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SyncretismMapTest {

    private static final SyncretismMap DECLARED_FIRST = SyncretismMap.of(
            LATIN_NOUN,
            List.of(
                    SyncretismGroupInput.of("ae", "gen.sg", "dat.sg", "nom.pl")
                            .withNote("puellae answers three cells at once"),
                    SyncretismGroupInput.of("is", "dat.pl", "abl.pl"),
                    SyncretismGroupInput.of("a", "nom.sg", "abl.sg")));

    @Nested
    class Of {

        @Test
        void canonicalisesEveryMember() {
            assertThat(DECLARED_FIRST.groupOf("case=gen;number=sg").map(SyncretismGroup::key))
                    .contains("ae");
        }

        @Test
        void keepsTheNoteItWasGiven() {
            assertThat(DECLARED_FIRST.groupOf("case=nom;number=pl").flatMap(SyncretismGroup::note))
                    .contains("puellae answers three cells at once");
        }

        @Test
        void refusesAGroupWithFewerThanTwoMembers() {
            assertThat(Problems.problemMessages(
                            () -> SyncretismMap.of(LATIN_NOUN, List.of(SyncretismGroupInput.of("lonely", "gen.sg")))))
                    .containsExactly("groups.0 must hold at least two bundles to be a syncretism");
        }

        @Test
        void refusesARepeatedGroupKey() {
            List<SyncretismGroupInput> inputs = List.of(
                    SyncretismGroupInput.of("a", "gen.sg", "dat.sg"), SyncretismGroupInput.of("a", "nom.pl", "acc.pl"));
            assertThat(Problems.problemMessages(() -> SyncretismMap.of(LATIN_NOUN, inputs)))
                    .containsExactly("groups.1 repeats the group key a");
        }

        @Test
        void refusesABundleClaimedByTwoGroups() {
            List<SyncretismGroupInput> inputs = List.of(
                    SyncretismGroupInput.of("first", "gen.sg", "dat.sg"),
                    SyncretismGroupInput.of("second", "dat.sg", "nom.pl"));
            List<String> messages = Problems.problemMessages(() -> SyncretismMap.of(LATIN_NOUN, inputs));
            assertThat(messages)
                    .containsExactly(
                            "groups.1 claims case=dat;number=sg which group first already holds",
                            "groups.1 must hold at least two bundles to be a syncretism");
        }

        @Test
        void refusesTheSameBundleListedTwiceInOneGroup() {
            List<SyncretismGroupInput> inputs = List.of(SyncretismGroupInput.of("a", "gen.sg", "case=gen;number=sg"));
            List<String> messages = Problems.problemMessages(() -> SyncretismMap.of(LATIN_NOUN, inputs));
            assertThat(messages).first().isEqualTo("groups.0 lists case=gen;number=sg twice");
        }

        @Test
        void refusesAMemberThatLeavesADimensionOpen() {
            List<SyncretismGroupInput> inputs = List.of(SyncretismGroupInput.of("a", "gen", "dat.sg"));
            assertThat(Problems.problemMessages(() -> SyncretismMap.of(LATIN_NOUN, inputs)))
                    .containsExactly(
                            "groups.0 member does not fix every required dimension: gen",
                            "groups.0 must hold at least two bundles to be a syncretism");
        }
    }

    @Nested
    class TwinsOf {

        @Test
        void namesTheOtherMembersAndNeverTheBundleItself() {
            assertThat(DECLARED_FIRST.twinsOf("case=gen;number=sg"))
                    .containsExactly("case=dat;number=sg", "case=nom;number=pl");
        }

        @Test
        void isEmptyForABundleInNoGroup() {
            assertThat(DECLARED_FIRST.twinsOf("case=acc;number=sg")).isEmpty();
            assertThat(SyncretismMap.NONE.twinsOf("case=gen;number=sg")).isEmpty();
        }
    }

    @Nested
    class AreSyncretic {

        @Test
        void isTrueForTwoMembersOfOneGroup() {
            assertThat(DECLARED_FIRST.areSyncretic("case=dat;number=pl", "case=abl;number=pl"))
                    .isTrue();
        }

        @Test
        void isFalseAcrossGroupsAndForABundleWithItself() {
            assertThat(DECLARED_FIRST.areSyncretic("case=dat;number=pl", "case=gen;number=sg"))
                    .isFalse();
            assertThat(DECLARED_FIRST.areSyncretic("case=gen;number=sg", "case=gen;number=sg"))
                    .isFalse();
        }

        @Test
        void readsTheGroupKeyBack() {
            assertThat(DECLARED_FIRST.groupKeyOf("case=abl;number=pl")).contains("is");
            assertThat(DECLARED_FIRST.groupKeyOf("case=acc;number=pl")).isEmpty();
        }

        @Test
        void listsEveryBundleInSomeGroup() {
            assertThat(DECLARED_FIRST.syncreticBundles()).hasSize(7);
        }
    }

    @Nested
    class DerivedFrom {

        @Test
        void groupsSlotsThatBuildTheSameString() {
            SyncretismMap derived = SyncretismMap.derivedFrom(FIRST_DECLENSION);
            assertThat(derived.areSyncretic("case=gen;number=sg", "case=dat;number=sg"))
                    .isTrue();
            assertThat(derived.areSyncretic("case=dat;number=pl", "case=abl;number=pl"))
                    .isTrue();
        }

        @Test
        void doesNotGroupSlotsThatDiffer() {
            SyncretismMap derived = SyncretismMap.derivedFrom(FIRST_DECLENSION);
            assertThat(derived.areSyncretic("case=acc;number=sg", "case=acc;number=pl"))
                    .isFalse();
        }

        @Test
        void findsTheNeuterNominativeAndAccusativeRuleFallingOutOfTheEndings() {
            SyncretismMap derived = SyncretismMap.derivedFrom(SECOND_NEUTER);
            assertThat(derived.areSyncretic("case=nom;number=sg", "case=acc;number=sg"))
                    .isTrue();
            assertThat(derived.areSyncretic("case=nom;number=pl", "case=acc;number=pl"))
                    .isTrue();
        }
    }

    @Nested
    class ReconcileWith {

        @Test
        void reportsNothingWhenTheDeclarationMatchesWhatTheEndingsProduce() {
            assertThat(DECLARED_FIRST.reconcileWith(FIRST_DECLENSION)).isEmpty();
        }

        @Test
        void reportsAPairTheProfileProducesThatNobodyDeclared() {
            List<SyncretismDisagreement> disagreements = SyncretismMap.NONE.reconcileWith(SECOND_NEUTER);
            assertThat(disagreements).hasSize(4);
            assertThat(disagreements).allMatch(entry -> entry.produced() && !entry.declared());
        }

        @Test
        void reportsAPairDeclaredThatTheProfileKeepsApart() {
            SyncretismMap overclaimed =
                    SyncretismMap.of(LATIN_NOUN, List.of(SyncretismGroupInput.of("wrong", "nom.sg", "gen.sg")));
            List<SyncretismDisagreement> disagreements = overclaimed.reconcileWith(SECOND_NEUTER);
            Optional<SyncretismDisagreement> claim = disagreements.stream()
                    .filter(entry -> entry.first().equals("case=nom;number=sg")
                            && entry.second().equals("case=gen;number=sg"))
                    .findFirst();
            assertThat(claim)
                    .contains(new SyncretismDisagreement("case=nom;number=sg", "case=gen;number=sg", true, false));
        }
    }
}
