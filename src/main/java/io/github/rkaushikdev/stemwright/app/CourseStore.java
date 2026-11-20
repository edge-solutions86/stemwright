package io.github.rkaushikdev.stemwright.app;

import io.github.rkaushikdev.stemwright.domain.course.Assignment;
import io.github.rkaushikdev.stemwright.domain.course.Course;
import io.github.rkaushikdev.stemwright.domain.course.Enrolment;
import java.util.List;
import java.util.Optional;

/**
 * The courses, keyed by course key, with each course's enrolments and assignments. A learner has at
 * most one enrolment on a course and an assignment key is unique within its course, so storing
 * either again replaces it.
 */
public interface CourseStore {

    Optional<Course> get(String key);

    /** Every course, by key as a reader sorts it. */
    List<Course> list();

    /** Stores the course under its key, in place of any course stored there before. */
    void put(Course entry);

    /** The enrolments of the course, the most recently stored last. */
    List<Enrolment> enrolments(String courseKey);

    /** Stores the enrolment on its course, in place of the learner's earlier enrolment there. */
    void putEnrolment(Enrolment enrolment);

    /** The assignments of the course, the most recently stored last. */
    List<Assignment> assignments(String courseKey);

    /** Stores the assignment on its course, in place of an earlier assignment with its key. */
    void putAssignment(Assignment entry);
}
