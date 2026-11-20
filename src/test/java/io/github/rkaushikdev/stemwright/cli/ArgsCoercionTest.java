package io.github.rkaushikdev.stemwright.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The corners of the grammar: how leniently a number flag is read (spacing, hex, exponents, a bare
 * fraction, blank as zero), and how the dashes are read.
 */
class ArgsCoercionTest {

    private static ParsedArgs parse(String... argv) {
        Map<String, FlagSpec> specs = new LinkedHashMap<>();
        specs.put("limit", FlagSpec.of(FlagKind.NUMBER, "how many questions"));
        specs.put("deck", FlagSpec.of(FlagKind.STRING, "the deck to drill").withAlias("d"));
        return Args.parse(List.of(argv), specs);
    }

    @Test
    void readsANumberFlagLeniently() {
        assertThat(parse("--limit", " 12 ").numberFlag("limit")).contains(12.0);
        assertThat(parse("--limit", "0x10").numberFlag("limit")).contains(16.0);
        assertThat(parse("--limit", "1e3").numberFlag("limit")).contains(1000.0);
        assertThat(parse("--limit", ".5").numberFlag("limit")).contains(0.5);
        assertThat(parse("--limit=").numberFlag("limit")).contains(0.0);
        assertThat(parse("--limit=-5").numberFlag("limit")).contains(-5.0);
    }

    @Test
    void refusesANumberThatIsNotFinite() {
        assertThatThrownBy(() -> parse("--limit", "Infinity")).hasMessage("limit is not a number: Infinity");
        assertThatThrownBy(() -> parse("--limit", "1_000")).hasMessage("limit is not a number: 1_000");
    }

    @Test
    void readsANegativeNumberOnlyAfterAnEqualsSign() {
        assertThatThrownBy(() -> parse("--limit", "-5")).hasMessage("limit needs a value");
    }

    @Test
    void readsANameOrAnAliasAfterEitherOneDashOrTwo() {
        assertThat(parse("-deck", "all").stringFlag("deck")).contains("all");
        assertThat(parse("--d", "all").stringFlag("deck")).contains("all");
        assertThat(parse("-d=all").stringFlag("deck")).contains("all");
        assertThat(parse("--deck", "-").stringFlag("deck")).contains("-");
    }
}
