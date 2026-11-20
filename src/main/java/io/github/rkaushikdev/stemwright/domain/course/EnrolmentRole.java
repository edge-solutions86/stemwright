package io.github.rkaushikdev.stemwright.domain.course;

import java.util.Arrays;
import java.util.Optional;

/** What somebody is on a course for. Only a learner is graded. */
public enum EnrolmentRole {
    /** Drills and is graded. */
    LEARNER("learner"),
    /** Drills and is not graded. */
    AUDITOR("auditor"),
    /** Helps teach the course. */
    ASSISTANT("assistant");

    private final String key;

    EnrolmentRole(String key) {
        this.key = key;
    }

    /** The lower case name a roster and a request use, such as {@code auditor}. */
    public String key() {
        return key;
    }

    public static Optional<EnrolmentRole> fromKey(String key) {
        return Arrays.stream(values()).filter(role -> role.key.equals(key)).findFirst();
    }

    /** True for exactly the keys of the three roles, compared as written. */
    public static boolean isEnrolmentRole(String value) {
        return fromKey(value).isPresent();
    }
}
