import { ProblemCollector } from '../errors.js';
import { asSlug } from '../identifiers.js';

/**
 * Everything about how much drilling happens and what is allowed to appear
 * beside what. Policies are layered: the language has a default, a deck may
 * override parts of it, and an assignment may override parts again. Each layer
 * is a patch rather than a replacement, so a deck that only wants to change the
 * new cell budget does not silently reset the leech threshold.
 */
export interface DrillPolicy {
  /** Cells never seen before that may be introduced in one day. */
  readonly newPerDay: number;
  /** Cells due for review that may be asked in one day. */
  readonly reviewsPerDay: number;
  /** How many cells of one lexeme may appear in a single session. */
  readonly perLexemeCap: number;
  /** How many other questions must separate two cells of the same paradigm row. */
  readonly rowSpacing: number;
  /** Whether answering a cell buries its syncretic twins for the rest of the day. */
  readonly buryTwins: boolean;
  /** Lapses before a cell is suspended as a leech. */
  readonly leechThreshold: number;
  /** Which interval ladder the strength model steps along. */
  readonly ladderKey: string;
  /** Weekdays with no drilling at all, as iso numbers where monday is one. */
  readonly restDays: readonly number[];
}

export type DrillPolicyPatch = Partial<{
  newPerDay: number;
  reviewsPerDay: number;
  perLexemeCap: number;
  rowSpacing: number;
  buryTwins: boolean;
  leechThreshold: number;
  ladderKey: string;
  restDays: readonly number[];
}>;

export const DEFAULT_POLICY: DrillPolicy = {
  newPerDay: 10,
  reviewsPerDay: 60,
  perLexemeCap: 3,
  rowSpacing: 2,
  buryTwins: true,
  leechThreshold: 6,
  ladderKey: 'standard',
  restDays: [],
};

function checkCount(problems: ProblemCollector, path: string, value: number, max: number): void {
  if (!Number.isInteger(value)) {
    problems.add(path, `must be a whole number: ${String(value)}`);
    return;
  }
  if (value < 0) {
    problems.add(path, `must not be negative: ${String(value)}`);
    return;
  }
  if (value > max) {
    problems.add(path, `must be at most ${max}: ${String(value)}`);
  }
}

export function drillPolicy(patch: DrillPolicyPatch = {}): DrillPolicy {
  return applyPolicyPatch(DEFAULT_POLICY, patch);
}

/**
 * Applies a patch and validates the result rather than the patch. A partial
 * override can only be judged against what it is overriding: a `perLexemeCap`
 * of nine is fine on its own and wrong beside a `newPerDay` of four.
 */
export function applyPolicyPatch(base: DrillPolicy, patch: DrillPolicyPatch): DrillPolicy {
  const problems = new ProblemCollector();
  const merged: DrillPolicy = {
    newPerDay: patch.newPerDay ?? base.newPerDay,
    reviewsPerDay: patch.reviewsPerDay ?? base.reviewsPerDay,
    perLexemeCap: patch.perLexemeCap ?? base.perLexemeCap,
    rowSpacing: patch.rowSpacing ?? base.rowSpacing,
    buryTwins: patch.buryTwins ?? base.buryTwins,
    leechThreshold: patch.leechThreshold ?? base.leechThreshold,
    ladderKey:
      patch.ladderKey === undefined ? base.ladderKey : asSlug('ladderKey', patch.ladderKey),
    restDays: [...new Set(patch.restDays ?? base.restDays)].sort((a, b) => a - b),
  };

  checkCount(problems, 'newPerDay', merged.newPerDay, 500);
  checkCount(problems, 'reviewsPerDay', merged.reviewsPerDay, 2000);
  checkCount(problems, 'perLexemeCap', merged.perLexemeCap, 200);
  checkCount(problems, 'rowSpacing', merged.rowSpacing, 50);
  checkCount(problems, 'leechThreshold', merged.leechThreshold, 100);

  for (const day of merged.restDays) {
    if (!Number.isInteger(day) || day < 1 || day > 7) {
      problems.add('restDays', `is not an iso weekday: ${String(day)}`);
    }
  }
  if (merged.restDays.length === 7) {
    problems.add('restDays', 'would rest every day of the week');
  }
  if (merged.newPerDay === 0 && merged.reviewsPerDay === 0) {
    problems.add('newPerDay', 'is zero and so is reviewsPerDay, which asks for no drilling at all');
  }

  problems.throwIfAny('drill policy is not usable');
  return merged;
}

export function policyAllowsDay(policy: DrillPolicy, isoWeekday: number): boolean {
  return !policy.restDays.includes(isoWeekday);
}

export function dailyCeiling(policy: DrillPolicy): number {
  return policy.newPerDay + policy.reviewsPerDay;
}

export function describePolicy(policy: DrillPolicy): string {
  const parts = [
    `up to ${policy.newPerDay} new and ${policy.reviewsPerDay} review cells a day`,
    `at most ${policy.perLexemeCap} cells of one word in a session`,
  ];
  if (policy.rowSpacing > 0) {
    parts.push(`${policy.rowSpacing} questions between cells of the same row`);
  }
  if (policy.buryTwins) {
    parts.push('twins buried after an answer');
  }
  if (policy.restDays.length > 0) {
    parts.push(`resting on weekdays ${policy.restDays.join(' and ')}`);
  }
  return parts.join(', ');
}

/** True when the second policy asks for at least as much work as the first. */
export function isAtLeastAsDemanding(base: DrillPolicy, other: DrillPolicy): boolean {
  return (
    other.newPerDay >= base.newPerDay &&
    other.reviewsPerDay >= base.reviewsPerDay &&
    other.restDays.length <= base.restDays.length
  );
}
