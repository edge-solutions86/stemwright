import { ValidationError } from '../errors.js';
import type { CivilDate } from '../time/civil.js';
import { addDays } from '../time/civil.js';
import type { Ladder } from './ladder.js';
import { clampRung, intervalAt } from './ladder.js';
import type { MasteryLedger } from './ledger.js';
import { strengthFor, withStrength } from './ledger.js';
import type { CellStrength } from './strength.js';
import { suspend } from './strength.js';

/**
 * A leech is a cell that keeps coming back wrong. Left alone it fills the
 * review queue and crowds out everything the learner could actually be
 * learning, so past a threshold something has to happen to it.
 *
 * What happens is a teaching decision rather than a technical one, which is why
 * there are three actions and none of them is the obvious default. Suspending
 * is right for a word nobody needs yet. Demoting is right when the learner met
 * it too early. Tagging is right when an instructor should look at whether the
 * paradigm itself is wrong, and it is the only action that changes nothing
 * about the schedule.
 */
export type LeechAction = 'suspend' | 'demote' | 'tag';

export interface LeechPolicy {
  readonly threshold: number;
  readonly action: LeechAction;
  /** Count only lapses since the last correct answer rather than all of them. */
  readonly consecutiveOnly: boolean;
  /** How many days a demoted cell waits before coming back. */
  readonly demotionRest: number;
}

export const DEFAULT_LEECH: LeechPolicy = {
  threshold: 6,
  action: 'suspend',
  consecutiveOnly: false,
  demotionRest: 3,
};

export function leechPolicy(patch: Partial<LeechPolicy> = {}): LeechPolicy {
  const policy = { ...DEFAULT_LEECH, ...patch };
  if (!Number.isInteger(policy.threshold) || policy.threshold < 1) {
    throw ValidationError.field(
      'threshold',
      `must be a whole number above zero: ${String(policy.threshold)}`,
    );
  }
  if (!Number.isInteger(policy.demotionRest) || policy.demotionRest < 0) {
    throw ValidationError.field(
      'demotionRest',
      `must not be negative: ${String(policy.demotionRest)}`,
    );
  }
  return policy;
}

/**
 * Consecutive counting needs a number the strength record does not keep, so it
 * is reconstructed: a streak above nought means the last answer was right, and
 * a cell whose last answer was right has no consecutive lapses at all.
 */
export function lapseCount(strength: CellStrength, policy: LeechPolicy): number {
  if (!policy.consecutiveOnly) {
    return strength.lapses;
  }
  return strength.streak > 0 ? 0 : strength.lapses;
}

export function isLeech(strength: CellStrength, policy: LeechPolicy): boolean {
  if (strength.suspended) {
    return false;
  }
  return lapseCount(strength, policy) >= policy.threshold;
}

export interface LeechOutcome {
  readonly cellId: string;
  readonly fired: boolean;
  readonly action: LeechAction | undefined;
  readonly lapses: number;
}

export function applyLeechPolicy(
  strength: CellStrength,
  policy: LeechPolicy,
  ladder: Ladder,
  on: CivilDate,
): { readonly strength: CellStrength; readonly outcome: LeechOutcome } {
  const lapses = lapseCount(strength, policy);
  if (!isLeech(strength, policy)) {
    return {
      strength,
      outcome: { cellId: strength.cellId, fired: false, action: undefined, lapses },
    };
  }

  const outcome: LeechOutcome = {
    cellId: strength.cellId,
    fired: true,
    action: policy.action,
    lapses,
  };

  switch (policy.action) {
    case 'suspend':
      return { strength: suspend(strength), outcome };
    case 'demote': {
      const rung = clampRung(ladder, 0);
      return {
        strength: {
          ...strength,
          rung,
          dueOn: addDays(on, Math.max(policy.demotionRest, intervalAt(ladder, rung))),
        },
        outcome,
      };
    }
    case 'tag':
      return { strength, outcome };
  }
}

export function leechesIn(
  ledger: MasteryLedger,
  cellIds: readonly string[],
  policy: LeechPolicy,
): CellStrength[] {
  return cellIds
    .map((cellId) => strengthFor(ledger, cellId))
    .filter((strength) => isLeech(strength, policy))
    .sort((a, b) => b.lapses - a.lapses || a.cellId.localeCompare(b.cellId));
}

export function sweepLeeches(
  ledger: MasteryLedger,
  cellIds: readonly string[],
  policy: LeechPolicy,
  ladder: Ladder,
  on: CivilDate,
): { readonly ledger: MasteryLedger; readonly outcomes: readonly LeechOutcome[] } {
  let next = ledger;
  const outcomes: LeechOutcome[] = [];
  for (const strength of leechesIn(ledger, cellIds, policy)) {
    const applied = applyLeechPolicy(strength, policy, ladder, on);
    outcomes.push(applied.outcome);
    if (applied.strength !== strength) {
      next = withStrength(next, applied.strength);
    }
  }
  return { ledger: next, outcomes };
}

export function describeLeech(policy: LeechPolicy): string {
  const counting = policy.consecutiveOnly ? 'consecutive lapses' : 'lapses';
  switch (policy.action) {
    case 'suspend':
      return `suspend a cell after ${policy.threshold} ${counting}`;
    case 'demote':
      return `send a cell back to the bottom rung after ${policy.threshold} ${counting}`;
    case 'tag':
      return `flag a cell for review after ${policy.threshold} ${counting} and change nothing`;
  }
}
