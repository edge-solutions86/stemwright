package io.github.rkaushikdev.stemwright.cli;

import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A command line after {@link Args#parse}: the arguments that were not flags, in order, and the
 * flags that were given, by their declared name.
 *
 * <p>The readers ask for a kind and give nothing when the flag was not given or is of another kind,
 * so a command reads its flags without casting.
 *
 * @param positionals the arguments that were not flags, in the order typed
 * @param flags the flags given, by declared name, unmodifiable and in the order first given
 */
public record ParsedArgs(List<String> positionals, Map<String, FlagValue> flags) {

    public ParsedArgs {
        positionals = List.copyOf(positionals);
        flags = Collections.unmodifiableMap(new LinkedHashMap<>(flags));
    }

    public Optional<String> stringFlag(String name) {
        return flags.get(name) instanceof FlagValue.StringValue string ? Optional.of(string.value()) : Optional.empty();
    }

    public Optional<Double> numberFlag(String name) {
        return flags.get(name) instanceof FlagValue.NumberValue number ? Optional.of(number.value()) : Optional.empty();
    }

    /** True only when the boolean flag was given and not set to false. */
    public boolean booleanFlag(String name) {
        return flags.get(name) instanceof FlagValue.BooleanValue flag && flag.value();
    }

    /** The string flag, refusing its absence with {@code <name> is required}. */
    public String requireFlag(String name) {
        return stringFlag(name).orElseThrow(() -> ValidationException.field(name, "is required"));
    }
}
