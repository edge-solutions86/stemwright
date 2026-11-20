package io.github.rkaushikdev.stemwright.domain.course;

import io.github.rkaushikdev.stemwright.domain.errors.ForbiddenException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Somebody acting on the desk, and what they may do. Two things decide it and they are checked in
 * that order: the account role, which is fixed, and the relationship to the course being acted on,
 * which is not.
 *
 * <p>An instructor of one course is a stranger to another, and the mistake that makes is the
 * expensive one, so every permission that touches a course takes a {@link CourseScope}, the course
 * and its roster, rather than trusting the role alone.
 *
 * <p>Every question comes in two forms, with a scope and without one. Two permissions are not about
 * a course at all, so the scope may be left out; asking about a course permission without a scope
 * answers no rather than throwing, so a caller that forgot the scope fails closed.
 *
 * @param id the account id
 * @param role the account role
 */
public record Actor(String id, AccountRole role) {

    public Actor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(role, "role");
    }

    /** Whether this actor teaches the course. A learner account never does, even when it is listed. */
    public boolean teachesCourse(CourseScope scope) {
        return role != AccountRole.LEARNER && scope.course().isInstructor(id);
    }

    /** Whether this actor is on the course's roster and has not withdrawn. */
    public boolean isEnrolled(CourseScope scope) {
        return scope.roster()
                .enrolmentOf(id)
                .filter(enrolment -> !enrolment.withdrawn())
                .isPresent();
    }

    /** {@link #can(Permission, CourseScope)} with no course in scope. */
    public boolean can(Permission permission) {
        return allows(permission, Optional.empty());
    }

    /**
     * Whether this actor may act. An admin may do anything. Account management is an admin's alone;
     * the lexicon and profiles are any instructor's; drilling and reading one's own progress belong
     * to the course's enrolled learners and its instructors; and the cohort, the decks and the course
     * itself belong to its instructors.
     */
    public boolean can(Permission permission, CourseScope scope) {
        return allows(permission, Optional.of(scope));
    }

    /** {@link #requirePermission(Permission, CourseScope)} with no course in scope. */
    public void requirePermission(Permission permission) {
        if (!can(permission)) {
            throw refusal(permission);
        }
    }

    /**
     * Passes silently when this actor may act.
     *
     * @throws ForbiddenException naming the actor and the permission when it may not
     */
    public void requirePermission(Permission permission, CourseScope scope) {
        if (!can(permission, scope)) {
            throw refusal(permission);
        }
    }

    /** {@link #canReadLearner(String, CourseScope)} with no course in scope. */
    public boolean canReadLearner(String learnerId) {
        return mayRead(learnerId, Optional.empty());
    }

    /**
     * Whether this actor may see another's work. A learner sees only their own, an instructor of the
     * course sees everybody on it, and an admin sees everything. Without a scope a learner still
     * sees their own work; with one, they see it only while they are on the course.
     */
    public boolean canReadLearner(String learnerId, CourseScope scope) {
        return mayRead(learnerId, Optional.of(scope));
    }

    /** {@link #requireReadLearner(String, CourseScope)} with no course in scope. */
    public void requireReadLearner(String learnerId) {
        if (!canReadLearner(learnerId)) {
            throw readRefusal(learnerId);
        }
    }

    /**
     * Passes silently when this actor may see the learner's work.
     *
     * @throws ForbiddenException naming both when it may not
     */
    public void requireReadLearner(String learnerId, CourseScope scope) {
        if (!canReadLearner(learnerId, scope)) {
            throw readRefusal(learnerId);
        }
    }

    /** {@link #visibleLearners(List, CourseScope)} with no course in scope. */
    public List<String> visibleLearners(List<String> learnerIds) {
        return learnerIds.stream().filter(this::canReadLearner).toList();
    }

    /** The learners whose work this actor may see, in the order given. */
    public List<String> visibleLearners(List<String> learnerIds, CourseScope scope) {
        return learnerIds.stream()
                .filter(learnerId -> canReadLearner(learnerId, scope))
                .toList();
    }

    /** {@link #permissions(CourseScope)} with no course in scope. */
    public List<Permission> permissions() {
        return Arrays.stream(Permission.values()).filter(this::can).toList();
    }

    /** Everything this actor may do on the course, in the order {@link Permission} declares them. */
    public List<Permission> permissions(CourseScope scope) {
        return Arrays.stream(Permission.values())
                .filter(permission -> can(permission, scope))
                .toList();
    }

    private boolean allows(Permission permission, Optional<CourseScope> scope) {
        if (role == AccountRole.ADMIN) {
            return true;
        }
        return switch (permission) {
            case MANAGE_ACCOUNTS -> false;
            case EDIT_LEXICON, PUBLISH_PROFILE -> role == AccountRole.INSTRUCTOR;
            case DRILL, READ_OWN_PROGRESS -> scope.filter(this::isOnCourse).isPresent();
            case READ_COHORT, EDIT_DECK, EDIT_COURSE ->
                scope.filter(this::teachesCourse).isPresent();
        };
    }

    private boolean mayRead(String learnerId, Optional<CourseScope> scope) {
        if (role == AccountRole.ADMIN) {
            return true;
        }
        if (id.equals(learnerId)) {
            return scope.map(this::isOnCourse).orElse(true);
        }
        return scope.filter(this::teachesCourse).isPresent();
    }

    /** Enrolled on the course or teaching it: what drilling it and reading one's own work on it need. */
    private boolean isOnCourse(CourseScope scope) {
        return isEnrolled(scope) || teachesCourse(scope);
    }

    private ForbiddenException refusal(Permission permission) {
        return new ForbiddenException(id + " may not " + permission.key());
    }

    private ForbiddenException readRefusal(String learnerId) {
        return new ForbiddenException(id + " may not read the work of " + learnerId);
    }
}
