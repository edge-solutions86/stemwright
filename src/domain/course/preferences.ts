import { ProblemCollector } from '../errors.js';
import type { DrillPolicy, DrillPolicyPatch } from '../drill/policy.js';
import { applyPolicyPatch } from '../drill/policy.js';
import { asSlug } from '../identifiers.js';
import type { Enrolment } from './enrolment.js';

/**
 * What a learner has asked for, and where it sits in the order.
 *
 * Four layers set drilling policy and they are not equal. The institution sets
 * a default, a course tightens it for a cohort, an enrolment adjusts it for one
 * person, and the learner asks for the rest. A learner may always ask for less
 * work than the layer above allows and never for more, because an assignment
 * that can be widened by the person being assessed is not an assignment.
 */
export interface LearnerPreferences {
  readonly learnerId: string;
  readonly patch: DrillPolicyPatch;
  readonly toleranceLevel: string | undefined;
  readonly ladderKey: string | undefined;
}

export interface PreferencesInput {
  readonly learnerId: string;
  readonly patch?: DrillPolicyPatch;
  readonly toleranceLevel?: string;
  readonly ladderKey?: string;
}

export function learnerPreferences(input: PreferencesInput): LearnerPreferences {
  const problems = new ProblemCollector();
  const patch = input.patch ?? {};

  if (patch.restDays !== undefined && patch.restDays.length === 7) {
    problems.add('patch.restDays', 'would rest every day of the week');
  }
  problems.throwIfAny(`preferences for ${input.learnerId} are not usable`);

  return {
    learnerId: input.learnerId,
    patch,
    toleranceLevel:
      input.toleranceLevel === undefined
        ? undefined
        : asSlug('toleranceLevel', input.toleranceLevel),
    ladderKey: input.ladderKey === undefined ? undefined : asSlug('ladderKey', input.ladderKey),
  };
}

export function noPreferences(learnerId: string): LearnerPreferences {
  return { learnerId, patch: {}, toleranceLevel: undefined, ladderKey: undefined };
}

/**
 * Trims a patch so it can only reduce the budgets of what it is applied to. A
 * learner asking for more new cells a day than the course allows gets the
 * course number, and the request is reported rather than silently dropped.
 */
export interface ClampedPatch {
  readonly patch: DrillPolicyPatch;
  readonly refused: readonly string[];
}

export function clampToCeiling(base: DrillPolicy, patch: DrillPolicyPatch): ClampedPatch {
  const refused: string[] = [];
  const clamped: DrillPolicyPatch = { ...patch };

  if (patch.newPerDay !== undefined && patch.newPerDay > base.newPerDay) {
    refused.push('newPerDay');
    delete clamped.newPerDay;
  }
  if (patch.reviewsPerDay !== undefined && patch.reviewsPerDay > base.reviewsPerDay) {
    refused.push('reviewsPerDay');
    delete clamped.reviewsPerDay;
  }
  if (patch.perLexemeCap !== undefined && patch.perLexemeCap > base.perLexemeCap) {
    refused.push('perLexemeCap');
    delete clamped.perLexemeCap;
  }

  return { patch: clamped, refused };
}

export interface PolicyResolution {
  readonly policy: DrillPolicy;
  readonly refused: readonly string[];
}

/**
 * Applies the layers in order and reports what the learner asked for and did
 * not get. The enrolment patch is not clamped: it was set by an instructor and
 * is allowed to raise as well as lower.
 */
export function resolvePolicyFor(
  base: DrillPolicy,
  coursePatch: DrillPolicyPatch,
  enrolment: Enrolment | undefined,
  preferences: LearnerPreferences,
): PolicyResolution {
  const withCourse = applyPolicyPatch(base, coursePatch);
  const withEnrolment = applyPolicyPatch(withCourse, enrolment?.policyPatch ?? {});
  const clamped = clampToCeiling(withEnrolment, preferences.patch);
  return { policy: applyPolicyPatch(withEnrolment, clamped.patch), refused: clamped.refused };
}

export function describeRefusals(resolution: PolicyResolution): string {
  if (resolution.refused.length === 0) {
    return 'every preference was applied';
  }
  return `${resolution.refused.join(', ')} were above the ceiling and were left alone`;
}

export function withPatch(
  preferences: LearnerPreferences,
  patch: DrillPolicyPatch,
): LearnerPreferences {
  return learnerPreferences({
    learnerId: preferences.learnerId,
    patch: { ...preferences.patch, ...patch },
    ...(preferences.toleranceLevel === undefined
      ? {}
      : { toleranceLevel: preferences.toleranceLevel }),
    ...(preferences.ladderKey === undefined ? {} : { ladderKey: preferences.ladderKey }),
  });
}
