import type { Cell } from '../paradigm/cell.js';
import type { Ladder } from '../mastery/ladder.js';
import { isAtTop } from '../mastery/ladder.js';
import type { MasteryLedger } from '../mastery/ledger.js';
import { strengthFor } from '../mastery/ledger.js';
import { accuracy, isDue, isNew } from '../mastery/strength.js';
import type { CivilDate } from '../time/civil.js';

/**
 * Coverage answers "how much of this paradigm does the learner hold", and it
 * has to be asked in two directions because they disagree. Down the bundles it
 * says which cells of the table are weak across every word. Down the lexemes it
 * says which words are weak across the whole table.
 *
 * A learner can look fine on one and terrible on the other. Somebody who knows
 * the nominative of four hundred nouns and nothing else scores well per lexeme
 * and badly per bundle, and the second reading is the one that says what to
 * drill next.
 */
export interface CoverageRow {
  readonly key: string;
  readonly total: number;
  readonly untouched: number;
  readonly learning: number;
  readonly mature: number;
  readonly due: number;
  readonly suspended: number;
  readonly accuracy: number;
}

function emptyRow(key: string): CoverageRow {
  return {
    key,
    total: 0,
    untouched: 0,
    learning: 0,
    mature: 0,
    due: 0,
    suspended: 0,
    accuracy: 0,
  };
}

interface Accumulator {
  row: CoverageRow;
  accuracySum: number;
  accuracyCount: number;
}

function accumulate(
  buckets: Map<string, Accumulator>,
  key: string,
  ledger: MasteryLedger,
  cell: Cell,
  ladder: Ladder,
  on: CivilDate,
): void {
  const existing = buckets.get(key) ?? { row: emptyRow(key), accuracySum: 0, accuracyCount: 0 };
  const strength = strengthFor(ledger, cell.id);
  const row = { ...existing.row, total: existing.row.total + 1 };

  if (strength.suspended) {
    row.suspended += 1;
  } else if (isNew(strength)) {
    row.untouched += 1;
  } else if (isAtTop(ladder, strength.rung)) {
    row.mature += 1;
  } else {
    row.learning += 1;
  }

  if (!strength.suspended && isDue(strength, on)) {
    row.due += 1;
  }

  const seen = strength.seen > 0;
  buckets.set(key, {
    row,
    accuracySum: existing.accuracySum + (seen ? accuracy(strength) : 0),
    accuracyCount: existing.accuracyCount + (seen ? 1 : 0),
  });
}

function finish(buckets: Map<string, Accumulator>): CoverageRow[] {
  return [...buckets.values()]
    .map(({ row, accuracySum, accuracyCount }) => ({
      ...row,
      accuracy: accuracyCount === 0 ? 0 : accuracySum / accuracyCount,
    }))
    .sort((a, b) => a.key.localeCompare(b.key));
}

export function coverageByBundle(
  cells: readonly Cell[],
  ledger: MasteryLedger,
  ladder: Ladder,
  on: CivilDate,
): CoverageRow[] {
  const buckets = new Map<string, Accumulator>();
  for (const cell of cells) {
    accumulate(buckets, cell.bundle, ledger, cell, ladder, on);
  }
  return finish(buckets);
}

export function coverageByLexeme(
  cells: readonly Cell[],
  ledger: MasteryLedger,
  ladder: Ladder,
  on: CivilDate,
): CoverageRow[] {
  const buckets = new Map<string, Accumulator>();
  for (const cell of cells) {
    accumulate(buckets, cell.lexemeId, ledger, cell, ladder, on);
  }
  return finish(buckets);
}

export function coverageTotal(rows: readonly CoverageRow[]): CoverageRow {
  const total = rows.reduce(
    (sum, row) => ({
      key: 'all',
      total: sum.total + row.total,
      untouched: sum.untouched + row.untouched,
      learning: sum.learning + row.learning,
      mature: sum.mature + row.mature,
      due: sum.due + row.due,
      suspended: sum.suspended + row.suspended,
      accuracy: 0,
    }),
    emptyRow('all'),
  );
  const seenRows = rows.filter((row) => row.accuracy > 0);
  const meanAccuracy =
    seenRows.length === 0
      ? 0
      : seenRows.reduce((sum, row) => sum + row.accuracy, 0) / seenRows.length;
  return { ...total, accuracy: meanAccuracy };
}

/** The share of a row that is mature, which is what a progress bar shows. */
export function maturityOf(row: CoverageRow): number {
  return row.total === 0 ? 0 : row.mature / row.total;
}

export function touchedShare(row: CoverageRow): number {
  return row.total === 0 ? 0 : (row.total - row.untouched) / row.total;
}

/** Rows ranked by how much work they still need, heaviest first. */
export function weakestRows(rows: readonly CoverageRow[], limit = 5): CoverageRow[] {
  return [...rows]
    .sort((a, b) => {
      const byMaturity = maturityOf(a) - maturityOf(b);
      if (Math.abs(byMaturity) > 1e-9) {
        return byMaturity;
      }
      const byAccuracy = a.accuracy - b.accuracy;
      return Math.abs(byAccuracy) > 1e-9 ? byAccuracy : a.key.localeCompare(b.key);
    })
    .slice(0, limit);
}
