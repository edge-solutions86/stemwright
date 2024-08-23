import { ProblemCollector } from '../errors.js';
import { asSlug } from '../identifiers.js';
import { digest } from '../identifiers.js';

/**
 * Intervals come from a ladder of named rungs rather than from a formula. A
 * ladder is something a course can print and argue about, which a decay
 * constant is not, and a department that wants the first review the next
 * morning can say so without anybody deriving what that implies.
 *
 * The rungs are days. Climbing goes up one rung, a lapse drops back by a
 * configured number of rungs rather than to the bottom, because dropping a cell
 * a learner has known for six months back to ten minutes is how a review queue
 * fills up with things nobody has forgotten.
 */
export interface Ladder {
  readonly key: string;
  readonly label: string;
  /** Days to wait at each rung, strictly increasing. */
  readonly rungs: readonly number[];
  /** How many rungs a lapse costs. */
  readonly lapseDrop: number;
  /** Fraction of the interval that may be spread either way, nought to one half. */
  readonly spread: number;
}

export interface LadderInput {
  readonly key: string;
  readonly label?: string;
  readonly rungs: readonly number[];
  readonly lapseDrop?: number;
  readonly spread?: number;
}

export function ladder(input: LadderInput): Ladder {
  const problems = new ProblemCollector();
  const key = asSlug('ladder.key', input.key);
  const rungs = [...input.rungs];

  if (rungs.length === 0) {
    problems.add('rungs', 'must hold at least one rung');
  }
  for (const [index, rung] of rungs.entries()) {
    if (!Number.isInteger(rung) || rung < 1) {
      problems.add(`rungs.${index}`, `must be a whole number of days above zero: ${String(rung)}`);
      continue;
    }
    const previous = rungs[index - 1];
    if (previous !== undefined && rung <= previous) {
      problems.add(`rungs.${index}`, `must be longer than the rung before it: ${String(rung)}`);
    }
  }

  const lapseDrop = input.lapseDrop ?? 2;
  if (!Number.isInteger(lapseDrop) || lapseDrop < 1) {
    problems.add('lapseDrop', `must be a whole number above zero: ${String(lapseDrop)}`);
  }

  const spread = input.spread ?? 0.1;
  if (!Number.isFinite(spread) || spread < 0 || spread > 0.5) {
    problems.add('spread', `must be between zero and one half: ${String(spread)}`);
  }

  problems.throwIfAny(`ladder ${key} is not usable`);
  return { key, label: input.label ?? key, rungs, lapseDrop, spread };
}

export const STANDARD_LADDER = ladder({
  key: 'standard',
  label: 'Standard',
  rungs: [1, 3, 7, 16, 35, 75, 160, 340],
});

export const SLOW_LADDER = ladder({
  key: 'slow-start',
  label: 'Slow start',
  rungs: [1, 2, 4, 8, 15, 30, 60, 120, 240],
  lapseDrop: 1,
  spread: 0.05,
});

export function rungCount(entry: Ladder): number {
  return entry.rungs.length;
}

export function topRung(entry: Ladder): number {
  return entry.rungs.length - 1;
}

export function clampRung(entry: Ladder, rung: number): number {
  if (!Number.isFinite(rung)) {
    return 0;
  }
  return Math.max(0, Math.min(topRung(entry), Math.trunc(rung)));
}

export function intervalAt(entry: Ladder, rung: number): number {
  return entry.rungs[clampRung(entry, rung)]!;
}

export function climb(entry: Ladder, rung: number): number {
  return clampRung(entry, rung + 1);
}

export function lapse(entry: Ladder, rung: number): number {
  return clampRung(entry, rung - entry.lapseDrop);
}

export function isAtTop(entry: Ladder, rung: number): boolean {
  return clampRung(entry, rung) === topRung(entry);
}

/**
 * Spreads an interval deterministically from a seed so two cells that climbed
 * together do not come due together forever. The same seed always gives the
 * same offset, which keeps a queue reproducible for a report and for a test.
 */
export function spreadInterval(entry: Ladder, rung: number, seed: string): number {
  const base = intervalAt(entry, rung);
  if (entry.spread === 0 || base <= 1) {
    return base;
  }
  const hash = digest(entry.key, String(rung), seed);
  const unit = Number.parseInt(hash.slice(0, 6), 36) / 36 ** 6;
  const swing = base * entry.spread;
  const offset = Math.round((unit * 2 - 1) * swing);
  return Math.max(1, base + offset);
}

export function describeLadder(entry: Ladder): string {
  const shown = entry.rungs.slice(0, 4).join(', ');
  const tail = entry.rungs.length > 4 ? `, up to ${entry.rungs[entry.rungs.length - 1]!}` : '';
  return `${entry.label} waits ${shown}${tail} days and drops ${entry.lapseDrop} rungs on a lapse`;
}
