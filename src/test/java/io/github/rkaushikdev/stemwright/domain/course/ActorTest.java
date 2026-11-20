package io.github.rkaushikdev.stemwright.domain.course;

import static io.github.rkaushikdev.stemwright.support.Course.MICHAELMAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.errors.ForbiddenException;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ActorTest {

    private static final Actor STUDENT = new Actor("lrn_one", AccountRole.LEARNER);
    private static final Actor TEACHER = new Actor("lrn_teacher", AccountRole.INSTRUCTOR);
    private static final Actor STRANGER = new Actor("lrn_other", AccountRole.INSTRUCTOR);
    private static final Actor ADMIN = new Actor("lrn_admin", AccountRole.ADMIN);

    private static final CourseScope SCOPE = new CourseScope(
            MICHAELMAS,
            Roster.of(
                    MICHAELMAS,
                    List.of(
                            Enrolment.of(MICHAELMAS, EnrolmentInput.of("lrn_one")),
                            Enrolment.of(MICHAELMAS, EnrolmentInput.of("lrn_two")),
                            Enrolment.of(MICHAELMAS, EnrolmentInput.of("lrn_three"))
                                    .withdraw())));

    private static final Course OTHER_COURSE = Course.of(CourseInput.builder(
                    "greek-2",
                    "greek",
                    CivilDates.parseCivilDate("2025-01-06"),
                    CivilDates.parseCivilDate("2025-03-14"))
            .instructorIds("lrn_other")
            .build());
    private static final CourseScope OTHER_SCOPE = new CourseScope(OTHER_COURSE, Roster.of(OTHER_COURSE, List.of()));

    @Nested
    class IsAccountRole {

        @Test
        void acceptsTheThreeRolesAndNothingElse() {
            assertThat(AccountRole.isAccountRole("learner")).isTrue();
            assertThat(AccountRole.isAccountRole("instructor")).isTrue();
            assertThat(AccountRole.isAccountRole("admin")).isTrue();
            assertThat(AccountRole.isAccountRole("tutor")).isFalse();
        }
    }

    @Nested
    class TeachesCourseAndIsEnrolled {

        @Test
        void recognisesTheInstructorOfThisCourse() {
            assertThat(TEACHER.teachesCourse(SCOPE)).isTrue();
            assertThat(STRANGER.teachesCourse(SCOPE)).isFalse();
        }

        @Test
        void doesNotLetALearnerAccountTeachEvenIfItIsListed() {
            Actor listed = new Actor("lrn_teacher", AccountRole.LEARNER);
            assertThat(listed.teachesCourse(SCOPE)).isFalse();
        }

        @Test
        void recognisesAnActiveEnrolmentAndNotAWithdrawnOne() {
            assertThat(STUDENT.isEnrolled(SCOPE)).isTrue();
            assertThat(new Actor("lrn_three", AccountRole.LEARNER).isEnrolled(SCOPE))
                    .isFalse();
            assertThat(new Actor("lrn_nine", AccountRole.LEARNER).isEnrolled(SCOPE))
                    .isFalse();
        }
    }

    @Nested
    class Can {

        @Test
        void letsAnEnrolledLearnerDrillAndReadTheirOwnProgress() {
            assertThat(STUDENT.can(Permission.DRILL, SCOPE)).isTrue();
            assertThat(STUDENT.can(Permission.READ_OWN_PROGRESS, SCOPE)).isTrue();
        }

        @Test
        void doesNotLetALearnerReadTheCohortOrEditAnything() {
            assertThat(STUDENT.can(Permission.READ_COHORT, SCOPE)).isFalse();
            assertThat(STUDENT.can(Permission.EDIT_DECK, SCOPE)).isFalse();
            assertThat(STUDENT.can(Permission.EDIT_LEXICON)).isFalse();
        }

        @Test
        void letsTheInstructorOfTheCourseReadTheCohortAndEditIt() {
            assertThat(TEACHER.can(Permission.READ_COHORT, SCOPE)).isTrue();
            assertThat(TEACHER.can(Permission.EDIT_COURSE, SCOPE)).isTrue();
        }

        @Test
        void doesNotLetAnInstructorOfAnotherCourseTouchThisOne() {
            assertThat(STRANGER.can(Permission.READ_COHORT, SCOPE)).isFalse();
            assertThat(STRANGER.can(Permission.EDIT_COURSE, SCOPE)).isFalse();
            assertThat(STRANGER.can(Permission.READ_COHORT, OTHER_SCOPE)).isTrue();
        }

        @Test
        void letsAnyInstructorEditTheLexiconAndPublishAProfile() {
            assertThat(STRANGER.can(Permission.EDIT_LEXICON)).isTrue();
            assertThat(STRANGER.can(Permission.PUBLISH_PROFILE)).isTrue();
        }

        @Test
        void failsClosedWhenACoursePermissionIsAskedWithoutAScope() {
            assertThat(TEACHER.can(Permission.READ_COHORT)).isFalse();
            assertThat(STUDENT.can(Permission.DRILL)).isFalse();
        }

        @Test
        void givesAnAdminEverything() {
            assertThat(ADMIN.can(Permission.MANAGE_ACCOUNTS)).isTrue();
            assertThat(ADMIN.can(Permission.EDIT_COURSE, SCOPE)).isTrue();
            assertThat(ADMIN.can(Permission.READ_COHORT)).isTrue();
        }

        @Test
        void givesNobodyButAnAdminAccountManagement() {
            assertThat(TEACHER.can(Permission.MANAGE_ACCOUNTS, SCOPE)).isFalse();
        }
    }

    @Nested
    class RequirePermission {

        @Test
        void passesSilentlyWhenTheActorMayAct() {
            assertThatCode(() -> TEACHER.requirePermission(Permission.READ_COHORT, SCOPE))
                    .doesNotThrowAnyException();
        }

        @Test
        void namesTheActorAndThePermissionWhenItRefuses() {
            assertThatThrownBy(() -> STUDENT.requirePermission(Permission.READ_COHORT, SCOPE))
                    .isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> STUDENT.requirePermission(Permission.READ_COHORT, SCOPE))
                    .hasMessageContaining("lrn_one may not read-cohort");
        }
    }

    @Nested
    class CanReadLearner {

        @Test
        void letsALearnerReadTheirOwnWork() {
            assertThat(STUDENT.canReadLearner("lrn_one", SCOPE)).isTrue();
        }

        @Test
        void doesNotLetALearnerReadSomebodyElse() {
            assertThat(STUDENT.canReadLearner("lrn_two", SCOPE)).isFalse();
        }

        @Test
        void letsTheInstructorReadAnybodyOnTheCourse() {
            assertThat(TEACHER.canReadLearner("lrn_two", SCOPE)).isTrue();
        }

        @Test
        void doesNotLetAnInstructorOfAnotherCourseReadThem() {
            assertThat(STRANGER.canReadLearner("lrn_two", SCOPE)).isFalse();
        }

        @Test
        void letsAnAdminReadAnybodyWithOrWithoutAScope() {
            assertThat(ADMIN.canReadLearner("lrn_two")).isTrue();
            assertThat(ADMIN.canReadLearner("lrn_two", SCOPE)).isTrue();
        }

        @Test
        void letsALearnerReadTheirOwnWorkWithNoScopeAtAll() {
            assertThat(STUDENT.canReadLearner("lrn_one")).isTrue();
        }

        @Test
        void refusesALearnerWhoHasWithdrawnFromTheCourse() {
            Actor gone = new Actor("lrn_three", AccountRole.LEARNER);
            assertThat(gone.canReadLearner("lrn_three", SCOPE)).isFalse();
        }

        @Test
        void throwsWithBothNamesWhenACallerDemandsAccess() {
            assertThatThrownBy(() -> STUDENT.requireReadLearner("lrn_two", SCOPE))
                    .hasMessageContaining("lrn_one may not read the work of lrn_two");
        }
    }

    @Nested
    class VisibleLearners {

        @Test
        void narrowsAListToWhatTheActorMaySee() {
            assertThat(STUDENT.visibleLearners(List.of("lrn_one", "lrn_two"), SCOPE))
                    .containsExactly("lrn_one");
            assertThat(TEACHER.visibleLearners(List.of("lrn_one", "lrn_two"), SCOPE))
                    .containsExactly("lrn_one", "lrn_two");
        }

        @Test
        void returnsNothingForAnInstructorOfAnotherCourse() {
            assertThat(STRANGER.visibleLearners(List.of("lrn_one"), SCOPE)).isEmpty();
        }
    }

    @Nested
    class Permissions {

        @Test
        void listsWhatALearnerMayDoOnTheirCourse() {
            assertThat(STUDENT.permissions(SCOPE)).containsExactly(Permission.DRILL, Permission.READ_OWN_PROGRESS);
        }

        @Test
        void listsWhatAnInstructorMayDo() {
            assertThat(TEACHER.permissions(SCOPE).stream().map(Permission::key))
                    .containsExactly(
                            "drill",
                            "read-own-progress",
                            "read-cohort",
                            "edit-deck",
                            "edit-course",
                            "edit-lexicon",
                            "publish-profile");
        }

        @Test
        void listsEverythingForAnAdmin() {
            assertThat(ADMIN.permissions(SCOPE)).hasSize(8);
        }
    }
}
