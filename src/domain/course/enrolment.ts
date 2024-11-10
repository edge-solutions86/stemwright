import { ConflictError, ProblemCollector } from '../errors.js';
import type { DrillPolicyPatch } from '../drill/policy.js';
import type { ShieldSet } from '../mastery/shield.js';
import { NO_SHIELDS } from '../mastery/shield.js';
import type { CivilDate } from '../time/civil.js';
import type { DayRange } from '../time/window.js';
import { dayRange, intersectRanges, rangeContains } from '../time/window.js';
import type { Course } from './course.js';

/**
 * A learner's membership of a course. It carries a window of its own, because
 * people join late and leave early, and everything the learner is measured on
 * is measured inside that window rather than the course term.
 *
 * The window is clipped to the term rather than checked against it. Somebody
 * enrolled from a date before the course starts is enrolled from the first day
 * of the course, which is what the registry means and what an error message
 * about it would only be pedantry about.
 */
export type EnrolmentRole = 'learner' | 'auditor' | 'assistant';

export interface Enrolment {
  readonly courseKey: string;
  readonly learnerId: string;
  readonly role: EnrolmentRole;
  readonly window: DayRange;
  readonly shields: ShieldSet;
  readonly policyPatch: DrillPolicyPatch;
  readonly withdrawn: boolean;
}

export interface EnrolmentInput {
  readonly learnerId: string;
  readonly role?: EnrolmentRole;
  readonly from?: CivilDate;
  readonly to?: CivilDate;
  readonly shields?: ShieldSet;
  readonly policyPatch?: DrillPolicyPatch;
}

const ROLES: readonly EnrolmentRole[] = ['learner', 'auditor', 'assistant'];

export function isEnrolmentRole(value: string): value is EnrolmentRole {
  return (ROLES as readonly string[]).includes(value);
}

export function enrol(entry: Course, input: EnrolmentInput): Enrolment {
  const problems = new ProblemCollector();
  const role = input.role ?? 'learner';
  if (!isEnrolmentRole(role)) {
    problems.add('role', `is not an enrolment role: ${String(role)}`);
  }

  const asked = problems.absorbFlat('window', () =>
    dayRange(input.from ?? entry.term.from, input.to ?? entry.term.to),
  );
  const clipped = asked === undefined ? undefined : intersectRanges(asked, entry.term);
  if (asked !== undefined && clipped === undefined) {
    problems.add('window', `lies entirely outside the term ${entry.term.from} to ${entry.term.to}`);
  }

  problems.throwIfAny(`enrolment of ${input.learnerId} is not usable`);

  return {
    courseKey: entry.key,
    learnerId: input.learnerId,
    role,
    window: clipped!,
    shields: input.shields ?? NO_SHIELDS,
    policyPatch: input.policyPatch ?? {},
    withdrawn: false,
  };
}

export function isActiveOn(enrolment: Enrolment, on: CivilDate): boolean {
  return !enrolment.withdrawn && rangeContains(enrolment.window, on);
}

export function withdraw(enrolment: Enrolment): Enrolment {
  if (enrolment.withdrawn) {
    throw new ConflictError(
      `${enrolment.learnerId} has already withdrawn from ${enrolment.courseKey}`,
    );
  }
  return { ...enrolment, withdrawn: true };
}

export function reinstate(enrolment: Enrolment): Enrolment {
  return enrolment.withdrawn ? { ...enrolment, withdrawn: false } : enrolment;
}

export function withShields(enrolment: Enrolment, shields: ShieldSet): Enrolment {
  return { ...enrolment, shields };
}

export function withPolicyPatch(enrolment: Enrolment, patch: DrillPolicyPatch): Enrolment {
  return { ...enrolment, policyPatch: { ...enrolment.policyPatch, ...patch } };
}

/** Only a learner is graded. An auditor drills and an assistant helps teach. */
export function isGraded(enrolment: Enrolment): boolean {
  return enrolment.role === 'learner' && !enrolment.withdrawn;
}

export interface Roster {
  readonly courseKey: string;
  readonly enrolments: readonly Enrolment[];
}

export function roster(entry: Course, enrolments: readonly Enrolment[]): Roster {
  const problems = new ProblemCollector();
  const seen = new Set<string>();
  for (const enrolment of enrolments) {
    if (enrolment.courseKey !== entry.key) {
      problems.add(enrolment.learnerId, `is enrolled on ${enrolment.courseKey}, not ${entry.key}`);
      continue;
    }
    if (seen.has(enrolment.learnerId)) {
      problems.add(enrolment.learnerId, 'is enrolled twice');
    }
    seen.add(enrolment.learnerId);
  }
  problems.throwIfAny(`roster for ${entry.key} is not usable`);
  return { courseKey: entry.key, enrolments };
}

export function enrolmentOf(list: Roster, learnerId: string): Enrolment | undefined {
  return list.enrolments.find((enrolment) => enrolment.learnerId === learnerId);
}

export function activeOn(list: Roster, on: CivilDate): Enrolment[] {
  return list.enrolments.filter((enrolment) => isActiveOn(enrolment, on));
}

export function gradedLearners(list: Roster): string[] {
  return list.enrolments
    .filter(isGraded)
    .map((enrolment) => enrolment.learnerId)
    .sort();
}
