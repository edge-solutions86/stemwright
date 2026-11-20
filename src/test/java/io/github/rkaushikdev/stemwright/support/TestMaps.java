package io.github.rkaushikdev.stemwright.support;

import java.util.LinkedHashMap;
import java.util.Map;

/** Maps written inline in a test, kept in the order they were written. */
public final class TestMaps {

    private TestMaps() {}

    /**
     * A mutable map of the given keys and values, alternating, in the order written. {@link Map#of}
     * has no defined iteration order, and several domain functions report problems in input order.
     */
    public static Map<String, String> ordered(String... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("keys and values must come in pairs");
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            map.put(keysAndValues[index], keysAndValues[index + 1]);
        }
        return map;
    }
}
