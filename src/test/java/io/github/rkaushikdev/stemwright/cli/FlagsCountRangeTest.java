package io.github.rkaushikdev.stemwright.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A count is an {@code int} here where the original's was any number, so a whole number past what
 * an {@code int} holds is refused under the flag's name rather than wrapped.
 */
class FlagsCountRangeTest {

    private static final Map<String, FlagSpec> SPECS = Map.of("spread", FlagSpec.of(FlagKind.NUMBER, "how many"));

    @Test
    void refusesACountLargerThanAnIntHolds() {
        ParsedArgs largest = Args.parse(List.of("--spread", "2147483647"), SPECS);
        ParsedArgs past = Args.parse(List.of("--spread", "3000000000"), SPECS);

        assertThat(Flags.countFrom(largest, "spread", 3)).isEqualTo(Integer.MAX_VALUE);
        assertThatThrownBy(() -> Flags.countFrom(past, "spread", 3))
                .isInstanceOf(ValidationException.class)
                .hasMessage("spread must be at most 2147483647, not 3000000000");
    }
}
