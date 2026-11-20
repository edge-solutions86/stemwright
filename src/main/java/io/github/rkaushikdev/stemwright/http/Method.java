package io.github.rkaushikdev.stemwright.http;

import java.util.Arrays;
import java.util.Optional;

/** The four request methods the api answers. Anything else is refused before it reaches a route. */
public enum Method {
    GET,
    POST,
    PUT,
    DELETE;

    /** The method as a request line writes it, such as {@code GET}. */
    public String key() {
        return name();
    }

    /** The method a request line names, or nothing for one the api does not answer. */
    public static Optional<Method> fromKey(String key) {
        return Arrays.stream(values())
                .filter(method -> method.name().equals(key))
                .findFirst();
    }
}
