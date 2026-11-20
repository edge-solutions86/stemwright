package io.github.rkaushikdev.stemwright.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ArgsTest {

    private static final Map<String, FlagSpec> SPECS = specs();

    private static Map<String, FlagSpec> specs() {
        Map<String, FlagSpec> specs = new LinkedHashMap<>();
        specs.put("deck", FlagSpec.of(FlagKind.STRING, "the deck to drill").withAlias("d"));
        specs.put("limit", FlagSpec.of(FlagKind.NUMBER, "how many questions"));
        specs.put("verbose", FlagSpec.of(FlagKind.BOOLEAN, "say more").withAlias("v"));
        specs.put("learner", FlagSpec.of(FlagKind.STRING, "whose queue").asRequired());
        return specs;
    }

    private static ParsedArgs parse(String... argv) {
        return Args.parse(List.of(argv), SPECS);
    }

    @Nested
    class Positionals {

        @Test
        void collectsArgumentsThatAreNotFlags() {
            assertThat(parse("drill", "latin", "--learner", "lrn_one").positionals())
                    .containsExactly("drill", "latin");
        }

        @Test
        void treatsABareDashAsAPositional() {
            assertThat(parse("-", "--learner", "lrn_one").positionals()).containsExactly("-");
        }

        @Test
        void stopsReadingFlagsAfterADoubleDash() {
            ParsedArgs parsed = parse("--learner", "lrn_one", "--", "--deck", "all");
            assertThat(parsed.positionals()).containsExactly("--deck", "all");
            assertThat(parsed.stringFlag("deck")).isEmpty();
        }
    }

    @Nested
    class Flags {

        @Test
        void readsAValueAfterTheFlag() {
            assertThat(parse("--learner", "lrn_one", "--deck", "all").stringFlag("deck"))
                    .contains("all");
        }

        @Test
        void readsAValueJoinedByAnEqualsSign() {
            assertThat(parse("--learner=lrn_one", "--deck=all").stringFlag("deck"))
                    .contains("all");
        }

        @Test
        void readsAnAlias() {
            assertThat(parse("--learner", "lrn_one", "-d", "all").stringFlag("deck"))
                    .contains("all");
        }

        @Test
        void readsABooleanFlagWithNoValue() {
            assertThat(parse("--learner", "lrn_one", "--verbose").booleanFlag("verbose"))
                    .isTrue();
            assertThat(parse("--learner", "lrn_one").booleanFlag("verbose")).isFalse();
        }

        @Test
        void readsAnExplicitBooleanValue() {
            assertThat(parse("--learner", "lrn_one", "--verbose=false").booleanFlag("verbose"))
                    .isFalse();
        }

        @Test
        void readsANumber() {
            assertThat(parse("--learner", "lrn_one", "--limit", "12").numberFlag("limit"))
                    .contains(12.0);
        }

        @Test
        void letsALaterFlagWin() {
            assertThat(parse("--learner", "a", "--learner", "b").stringFlag("learner"))
                    .contains("b");
        }
    }

    @Nested
    class Failures {

        @Test
        void refusesAFlagNobodyDeclared() {
            assertThatThrownBy(() -> parse("--learner", "lrn_one", "--nonsense", "x"))
                    .hasMessageContaining("is not an option of this command: nonsense");
        }

        @Test
        void refusesAValueFlagWithNothingAfterIt() {
            assertThatThrownBy(() -> parse("--learner")).hasMessageContaining("learner needs a value");
        }

        @Test
        void refusesAValueFlagFollowedByAnotherFlag() {
            assertThatThrownBy(() -> parse("--learner", "--verbose")).hasMessageContaining("needs a value");
        }

        @Test
        void refusesANumberThatIsNotOne() {
            assertThatThrownBy(() -> parse("--learner", "lrn_one", "--limit", "lots"))
                    .hasMessageContaining("is not a number");
        }

        @Test
        void refusesABooleanGivenSomethingOtherThanTrueOrFalse() {
            assertThatThrownBy(() -> parse("--learner", "lrn_one", "--verbose=yes"))
                    .hasMessageContaining("takes true or false");
        }

        @Test
        void refusesAMissingRequiredFlag() {
            assertThatThrownBy(() -> parse("drill")).hasMessageContaining("learner is required");
        }
    }

    @Nested
    class Accessors {

        @Test
        void readsARequiredFlagOrRefuses() {
            ParsedArgs parsed = parse("--learner", "lrn_one");
            assertThat(parsed.requireFlag("learner")).isEqualTo("lrn_one");
            assertThatThrownBy(() -> parsed.requireFlag("deck")).isInstanceOf(ValidationException.class);
        }

        @Test
        void givesNothingWhenAFlagIsOfAnotherKind() {
            ParsedArgs parsed = parse("--learner", "lrn_one", "--limit", "3");
            assertThat(parsed.stringFlag("limit")).isEmpty();
            assertThat(parsed.numberFlag("learner")).isEmpty();
        }
    }

    @Nested
    class FormatFlags {

        @Test
        void listsTheFlagsInNameOrderWithTheirAliases() {
            List<String> lines = Args.formatFlags(SPECS);
            assertThat(lines.get(0)).isEqualTo("  --deck, -d  the deck to drill");
            assertThat(lines).anyMatch(line -> line.contains("(required)"));
        }
    }
}
