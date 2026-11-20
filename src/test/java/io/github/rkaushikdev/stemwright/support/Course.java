package io.github.rkaushikdev.stemwright.support;

import io.github.rkaushikdev.stemwright.domain.course.CourseInput;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;

/**
 * The course the course, enrolment, permission and report tests share.
 *
 * <p>This class has the same simple name as the domain type it holds, so a test reaches the fixture
 * through {@code import static io.github.rkaushikdev.stemwright.support.Course.MICHAELMAS} rather
 * than by importing the class.
 */
public final class Course {

    private Course() {}

    /**
     * Latin 1A, the autumn term of 2024 from the seventh of October to the thirteenth of December,
     * teaching the decks {@code all} and {@code core}, with {@code lrn_teacher} as its one instructor.
     */
    public static final io.github.rkaushikdev.stemwright.domain.course.Course MICHAELMAS =
            io.github.rkaushikdev.stemwright.domain.course.Course.of(CourseInput.builder(
                            "latin-1a",
                            "latin",
                            CivilDates.parseCivilDate("2024-10-07"),
                            CivilDates.parseCivilDate("2024-12-13"))
                    .title("Latin 1A")
                    .deckKeys("all", "core")
                    .instructorIds("lrn_teacher")
                    .build());
}
