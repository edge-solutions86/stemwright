import type { Id } from '../identifiers.js';
import type { CivilDate } from '../time/civil.js';
import { compareCivil } from '../time/civil.js';
import type { Ladder } from './ladder.js';
import { isAtTop } from './ladder.js';
import type { CellStrength } from './strength.js';
import { daysOverdue, isDue, isNew, newStrength } from './strength.js';

/**
 * One learner's whole state, keyed by cell id. Absence is meaningful and it is
 * the common case: a lexicon of four hundred nouns is thirty thousand cells,
 * and a learner in week three has answered perhaps sixty of them. Storing a
 * row for every unanswered cell would be storing the paradigm twice.
 *
 * So `strengthFor` invents a fresh strength rather than failing, and nothing
 * downstream is allowed to tell the difference between a cell that was never
 * stored and one stored in its initial state.
 */
export interface MasteryLedger {
  readonly learnerId: string;
  readonly byCell: ReadonlyMap<string, CellStrength>;
}

export function emptyLedger(learnerId: Id<'learner'> | string): MasteryLedger {
  return { learnerId, byCell: new Map() };
}

export function buildLedger(
  learnerId: Id<'learner'> | string,
  entries: readonly CellStrength[],
): MasteryLedger {
  const byCell = new Map<string, CellStrength>();
  for (const entry of entries) {
    byCell.set(entry.cellId, entry);
  }
  return { learnerId, byCell };
}

export function strengthFor(ledger: MasteryLedger, cellId: string): CellStrength {
  return ledger.byCell.get(cellId) ?? newStrength(cellId);
}

export function hasStrength(ledger: MasteryLedger, cellId: string): boolean {
  return ledger.byCell.has(cellId);
}

export function withStrength(ledger: MasteryLedger, strength: CellStrength): MasteryLedger {
  const byCell = new Map(ledger.byCell);
  byCell.set(strength.cellId, strength);
  return { learnerId: ledger.learnerId, byCell };
}

export function withStrengths(
  ledger: MasteryLedger,
  strengths: readonly CellStrength[],
): MasteryLedger {
  const byCell = new Map(ledger.byCell);
  for (const strength of strengths) {
    byCell.set(strength.cellId, strength);
  }
  return { learnerId: ledger.learnerId, byCell };
}

export function forgetCell(ledger: MasteryLedger, cellId: string): MasteryLedger {
  if (!ledger.byCell.has(cellId)) {
    return ledger;
  }
  const byCell = new Map(ledger.byCell);
  byCell.delete(cellId);
  return { learnerId: ledger.learnerId, byCell };
}

/**
 * Due cells among the ones asked about, most overdue first. The candidate list
 * comes from the caller because what is due depends on which deck is being
 * drilled, not on everything the learner has ever seen.
 */
export function dueCells(
  ledger: MasteryLedger,
  cellIds: readonly string[],
  on: CivilDate,
): CellStrength[] {
  return cellIds
    .map((cellId) => strengthFor(ledger, cellId))
    .filter((strength) => isDue(strength, on))
    .sort((a, b) => {
      const byOverdue = daysOverdue(b, on) - daysOverdue(a, on);
      return byOverdue !== 0 ? byOverdue : a.cellId.localeCompare(b.cellId);
    });
}

export function newCells(ledger: MasteryLedger, cellIds: readonly string[]): CellStrength[] {
  return cellIds
    .map((cellId) => strengthFor(ledger, cellId))
    .filter(isNew)
    .sort((a, b) => a.cellId.localeCompare(b.cellId));
}

export function suspendedCells(ledger: MasteryLedger, cellIds: readonly string[]): CellStrength[] {
  return cellIds
    .map((cellId) => strengthFor(ledger, cellId))
    .filter((strength) => strength.suspended)
    .sort((a, b) => a.cellId.localeCompare(b.cellId));
}

export interface LedgerCounts {
  readonly known: number;
  readonly introduced: number;
  readonly due: number;
  readonly mature: number;
  readonly suspended: number;
  readonly untouched: number;
}

export function ledgerCounts(
  ledger: MasteryLedger,
  cellIds: readonly string[],
  ladder: Ladder,
  on: CivilDate,
): LedgerCounts {
  let introduced = 0;
  let due = 0;
  let mature = 0;
  let suspended = 0;
  let untouched = 0;

  for (const cellId of cellIds) {
    const strength = strengthFor(ledger, cellId);
    if (strength.suspended) {
      suspended += 1;
      continue;
    }
    if (isNew(strength)) {
      untouched += 1;
      continue;
    }
    introduced += 1;
    if (isDue(strength, on)) {
      due += 1;
    }
    if (isAtTop(ladder, strength.rung)) {
      mature += 1;
    }
  }

  return { known: cellIds.length, introduced, due, mature, suspended, untouched };
}

/** The next day anything in the list comes round, or nothing if none will. */
export function nextDueDate(
  ledger: MasteryLedger,
  cellIds: readonly string[],
): CivilDate | undefined {
  let soonest: CivilDate | undefined;
  for (const cellId of cellIds) {
    const strength = strengthFor(ledger, cellId);
    if (strength.suspended || strength.dueOn === undefined) {
      continue;
    }
    if (soonest === undefined || compareCivil(strength.dueOn, soonest) < 0) {
      soonest = strength.dueOn;
    }
  }
  return soonest;
}

export function ledgerSize(ledger: MasteryLedger): number {
  return ledger.byCell.size;
}
