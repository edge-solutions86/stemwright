package io.github.rkaushikdev.stemwright.cli;

import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.text.Collation;
import io.github.rkaushikdev.stemwright.domain.text.Numbers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Argument parsing, written rather than installed. The grammar is small and fixed, and its messages
 * and its rules (a boolean flag takes {@code =false}, a number is read as JavaScript reads one) are
 * part of what the cli promises, which a general purpose parser would change. picocli starts the
 * process and hands the arguments on; this is what reads them.
 *
 * <p>Two rules keep it predictable. A flag needs a declaration, so a typo is an error rather than a
 * silently ignored option. And {@code --} ends the flags, so a lemma that begins with a dash can
 * still be typed.
 *
 * <p>The grammar, in full: a token that does not begin with a dash, and a lone {@code -}, is a
 * positional. {@code --} makes every later token a positional. Otherwise one or two leading dashes
 * are dropped and what is left names a flag, by its name or its alias; {@code name=value} gives the
 * value in the same token. A boolean flag with no {@code =} is set to true; any other flag takes the
 * next token as its value, which must not itself begin with a dash unless it is a lone {@code -}. A
 * flag given twice keeps its last value.
 */
public final class Args {

    private Args() {}

    /**
     * Reads a command line against the flags a command declares, checked in the order the map
     * iterates in, which also decides which missing required flag is named.
     *
     * @throws ValidationException naming the flag for an undeclared flag, a value flag with nothing
     *     after it, a number that is not a finite number, a boolean given anything but {@code true}
     *     or {@code false}, and a required flag that was not given
     */
    public static ParsedArgs parse(List<String> argv, Map<String, FlagSpec> specs) {
        List<String> positionals = new ArrayList<>();
        Map<String, FlagValue> flags = new LinkedHashMap<>();
        boolean literal = false;

        for (int index = 0; index < argv.size(); index += 1) {
            String token = argv.get(index);

            if (literal || !token.startsWith("-") || token.equals("-")) {
                positionals.add(token);
                continue;
            }
            if (token.equals("--")) {
                literal = true;
                continue;
            }

            String trimmed = token.substring(token.startsWith("--") ? 2 : 1);
            int equals = trimmed.indexOf('=');
            String rawName = equals < 0 ? trimmed : trimmed.substring(0, equals);
            String name = resolveName(specs, rawName);
            FlagSpec spec = specs.get(name);

            if (equals >= 0) {
                flags.put(name, coerce(name, spec, trimmed.substring(equals + 1)));
                continue;
            }
            if (spec.kind() == FlagKind.BOOLEAN) {
                flags.put(name, new FlagValue.BooleanValue(true));
                continue;
            }

            if (index + 1 >= argv.size() || isFlagLike(argv.get(index + 1))) {
                throw ValidationException.field(name, "needs a value");
            }
            flags.put(name, coerce(name, spec, argv.get(index + 1)));
            index += 1;
        }

        for (Map.Entry<String, FlagSpec> entry : specs.entrySet()) {
            if (entry.getValue().required() && !flags.containsKey(entry.getKey())) {
                throw ValidationException.field(entry.getKey(), "is required");
            }
        }

        return new ParsedArgs(positionals, flags);
    }

    /**
     * The help lines for the flags, in name order: {@code --name, -alias  what it does}, with
     * {@code (required)} after a flag the command cannot run without.
     */
    public static List<String> formatFlags(Map<String, FlagSpec> specs) {
        return specs.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Collation.ORDER))
                .map(entry -> {
                    FlagSpec spec = entry.getValue();
                    String alias = spec.alias().map(value -> ", -" + value).orElse("");
                    String required = spec.required() ? " (required)" : "";
                    return "  --" + entry.getKey() + alias + "  " + spec.describe() + required;
                })
                .toList();
    }

    private static boolean isFlagLike(String token) {
        return token.startsWith("-") && !token.equals("-");
    }

    private static String resolveName(Map<String, FlagSpec> specs, String token) {
        if (specs.containsKey(token)) {
            return token;
        }
        for (Map.Entry<String, FlagSpec> entry : specs.entrySet()) {
            if (entry.getValue().alias().filter(token::equals).isPresent()) {
                return entry.getKey();
            }
        }
        throw ValidationException.field("flag", "is not an option of this command: " + token);
    }

    private static FlagValue coerce(String name, FlagSpec spec, String raw) {
        return switch (spec.kind()) {
            case STRING -> new FlagValue.StringValue(raw);
            case NUMBER -> {
                double value = Numbers.parse(raw);
                if (!Double.isFinite(value)) {
                    throw ValidationException.field(name, "is not a number: " + raw);
                }
                yield new FlagValue.NumberValue(value);
            }
            case BOOLEAN -> {
                if (raw.equals("true") || raw.equals("false")) {
                    yield new FlagValue.BooleanValue(raw.equals("true"));
                }
                throw ValidationException.field(name, "is a flag and takes true or false, not " + raw);
            }
        };
    }
}
