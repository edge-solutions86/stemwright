import { ValidationError } from '../errors.js';
import type { Cell } from '../paradigm/cell.js';
import type { CivilDate } from '../time/civil.js';
import { addDays, compareCivil } from '../time/civil.js';
import type { Ladder } from './ladder.js';
import { climb, intervalAt } from './ladder.js';
import type { MasteryLedger } from './ledger.js';
import { strengthFor, withStrengths } from './ledger.js';
import type { CellStrength } from './strength.js';
import { isNew } from './strength.js';

/**
 * When a learner answers a cell, its syncretic twins have been answered too, in
 * every sense except that nobody asked them. A drill that ignores this asks the
 * same string three times over and reports three separate weaknesses.
 *
 * Credit is discounted rather than full. A learner who typed `puellae` for the
 * genitive singular has shown they know that form; they have not shown they
 * know it is also the nominative plural. So the twin moves, but less far, and
 * a twin that has never been introduced does not move at all, because there is
 * nothing yet to move.
 */
export interface CreditRule {
  /** Fraction of a full climb a twin receives, nought to one. */
  readonly share: number;
  /** Whether a twin may climb a rung, or only have its due date pushed out. */
  readonly allowClimb: boolean;
  /** Whether a twin the learner has never met is introduced by the credit. */
  readonly introduces: boolean;
}

export const DEFAULT_CREDIT: CreditRule = { share: 0.5, allowClimb: true, introduces: false };

export const NO_CREDIT: CreditRule = { share: 0, allowClimb: false, introduces: false };

export function creditRule(patch: Partial<CreditRule> = {}): CreditRule {
  const rule = { ...DEFAULT_CREDIT, ...patch };
  if (!Number.isFinite(rule.share) || rule.share < 0 || rule.share > 1) {
    throw ValidationError.field('share', `must be between nought and one: ${String(rule.share)}`);
  }
  return rule;
}

export interface CreditOutcome {
  readonly cellId: string;
  readonly moved: boolean;
  readonly climbed: boolean;
  readonly from: CivilDate | undefined;
  readonly to: CivilDate | undefined;
}

/**
 * Works out what one twin gets. It never pulls a due date forward: a twin
 * already scheduled further out than the credit would place it keeps the later
 * date, because credit is a reward and moving a review closer is not one.
 */
export function creditFor(
  strength: CellStrength,
  ladder: Ladder,
  rule: CreditRule,
  on: CivilDate,
): { readonly strength: CellStrength; readonly outcome: CreditOutcome } {
  const unchanged: CreditOutcome = {
    cellId: strength.cellId,
    moved: false,
    climbed: false,
    from: strength.dueOn,
    to: strength.dueOn,
  };

  if (strength.suspended || rule.share === 0) {
    return { strength, outcome: unchanged };
  }
  if (isNew(strength) && !rule.introduces) {
    return { strength, outcome: unchanged };
  }

  const rung = rule.allowClimb && !isNew(strength) ? climb(ladder, strength.rung) : strength.rung;
  const full = intervalAt(ladder, rung);
  const shared = Math.max(1, Math.round(full * rule.share));
  const candidate = addDays(on, shared);
  const keepsExisting =
    strength.dueOn !== undefined && compareCivil(strength.dueOn, candidate) >= 0;

  if (keepsExisting && rung === strength.rung) {
    return { strength, outcome: unchanged };
  }

  const dueOn = keepsExisting ? strength.dueOn : candidate;
  return {
    strength: { ...strength, rung, dueOn },
    outcome: {
      cellId: strength.cellId,
      moved: true,
      climbed: rung !== strength.rung,
      from: strength.dueOn,
      to: dueOn,
    },
  };
}

export interface CreditReport {
  readonly ledger: MasteryLedger;
  readonly outcomes: readonly CreditOutcome[];
}

/**
 * Applies credit to every twin of the answered cell. The twin list is resolved
 * by the caller, because working it out needs the profile revision in force and
 * this module is meant to stay free of that dependency.
 */
export function propagateCredit(
  ledger: MasteryLedger,
  ladder: Ladder,
  rule: CreditRule,
  twinCellIds: readonly string[],
  on: CivilDate,
): CreditReport {
  const changed: CellStrength[] = [];
  const outcomes: CreditOutcome[] = [];

  for (const cellId of twinCellIds) {
    const before = strengthFor(ledger, cellId);
    const { strength, outcome } = creditFor(before, ladder, rule, on);
    outcomes.push(outcome);
    if (outcome.moved) {
      changed.push(strength);
    }
  }

  return { ledger: withStrengths(ledger, changed), outcomes };
}

export function movedCount(report: CreditReport): number {
  return report.outcomes.filter((outcome) => outcome.moved).length;
}

export function climbedCount(report: CreditReport): number {
  return report.outcomes.filter((outcome) => outcome.climbed).length;
}

export function describeCredit(rule: CreditRule, answered: Cell, moved: number): string {
  if (rule.share === 0) {
    return `no credit passes from ${answered.bundle}`;
  }
  const share = Math.round(rule.share * 100);
  return `${moved} twins of ${answered.bundle} took ${share} percent of the interval`;
}
