package io.github.rkaushikdev.stemwright.cli;

import java.util.Objects;
import java.util.Optional;

/**
 * The declaration of one flag a command accepts. A command's flags are a map from flag name to
 * spec, in the order the command declares them.
 *
 * @param kind what the value is read as
 * @param alias the short name, typed after a single dash, such as {@code d} for {@code -d}
 * @param describe the line the help text prints for the flag
 * @param required whether the command refuses to run without it
 */
public record FlagSpec(FlagKind kind, Optional<String> alias, String describe, boolean required) {

    public FlagSpec {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(describe, "describe");
    }

    /** An optional flag with no alias. */
    public static FlagSpec of(FlagKind kind, String describe) {
        return new FlagSpec(kind, Optional.empty(), describe, false);
    }

    public FlagSpec withAlias(String alias) {
        return new FlagSpec(kind, Optional.of(alias), describe, required);
    }

    /** A copy the command refuses to run without. */
    public FlagSpec asRequired() {
        return new FlagSpec(kind, alias, describe, true);
    }
}
