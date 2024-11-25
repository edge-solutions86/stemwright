import { ForbiddenError } from '../errors.js';
import type { Course } from './course.js';
import { isInstructor } from './course.js';
import type { Roster } from './enrolment.js';
import { enrolmentOf } from './enrolment.js';

/**
 * Who may do what. Two things decide it and they are checked in that order: the
 * account role, which is fixed, and the relationship to the course being acted
 * on, which is not.
 *
 * An instructor of one course is a stranger to another, and the mistake that
 * makes is the expensive one, so every permission that touches a course takes
 * the course and the roster rather than trusting the role alone.
 */
export type AccountRole = 'learner' | 'instructor' | 'admin';

export interface Actor {
  readonly id: string;
  readonly role: AccountRole;
}

export type Permission =
  | 'drill'
  | 'read-own-progress'
  | 'read-cohort'
  | 'edit-deck'
  | 'edit-course'
  | 'edit-lexicon'
  | 'publish-profile'
  | 'manage-accounts';

export interface CourseScope {
  readonly course: Course;
  readonly roster: Roster;
}

const ROLES: readonly AccountRole[] = ['learner', 'instructor', 'admin'];

export function isAccountRole(value: string): value is AccountRole {
  return (ROLES as readonly string[]).includes(value);
}

export function teachesCourse(actor: Actor, scope: CourseScope): boolean {
  return actor.role !== 'learner' && isInstructor(scope.course, actor.id);
}

export function isEnrolled(actor: Actor, scope: CourseScope): boolean {
  const enrolment = enrolmentOf(scope.roster, actor.id);
  return enrolment !== undefined && !enrolment.withdrawn;
}

/**
 * The scope is optional because two permissions are not about a course at all.
 * Asking about a course permission without a scope answers no rather than
 * throwing, so a caller that forgot the scope fails closed.
 */
export function can(actor: Actor, permission: Permission, scope?: CourseScope): boolean {
  if (actor.role === 'admin') {
    return true;
  }

  switch (permission) {
    case 'manage-accounts':
      return false;
    case 'edit-lexicon':
    case 'publish-profile':
      return actor.role === 'instructor';
    case 'drill':
    case 'read-own-progress':
      return scope === undefined ? false : isEnrolled(actor, scope) || teachesCourse(actor, scope);
    case 'read-cohort':
    case 'edit-deck':
    case 'edit-course':
      return scope === undefined ? false : teachesCourse(actor, scope);
  }
}

export function require(actor: Actor, permission: Permission, scope?: CourseScope): void {
  if (!can(actor, permission, scope)) {
    throw new ForbiddenError(`${actor.id} may not ${permission}`);
  }
}

/**
 * Whether one actor may see another's work. A learner sees only their own, an
 * instructor of the course sees everybody on it, and an admin sees everything.
 */
export function canReadLearner(actor: Actor, learnerId: string, scope?: CourseScope): boolean {
  if (actor.role === 'admin') {
    return true;
  }
  if (actor.id === learnerId) {
    return scope === undefined ? true : isEnrolled(actor, scope) || teachesCourse(actor, scope);
  }
  return scope === undefined ? false : teachesCourse(actor, scope);
}

export function requireReadLearner(actor: Actor, learnerId: string, scope?: CourseScope): void {
  if (!canReadLearner(actor, learnerId, scope)) {
    throw new ForbiddenError(`${actor.id} may not read the work of ${learnerId}`);
  }
}

export function visibleLearners(
  actor: Actor,
  learnerIds: readonly string[],
  scope?: CourseScope,
): string[] {
  return learnerIds.filter((learnerId) => canReadLearner(actor, learnerId, scope));
}

export function describePermissions(actor: Actor, scope?: CourseScope): Permission[] {
  const every: Permission[] = [
    'drill',
    'read-own-progress',
    'read-cohort',
    'edit-deck',
    'edit-course',
    'edit-lexicon',
    'publish-profile',
    'manage-accounts',
  ];
  return every.filter((permission) => can(actor, permission, scope));
}
