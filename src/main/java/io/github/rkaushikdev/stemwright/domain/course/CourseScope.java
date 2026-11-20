package io.github.rkaushikdev.stemwright.domain.course;

import java.util.Objects;

/**
 * The course an action touches, together with who is on it. Every permission that touches a course
 * is decided against one of these rather than against the account role alone, because the
 * relationship to the course is what changes from one course to the next.
 *
 * @param course the course being acted on
 * @param roster the enrolments of that course
 */
public record CourseScope(Course course, Roster roster) {

    public CourseScope {
        Objects.requireNonNull(course, "course");
        Objects.requireNonNull(roster, "roster");
    }
}
