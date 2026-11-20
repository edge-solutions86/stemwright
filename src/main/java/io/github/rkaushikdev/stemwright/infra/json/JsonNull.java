package io.github.rkaushikdev.stemwright.infra.json;

/**
 * The json {@code null}. There is exactly one, {@link Json#NULL}.
 *
 * <p>It is a value like any other, which is the point: a field that holds {@code null} is present,
 * and a decoder that has to tell a missing field from a null one (a journal entry's payload may be
 * null, but it may not be missing) can.
 */
public enum JsonNull implements Json {
    INSTANCE;

    /** The json text, {@code null}. */
    @Override
    public String toString() {
        return "null";
    }
}
