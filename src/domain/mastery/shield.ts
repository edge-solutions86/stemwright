import { ProblemCollector } from '../errors.js';
import { requireText } from '../text/normalise.js';
import type { CivilDate } from '../time/civil.js';
import { addDays, compareCivil } from '../time/civil.js';
import type { DayRange } from '../time/window.js';
import { coveredBy, normaliseRanges, rangeContains } from '../time/window.js';
import type { MasteryLedger } from './ledger.js';
import { strengthFor, withStrengths } from './ledger.js';
import type { CellStrength } from './strength.js';
import { deferTo } from './strength.js';

/**
 * A shield is a stretch of days the learner is excused from. Illness, a reading
 * week, a field trip. Shields do two things and they are separate: reviews due
 * inside one are pushed out to the far side, and a streak counted across one
 * steps over it rather than breaking.
 *
 * The second is the part that is easy to get wrong. Treating a shielded day as
 * an ordinary missed day punishes the learner for something the department
 * signed off, and treating it as a day they answered credits them with work
 * they did not do. Stepping over is neither.
 */
export interface Shield {
  readonly window: DayRange;
  readonly reason: string;
}

export interface ShieldInput {
  readonly window: DayRange;
  readonly reason?: string;
}

export interface ShieldSet {
  readonly shields: readonly Shield[];
  readonly windows: readonly DayRange[];
}

export const NO_SHIELDS: ShieldSet = { shields: [], windows: [] };

export function shieldSet(inputs: readonly ShieldInput[]): ShieldSet {
  const problems = new ProblemCollector();
  const shields: Shield[] = [];

  for (const [index, input] of inputs.entries()) {
    const reason =
      input.reason === undefined
        ? 'no reason recorded'
        : requireText(`shields.${index}.reason`, input.reason, 200);
    shields.push({ window: input.window, reason });
  }

  problems.throwIfAny('shield set is not usable');
  return { shields, windows: normaliseRanges(shields.map((shield) => shield.window)) };
}

export function isShielded(set: ShieldSet, day: CivilDate): boolean {
  return coveredBy(set.windows, day);
}

export function shieldCovering(set: ShieldSet, day: CivilDate): Shield | undefined {
  return set.shields.find((shield) => rangeContains(shield.window, day));
}

/** The first day at or after this one that is not shielded. */
export function firstOpenDay(set: ShieldSet, day: CivilDate): CivilDate {
  let candidate = day;
  for (const window of set.windows) {
    if (rangeContains(window, candidate)) {
      candidate = addDays(window.to, 1);
    }
  }
  return candidate;
}

export function shieldedDaysIn(set: ShieldSet, range: DayRange): number {
  let count = 0;
  for (const window of set.windows) {
    const from = compareCivil(window.from, range.from) < 0 ? range.from : window.from;
    const to = compareCivil(window.to, range.to) > 0 ? range.to : window.to;
    if (compareCivil(from, to) <= 0) {
      count += 1;
      let cursor = from;
      while (compareCivil(cursor, to) < 0) {
        cursor = addDays(cursor, 1);
        count += 1;
      }
    }
  }
  return count;
}

export interface ShieldOutcome {
  readonly cellId: string;
  readonly from: CivilDate;
  readonly to: CivilDate;
}

/**
 * Pushes every review due inside a shield out to the first open day. Cells due
 * before the shield are left alone: a learner who was already behind when the
 * shield started does not get the backlog forgiven.
 */
export function applyShields(
  ledger: MasteryLedger,
  cellIds: readonly string[],
  set: ShieldSet,
): { readonly ledger: MasteryLedger; readonly outcomes: readonly ShieldOutcome[] } {
  const changed: CellStrength[] = [];
  const outcomes: ShieldOutcome[] = [];

  for (const cellId of cellIds) {
    const strength = strengthFor(ledger, cellId);
    if (strength.dueOn === undefined || strength.suspended || !isShielded(set, strength.dueOn)) {
      continue;
    }
    const open = firstOpenDay(set, strength.dueOn);
    changed.push(deferTo(strength, open));
    outcomes.push({ cellId, from: strength.dueOn, to: open });
  }

  return { ledger: withStrengths(ledger, changed), outcomes };
}

/**
 * Counts a run of days ending at `on`, treating a shielded day as neither a
 * break nor a day answered. A day the learner answered on continues the run, a
 * shielded day is stepped over, and anything else ends it.
 */
export function streakThrough(
  answeredOn: ReadonlySet<string>,
  set: ShieldSet,
  on: CivilDate,
  limit = 400,
): number {
  let streak = 0;
  let cursor = on;
  for (let step = 0; step < limit; step += 1) {
    if (answeredOn.has(cursor)) {
      streak += 1;
    } else if (!isShielded(set, cursor)) {
      return streak;
    }
    cursor = addDays(cursor, -1);
  }
  return streak;
}

export function describeShields(set: ShieldSet): string {
  if (set.shields.length === 0) {
    return 'no shielded days';
  }
  return set.shields
    .map((shield) => `${shield.window.from} to ${shield.window.to} for ${shield.reason}`)
    .join(', ');
}
