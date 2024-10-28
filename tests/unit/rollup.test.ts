import { describe, expect, it } from 'vitest';
import { attempt } from '../../src/domain/mastery/attempt.js';
import type { AttemptInput } from '../../src/domain/mastery/attempt.js';
import {
  activeDays,
  attemptsInRange,
  cellRollup,
  dailyRollup,
  deckRollup,
  firstCorrectOn,
  hardestCells,
} from '../../src/domain/report/rollup.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { dayRange } from '../../src/domain/time/window.js';

const d = (value: string) => parseCivilDate(value);
const r = (from: string, to: string) => dayRange(d(from), d(to));

let sequence = 0;
const make = (patch: Partial<AttemptInput> = {}) => {
  sequence += 1;
  return attempt({
    learnerId: 'lrn_one',
    sessionId: 'sess_aaaaaa',
    cellId: 'cell_aaaaaa',
    deckKey: 'first-year',
    on: d('2025-03-03'),
    sequence,
    given: 'puellae',
    expected: 'puellae',
    verdict: 'exact',
    toleranceLevel: 'standard',
    revisionId: 'rev_aaaaaa',
    ...patch,
  });
};

const attempts = [
  make({ on: d('2025-03-03'), cellId: 'cell_a', verdict: 'exact' }),
  make({ on: d('2025-03-03'), cellId: 'cell_b', verdict: 'wrong', given: 'x' }),
  make({ on: d('2025-03-05'), cellId: 'cell_b', verdict: 'wrong', given: 'y' }),
  make({ on: d('2025-03-05'), cellId: 'cell_b', verdict: 'exact' }),
  make({
    on: d('2025-03-05'),
    cellId: 'cell_c',
    verdict: 'near',
    given: 'z',
    deckKey: 'second-year',
  }),
];

describe('dailyRollup', () => {
  it('has a row for every day of the range', () => {
    const rows = dailyRollup(attempts, r('2025-03-03', '2025-03-06'));
    expect(rows.map((row) => row.on)).toEqual([
      '2025-03-03',
      '2025-03-04',
      '2025-03-05',
      '2025-03-06',
    ]);
  });

  it('counts the attempts of each day', () => {
    const rows = dailyRollup(attempts, r('2025-03-03', '2025-03-05'));
    expect(rows[0]?.tally.total).toBe(2);
    expect(rows[1]?.tally.total).toBe(0);
    expect(rows[2]?.tally.total).toBe(3);
  });

  it('counts distinct cells rather than attempts', () => {
    const rows = dailyRollup(attempts, r('2025-03-05', '2025-03-05'));
    expect(rows[0]?.distinctCells).toBe(2);
    expect(rows[0]?.tally.total).toBe(3);
  });

  it('reads accuracy off each day', () => {
    const rows = dailyRollup(attempts, r('2025-03-03', '2025-03-03'));
    expect(rows[0]?.accuracy).toBe(0.5);
  });

  it('is zero rather than undefined on a day with nothing', () => {
    const rows = dailyRollup(attempts, r('2025-03-04', '2025-03-04'));
    expect(rows[0]?.accuracy).toBe(0);
  });

  it('leaves out attempts outside the range', () => {
    const rows = dailyRollup(attempts, r('2025-03-05', '2025-03-05'));
    expect(rows).toHaveLength(1);
    expect(rows[0]?.tally.total).toBe(3);
  });
});

describe('cellRollup', () => {
  it('has one row per cell in key order', () => {
    expect(cellRollup(attempts).map((row) => row.cellId)).toEqual(['cell_a', 'cell_b', 'cell_c']);
  });

  it('counts every attempt on a cell', () => {
    const row = cellRollup(attempts).find((entry) => entry.cellId === 'cell_b')!;
    expect(row.tally.total).toBe(3);
    expect(row.tally.wrong).toBe(2);
    expect(row.accuracy).toBeCloseTo(1 / 3);
  });

  it('reports the latest verdict and the day it happened', () => {
    const row = cellRollup(attempts).find((entry) => entry.cellId === 'cell_b')!;
    expect(row.lastVerdict).toBe('exact');
    expect(row.lastSeenOn).toBe('2025-03-05');
  });

  it('gives nothing for an empty log', () => {
    expect(cellRollup([])).toEqual([]);
  });
});

describe('hardestCells', () => {
  it('ranks the cells with the most wrong answers first', () => {
    expect(hardestCells(cellRollup(attempts)).map((row) => row.cellId)).toEqual([
      'cell_b',
      'cell_c',
    ]);
  });

  it('leaves out cells that were never missed', () => {
    expect(hardestCells(cellRollup(attempts)).map((row) => row.cellId)).not.toContain('cell_a');
  });

  it('honours the limit', () => {
    expect(hardestCells(cellRollup(attempts), 1)).toHaveLength(1);
  });

  it('gives nothing when everything was right', () => {
    const clean = [make({ cellId: 'cell_a' }), make({ cellId: 'cell_b' })];
    expect(hardestCells(cellRollup(clean))).toEqual([]);
  });
});

describe('deckRollup', () => {
  it('splits the log by deck', () => {
    const rows = deckRollup(attempts);
    expect(rows.map((row) => row.deckKey)).toEqual(['first-year', 'second-year']);
    expect(rows[0]?.tally.total).toBe(4);
  });

  it('counts the distinct days each deck was drilled', () => {
    expect(deckRollup(attempts)[0]?.days).toBe(2);
    expect(deckRollup(attempts)[1]?.days).toBe(1);
  });
});

describe('activeDays', () => {
  it('names each day something was answered once', () => {
    expect([...activeDays(attempts)].sort()).toEqual(['2025-03-03', '2025-03-05']);
  });

  it('is empty for an empty log', () => {
    expect(activeDays([]).size).toBe(0);
  });
});

describe('firstCorrectOn', () => {
  it('finds the first day a cell was answered right', () => {
    expect(firstCorrectOn(attempts, 'cell_b')).toBe('2025-03-05');
  });

  it('gives nothing for a cell that was never right', () => {
    expect(firstCorrectOn(attempts, 'cell_c')).toBeUndefined();
    expect(firstCorrectOn(attempts, 'cell_zzz')).toBeUndefined();
  });
});

describe('attemptsInRange', () => {
  it('keeps only the attempts inside the range', () => {
    expect(attemptsInRange(attempts, r('2025-03-05', '2025-03-09'))).toHaveLength(3);
    expect(attemptsInRange(attempts, r('2025-04-01', '2025-04-30'))).toEqual([]);
  });
});
