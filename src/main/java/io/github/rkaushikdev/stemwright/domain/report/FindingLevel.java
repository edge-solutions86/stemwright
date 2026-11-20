package io.github.rkaushikdev.stemwright.domain.report;

import java.util.Arrays;
import java.util.Optional;

/**
 * How serious a finding of the health check is. The constants are declared worst first, and that
 * order is the order a report lists them in.
 */
public enum FindingLevel {
    /** Something that stops a class drilling today. */
    ERROR("error"),
    /** Something wrong that has not stopped anything yet. */
    WARNING("warning"),
    /** Something an editor would want to know, which may be fine. */
    NOTE("note");

    private final String key;

    FindingLevel(String key) {
        this.key = key;
    }

    /** The level as it is printed, such as {@code warning}. */
    public String key() {
        return key;
    }

    public static Optional<FindingLevel> fromKey(String key) {
        return Arrays.stream(values()).filter(level -> level.key.equals(key)).findFirst();
    }
}
