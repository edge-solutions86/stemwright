package io.github.rkaushikdev.stemwright.cli;

import static io.github.rkaushikdev.stemwright.support.Problems.detailsOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.app.Clock;
import io.github.rkaushikdev.stemwright.domain.course.AccountRole;
import io.github.rkaushikdev.stemwright.domain.course.Actor;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FlagsTest {

    private static final LocalDate TODAY = CivilDates.parseCivilDate("2024-10-21");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2024-10-21T09:00:00Z"), TODAY);

    private static final Map<String, FlagSpec> SPECS = specs();

    private static Map<String, FlagSpec> specs() {
        Map<String, FlagSpec> specs = new LinkedHashMap<>();
        specs.put("on", Flags.DAY);
        specs.put("actor", Flags.ACTOR);
        specs.put("spread", FlagSpec.of(FlagKind.NUMBER, "how many to list"));
        specs.put("since", Flags.DAY);
        return specs;
    }

    private static ParsedArgs parse(String... argv) {
        return Args.parse(List.of(argv), SPECS);
    }

    @Nested
    class TheSharedFlagSpecs {

        @Test
        void marksTheThreeFlagsACommandCannotRunWithout() {
            for (FlagSpec spec : List.of(Flags.LANGUAGE, Flags.DECK, Flags.LEARNER)) {
                assertThat(spec.required()).isTrue();
                assertThat(spec.kind()).isEqualTo(FlagKind.STRING);
            }
        }

        @Test
        void leavesTheDayAndTheActorOptional() {
            assertThat(Flags.DAY.required()).isFalse();
            assertThat(Flags.ACTOR.required()).isFalse();
        }

        @Test
        void givesTheTwoMostTypedFlagsAShortAliasAndKeepsThemApart() {
            assertThat(Flags.DECK.alias()).contains("d");
            assertThat(Flags.LEARNER.alias()).contains("l");
            assertThat(Flags.LANGUAGE.alias()).isEmpty();
        }

        @Test
        void describesEveryOneOfThem() {
            for (FlagSpec spec : List.of(Flags.LANGUAGE, Flags.DECK, Flags.LEARNER, Flags.DAY, Flags.ACTOR)) {
                assertThat(spec.describe().length()).isGreaterThan(4);
            }
        }
    }

    @Nested
    class DayFrom {

        @Test
        void readsTheDayOffTheFlag() {
            assertThat(Flags.dayFrom(CLOCK, parse("--on", "2025-03-04")))
                    .isEqualTo(CivilDates.parseCivilDate("2025-03-04"));
        }

        @Test
        void fallsBackToTheClockTheApplicationWasBuiltWith() {
            assertThat(Flags.dayFrom(CLOCK, parse())).isEqualTo(TODAY);
        }

        @Test
        void readsAFlagUnderAnotherName() {
            assertThat(Flags.dayFrom(CLOCK, parse("--since", "2023-12-31"), "since"))
                    .isEqualTo(CivilDates.parseCivilDate("2023-12-31"));
        }

        @Test
        void refusesADayThatIsNotADay() {
            assertThatThrownBy(() -> Flags.dayFrom(CLOCK, parse("--on", "yesterday")))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void namesTheFlagItRefusedRatherThanTheOneItReadsByDefault() {
            assertThat(detailsOf(() -> Flags.dayFrom(CLOCK, parse("--since", "2024-13-40"), "since"))
                            .get(0)
                            .path())
                    .isEqualTo("since");
        }
    }

    @Nested
    class ActorFrom {

        @Test
        void takesTheIdOffTheFlag() {
            assertThat(Flags.actorFrom(parse("--actor", "lrn_teacher")))
                    .isEqualTo(new Actor("lrn_teacher", AccountRole.INSTRUCTOR));
        }

        @Test
        void fallsBackToANameThatSaysWhereTheWriteCameFrom() {
            assertThat(Flags.actorFrom(parse()).id()).isEqualTo("cli");
        }

        @Test
        void alwaysClaimsTheInstructorRole() {
            assertThat(Flags.actorFrom(parse("--actor", "lrn_one")).role()).isEqualTo(AccountRole.INSTRUCTOR);
        }
    }

    @Nested
    class CountFrom {

        @Test
        void takesTheNumberOffTheFlag() {
            assertThat(Flags.countFrom(parse("--spread", "7"), "spread", 3)).isEqualTo(7);
        }

        @Test
        void fallsBackWhenTheFlagWasLeftOff() {
            assertThat(Flags.countFrom(parse(), "spread", 3)).isEqualTo(3);
        }

        @Test
        void refusesACountOfZeroOrBelow() {
            assertThatThrownBy(() -> Flags.countFrom(parse("--spread", "0"), "spread", 3))
                    .hasMessageContaining("must be a whole number above zero");
            assertThatThrownBy(() -> Flags.countFrom(parse("--spread", "-2"), "spread", 3))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void refusesACountThatIsNotWhole() {
            assertThatThrownBy(() -> Flags.countFrom(parse("--spread", "2.5"), "spread", 3))
                    .hasMessageContaining("not 2.5");
        }
    }
}
