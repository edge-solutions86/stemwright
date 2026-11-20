package io.github.rkaushikdev.stemwright.cli;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A command's flags in the order it declares them. The order is not cosmetic: it is the order
 * {@link Args#parse} checks the required flags in, so it decides which missing flag a refusal names.
 */
final class FlagSpecs {

    private FlagSpecs() {}

    /** The flags in the order given, a name given twice keeping its last spec. */
    @SafeVarargs
    static Map<String, FlagSpec> of(Map.Entry<String, FlagSpec>... flags) {
        Map<String, FlagSpec> declared = new LinkedHashMap<>();
        for (Map.Entry<String, FlagSpec> flag : flags) {
            declared.put(flag.getKey(), flag.getValue());
        }
        return declared;
    }
}
