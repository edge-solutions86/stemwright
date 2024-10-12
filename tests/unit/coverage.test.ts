import { describe, expect, it } from 'vitest';
import { deck } from '../../src/domain/drill/deck.js';
import { resolveMembership } from '../../src/domain/drill/membership.js';
import { ladder } from '../../src/domain/mastery/ladder.js';
import { buildLedger, emptyLedger } from '../../src/domain/mastery/ledger.js';
import {
  newStrength,
  recordCorrect,
  recordLapse,
  suspend,
} from '../../src/domain/mastery/strength.js';
import {
  coverageByBundle,
  coverageByLexeme,
  coverageTotal,
  maturityOf,
  touchedShare,
  weakestRows,
} from '../../src/domain/report/coverage.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { drillContext, puella } from '../support/drill.js';
import { latinNoun } from '../support/profiles.js';

const d = (value: string) => parseCivilDate(value);
const TERM = d('2024-10-01');
const flat = ladder({ key: 'flat', rungs: [1, 3], spread: 0 });

const membership = resolveMembership(drillContext, deck(latinNoun, { key: 'all' }), TERM);
const cellOf = (bundle: string, lexemeId = puella.id) =>
  membership.cells.find((cell) => cell.lexemeId === lexemeId && cell.bundle === bundle)!;

const mature = (cellId: string) =>
  recordCorrect(recordCorrect(newStrength(cellId), flat, d('2024-09-01')), flat, d('2024-09-02'));
const learning = (cellId: string) => recordCorrect(newStrength(cellId), flat, d('2024-09-30'));

const ledger = buildLedger('lrn_one', [
  mature(cellOf('case=nom;number=sg').id),
  learning(cellOf('case=gen;number=sg').id),
  suspend(recordLapse(newStrength(cellOf('case=dat;number=sg').id), flat, TERM)),
]);

describe('coverageByBundle', () => {
  const rows = coverageByBundle(membership.cells, ledger, flat, TERM);

  it('has one row per bundle', () => {
    expect(rows).toHaveLength(10);
  });

  it('counts three lexemes in every bundle', () => {
    expect(rows.every((row) => row.total === 3)).toBe(true);
  });

  it('splits a bundle into its buckets', () => {
    const nomSg = rows.find((row) => row.key === 'case=nom;number=sg')!;
    expect(nomSg.mature).toBe(1);
    expect(nomSg.untouched).toBe(2);
    expect(nomSg.suspended).toBe(0);
  });

  it('counts a suspended cell as suspended and nothing else', () => {
    const datSg = rows.find((row) => row.key === 'case=dat;number=sg')!;
    expect(datSg.suspended).toBe(1);
    expect(datSg.untouched).toBe(2);
    expect(datSg.learning).toBe(0);
  });

  it('counts a due cell', () => {
    const genSg = rows.find((row) => row.key === 'case=gen;number=sg')!;
    expect(genSg.learning).toBe(1);
    expect(genSg.due).toBe(1);
  });

  it('averages accuracy over the cells that were seen', () => {
    const nomSg = rows.find((row) => row.key === 'case=nom;number=sg')!;
    expect(nomSg.accuracy).toBe(1);
    const accPl = rows.find((row) => row.key === 'case=acc;number=pl')!;
    expect(accPl.accuracy).toBe(0);
  });
});

describe('coverageByLexeme', () => {
  const rows = coverageByLexeme(membership.cells, ledger, flat, TERM);

  it('has one row per lexeme', () => {
    expect(rows).toHaveLength(3);
  });

  it('counts ten cells for each', () => {
    expect(rows.every((row) => row.total === 10)).toBe(true);
  });

  it('puts the answered cells under the right lexeme', () => {
    const row = rows.find((entry) => entry.key === puella.id)!;
    expect(row.mature).toBe(1);
    expect(row.learning).toBe(1);
    expect(row.suspended).toBe(1);
    expect(row.untouched).toBe(7);
  });
});

describe('coverageTotal', () => {
  it('adds the buckets across every row', () => {
    const total = coverageTotal(coverageByBundle(membership.cells, ledger, flat, TERM));
    expect(total.total).toBe(30);
    expect(total.mature).toBe(1);
    expect(total.learning).toBe(1);
    expect(total.suspended).toBe(1);
    expect(total.untouched).toBe(27);
  });

  it('reports zeros for no rows at all', () => {
    expect(coverageTotal([])).toEqual({
      key: 'all',
      total: 0,
      untouched: 0,
      learning: 0,
      mature: 0,
      due: 0,
      suspended: 0,
      accuracy: 0,
    });
  });
});

describe('maturityOf and touchedShare', () => {
  const rows = coverageByLexeme(membership.cells, ledger, flat, TERM);
  const row = rows.find((entry) => entry.key === puella.id)!;

  it('reads maturity as a share of the row', () => {
    expect(maturityOf(row)).toBeCloseTo(0.1);
  });

  it('reads how much of the row has been met at all', () => {
    expect(touchedShare(row)).toBeCloseTo(0.3);
  });

  it('is nought for an empty row', () => {
    const empty = { ...row, total: 0, mature: 0, untouched: 0 };
    expect(maturityOf(empty)).toBe(0);
    expect(touchedShare(empty)).toBe(0);
  });
});

describe('weakestRows', () => {
  const rows = coverageByBundle(membership.cells, ledger, flat, TERM);

  it('puts the least mature first', () => {
    const weakest = weakestRows(rows, 3);
    expect(weakest.every((row) => row.mature === 0)).toBe(true);
  });

  it('takes only as many as it was asked for', () => {
    expect(weakestRows(rows, 2)).toHaveLength(2);
    expect(weakestRows(rows, 99)).toHaveLength(10);
  });

  it('leaves the mature row out of the weakest few', () => {
    expect(weakestRows(rows, 3).map((row) => row.key)).not.toContain('case=nom;number=sg');
  });

  it('returns nothing for no rows', () => {
    expect(weakestRows([], 5)).toEqual([]);
  });

  it('is stable for a fresh learner, falling back to the key', () => {
    const fresh = coverageByBundle(membership.cells, emptyLedger('lrn_two'), flat, TERM);
    expect(weakestRows(fresh, 2).map((row) => row.key)).toEqual(
      fresh
        .map((row) => row.key)
        .sort()
        .slice(0, 2),
    );
  });
});
