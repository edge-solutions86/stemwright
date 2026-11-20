package io.github.rkaushikdev.stemwright.domain.drill;

import java.util.Arrays;
import java.util.Optional;

/** What to do about the open session when a learner comes back to a deck. */
public enum ResumeAction {
    /** Carry on in the open session, which is from today. */
    RESUME("resume"),
    /** Close the open session, which is from another day, and start a new one. */
    CLOSE_AND_OPEN("close-and-open"),
    /** Start a new session, because none is open. */
    OPEN("open");

    private final String key;

    ResumeAction(String key) {
        this.key = key;
    }

    /** The action as it is printed, such as {@code close-and-open}. */
    public String key() {
        return key;
    }

    public static Optional<ResumeAction> fromKey(String key) {
        return Arrays.stream(values()).filter(action -> action.key.equals(key)).findFirst();
    }
}
