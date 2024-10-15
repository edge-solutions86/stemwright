import type { Cell } from '../paradigm/cell.js';
import type { DrillPolicy } from '../drill/policy.js';
import { policyAllowsDay } from '../drill/policy.js';
import type { Ladder } from '../mastery/ladder.js';
import { intervalAt } from '../mastery/ladder.js';
import type { MasteryLedger } from '../mastery/ledger.js';
import { strengthFor } from '../mastery/ledger.js';
import { isNew } from '../mastery/strength.js';
import type { CivilDate } from '../time/civil.js';
import { addDays, compareCivil, weekday } from '../time/civil.js';
import type { DayRange } from '../time/window.js';
import { eachDay } from '../time/window.js';

/**
 * How much work is coming. A forecast is not a plan: it reads the due dates
 * already written down and says how many fall on each day, without pretending
 * to know whether the learner will get them right.
 *
 * The optimistic projection does pretend, and says so in its name. It assumes
 * every review is answered correctly on the day it falls due and follows the
 * ladder forward, which gives the lightest possible future and is useful only
 * as a floor.
 */
export interface ForecastDay {
  readonly on: CivilDate;
  readonly due: number;
  readonly overCeiling: boolean;
  readonly resting: boolean;
}

export function forecast(
  cells: readonly Cell[],
  ledger: MasteryLedger,
  range: DayRange,
  policy: DrillPolicy,
): ForecastDay[] {
  const counts = new Map<string, number>();
  for (const cell of cells) {
    const strength = strengthFor(ledger, cell.id);
    if (strength.suspended || strength.dueOn === undefined) {
      continue;
    }
    const key = compareCivil(strength.dueOn, range.from) < 0 ? range.from : strength.dueOn;
    counts.set(key, (counts.get(key) ?? 0) + 1);
  }

  const days: ForecastDay[] = [];
  for (const day of eachDay(range)) {
    const due = counts.get(day) ?? 0;
    days.push({
      on: day,
      due,
      overCeiling: due > policy.reviewsPerDay,
      resting: !policyAllowsDay(policy, weekday(day)),
    });
  }
  return days;
}

export function busiestDay(days: readonly ForecastDay[]): ForecastDay | undefined {
  return [...days].sort((a, b) => b.due - a.due || a.on.localeCompare(b.on))[0];
}

export function totalDue(days: readonly ForecastDay[]): number {
  return days.reduce((sum, day) => sum + day.due, 0);
}

export function daysOverCeiling(days: readonly ForecastDay[]): ForecastDay[] {
  return days.filter((day) => day.overCeiling);
}

/** Reviews due on a day the policy rests, which will spill onto the next day. */
export function spilledOnRestDays(days: readonly ForecastDay[]): number {
  return days.filter((day) => day.resting).reduce((sum, day) => sum + day.due, 0);
}

export interface ProjectionDay {
  readonly on: CivilDate;
  readonly reviews: number;
  readonly introductions: number;
}

/**
 * Walks forward assuming every answer is right, introducing new cells at the
 * policy budget. Each cell is followed along the ladder, so the shape of the
 * curve comes from the rungs rather than from a decay model.
 */
export function optimisticProjection(
  cells: readonly Cell[],
  ledger: MasteryLedger,
  ladder: Ladder,
  policy: DrillPolicy,
  range: DayRange,
): ProjectionDay[] {
  const pending: { due: CivilDate; rung: number }[] = [];
  const untouched: string[] = [];

  for (const cell of cells) {
    const strength = strengthFor(ledger, cell.id);
    if (strength.suspended) {
      continue;
    }
    if (isNew(strength)) {
      untouched.push(cell.id);
      continue;
    }
    if (strength.dueOn !== undefined) {
      pending.push({ due: strength.dueOn, rung: strength.rung });
    }
  }

  const days: ProjectionDay[] = [];
  let queueOfNew = untouched.length;

  for (const day of eachDay(range)) {
    if (!policyAllowsDay(policy, weekday(day))) {
      days.push({ on: day, reviews: 0, introductions: 0 });
      continue;
    }

    let reviews = 0;
    for (const entry of [...pending]) {
      if (compareCivil(entry.due, day) > 0) {
        continue;
      }
      reviews += 1;
      const next = Math.min(entry.rung + 1, ladder.rungs.length - 1);
      entry.rung = next;
      entry.due = addDays(day, intervalAt(ladder, next));
    }

    const introductions = Math.min(policy.newPerDay, queueOfNew);
    queueOfNew -= introductions;
    for (let index = 0; index < introductions; index += 1) {
      pending.push({ due: addDays(day, intervalAt(ladder, 0)), rung: 0 });
    }

    days.push({ on: day, reviews, introductions });
  }

  return days;
}

export function daysToClearBacklog(days: readonly ProjectionDay[]): number {
  const index = days.findIndex((day) => day.introductions === 0 && day.reviews === 0);
  return index < 0 ? days.length : index;
}
