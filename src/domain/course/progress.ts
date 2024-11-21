import type { Deck } from '../drill/deck.js';
import type { DrillContext } from '../drill/membership.js';
import { resolveMembership } from '../drill/membership.js';
import { parseBundleKey } from '../features/bundle.js';
import { intersectSelectors, matchesSelector } from '../features/selector.js';
import type { Attempt } from '../mastery/attempt.js';
import type { Ladder } from '../mastery/ladder.js';
import { isAtTop } from '../mastery/ladder.js';
import type { MasteryLedger } from '../mastery/ledger.js';
import { strengthFor } from '../mastery/ledger.js';
import type { Cell } from '../paradigm/cell.js';
import type { CivilDate } from '../time/civil.js';
import { minCivil } from '../time/civil.js';
import { dayRange, rangeContains } from '../time/window.js';
import type { Assignment } from './assignment.js';
import { isOverdueOn } from './assignment.js';
import type { Enrolment } from './enrolment.js';

/**
 * How far a learner has got on one assignment.
 *
 * The cell set is the deck narrowed by the assignment, which is an intersection
 * of two selectors and can be empty. An assignment that selects nothing is a
 * setting mistake rather than a learner failure, and it is reported as such
 * instead of showing up as nought progress.
 *
 * Practice days are counted inside the assignment window and inside the
 * enrolment window, so a learner who joined halfway through is not marked down
 * for the days before they were on the register.
 */
export interface AssignmentProgress {
  readonly assignmentKey: string;
  readonly learnerId: string;
  readonly on: CivilDate;
  readonly cells: number;
  readonly mastered: number;
  readonly practiceDays: number;
  readonly masteryMet: boolean;
  readonly practiceMet: boolean;
  readonly met: boolean;
  readonly overdue: boolean;
  readonly selectsNothing: boolean;
}

export function assignmentCells(
  context: DrillContext,
  entry: Assignment,
  deck: Deck,
  on: CivilDate,
): Cell[] {
  const narrowed = intersectSelectors(context.schema, deck.scope.cells, entry.narrowing);
  const membership = resolveMembership(context, deck, on);
  return membership.cells.filter((cell) =>
    matchesSelector(narrowed, parseBundleKey(context.schema, cell.bundle)),
  );
}

export function assignmentProgress(
  context: DrillContext,
  entry: Assignment,
  deck: Deck,
  enrolment: Enrolment,
  ledger: MasteryLedger,
  attempts: readonly Attempt[],
  ladder: Ladder,
  on: CivilDate,
): AssignmentProgress {
  const cells = assignmentCells(context, entry, deck, on);
  const cellIds = new Set<string>(cells.map((cell) => cell.id));

  let mastered = 0;
  for (const cell of cells) {
    const strength = strengthFor(ledger, cell.id);
    if (!strength.suspended && strength.seen > 0 && isAtTop(ladder, strength.rung)) {
      mastered += 1;
    }
  }

  const countedFrom = entry.window.from;
  const countedTo = minCivil(entry.window.to, on);
  const counted = countedTo < countedFrom ? undefined : dayRange(countedFrom, countedTo);

  const days = new Set<string>();
  if (counted !== undefined) {
    for (const attempt of attempts) {
      if (!cellIds.has(attempt.cellId)) {
        continue;
      }
      if (!rangeContains(counted, attempt.on) || !rangeContains(enrolment.window, attempt.on)) {
        continue;
      }
      days.add(attempt.on);
    }
  }

  const masteryMet =
    entry.target.masteredCells === undefined || mastered >= entry.target.masteredCells;
  const practiceMet =
    entry.target.practiceDays === undefined || days.size >= entry.target.practiceDays;

  return {
    assignmentKey: entry.key,
    learnerId: enrolment.learnerId,
    on,
    cells: cells.length,
    mastered,
    practiceDays: days.size,
    masteryMet,
    practiceMet,
    met: masteryMet && practiceMet,
    overdue: isOverdueOn(entry, on),
    selectsNothing: cells.length === 0,
  };
}

export function masteryShare(progress: AssignmentProgress): number {
  return progress.cells === 0 ? 0 : progress.mastered / progress.cells;
}

export function describeProgress(progress: AssignmentProgress): string {
  if (progress.selectsNothing) {
    return `${progress.assignmentKey} selects no cells at all`;
  }
  const state = progress.met ? 'met' : progress.overdue ? 'missed' : 'in progress';
  return `${progress.assignmentKey} is ${state}, ${progress.mastered} of ${progress.cells} cells mastered over ${progress.practiceDays} days`;
}

export function unmetTargets(progress: AssignmentProgress): string[] {
  const unmet: string[] = [];
  if (!progress.masteryMet) {
    unmet.push('mastery');
  }
  if (!progress.practiceMet) {
    unmet.push('practice');
  }
  return unmet;
}
