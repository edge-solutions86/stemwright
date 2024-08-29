import { describe, expect, it } from 'vitest';
import { ladder } from '../../src/domain/mastery/ladder.js';
import {
  buildLedger,
  dueCells,
  emptyLedger,
  forgetCell,
  hasStrength,
  ledgerCounts,
  ledgerSize,
  newCells,
  nextDueDate,
  strengthFor,
  suspendedCells,
  withStrength,
  withStrengths,
} from '../../src/domain/mastery/ledger.js';
import {
  newStrength,
  recordCorrect,
  recordLapse,
  suspend,
} from '../../src/domain/mastery/strength.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';

const d = (value: string) => parseCivilDate(value);
const DAY = d('2025-03-03');
const flat = ladder({ key: 'flat', rungs: [1, 3, 7], spread: 0 });

const seen = (cellId: string, times: number, from = DAY) => {
  let strength = newStrength(cellId);
  for (let step = 0; step < times; step += 1) {
    strength = recordCorrect(strength, flat, from);
  }
  return strength;
};

const cells = ['cell_a', 'cell_b', 'cell_c', 'cell_d'];

const ledger = buildLedger('lrn_one', [
  seen('cell_a', 1),
  seen('cell_b', 3),
  suspend(recordLapse(newStrength('cell_c'), flat, DAY)),
]);

describe('emptyLedger and buildLedger', () => {
  it('starts with nothing stored', () => {
    expect(ledgerSize(emptyLedger('lrn_one'))).toBe(0);
  });

  it('keys what it was given by cell', () => {
    expect(ledgerSize(ledger)).toBe(3);
    expect(hasStrength(ledger, 'cell_a')).toBe(true);
    expect(hasStrength(ledger, 'cell_d')).toBe(false);
  });
});

describe('strengthFor', () => {
  it('reads a stored strength back', () => {
    expect(strengthFor(ledger, 'cell_b').rung).toBe(2);
  });

  it('invents a fresh strength for a cell nobody has answered', () => {
    const fresh = strengthFor(ledger, 'cell_d');
    expect(fresh.cellId).toBe('cell_d');
    expect(fresh.seen).toBe(0);
    expect(fresh.dueOn).toBeUndefined();
  });

  it('gives the same answer for an absent cell and one stored fresh', () => {
    const stored = withStrength(ledger, newStrength('cell_d'));
    expect(strengthFor(stored, 'cell_d')).toEqual(strengthFor(ledger, 'cell_d'));
  });
});

describe('withStrength and forgetCell', () => {
  it('adds without touching the original', () => {
    const grown = withStrength(ledger, seen('cell_d', 1));
    expect(ledgerSize(grown)).toBe(4);
    expect(ledgerSize(ledger)).toBe(3);
  });

  it('replaces a strength that was already there', () => {
    const changed = withStrength(ledger, seen('cell_a', 3));
    expect(strengthFor(changed, 'cell_a').rung).toBe(2);
    expect(ledgerSize(changed)).toBe(3);
  });

  it('takes several at once', () => {
    const grown = withStrengths(ledger, [seen('cell_d', 1), seen('cell_e', 1)]);
    expect(ledgerSize(grown)).toBe(5);
  });

  it('forgets a cell and tolerates one it never had', () => {
    expect(ledgerSize(forgetCell(ledger, 'cell_a'))).toBe(2);
    expect(forgetCell(ledger, 'cell_z')).toBe(ledger);
  });
});

describe('dueCells', () => {
  it('takes only the ones that have come round', () => {
    expect(dueCells(ledger, cells, d('2025-03-04')).map((s) => s.cellId)).toEqual(['cell_a']);
  });

  it('sorts the most overdue first', () => {
    const mixed = withStrength(ledger, recordCorrect(newStrength('cell_d'), flat, d('2025-03-01')));
    expect(dueCells(mixed, cells, d('2025-03-05')).map((s) => s.cellId)).toEqual([
      'cell_d',
      'cell_a',
    ]);
  });

  it('leaves out a suspended cell however overdue', () => {
    expect(dueCells(ledger, cells, d('2030-01-01')).map((s) => s.cellId)).not.toContain('cell_c');
  });

  it('leaves out a cell nobody has answered', () => {
    expect(dueCells(ledger, cells, d('2030-01-01')).map((s) => s.cellId)).not.toContain('cell_d');
  });

  it('only looks at the cells it was asked about', () => {
    expect(dueCells(ledger, ['cell_b'], d('2030-01-01')).map((s) => s.cellId)).toEqual(['cell_b']);
  });
});

describe('newCells and suspendedCells', () => {
  it('names the cells that have never been answered', () => {
    expect(newCells(ledger, cells).map((s) => s.cellId)).toEqual(['cell_d']);
  });

  it('names the suspended cells', () => {
    expect(suspendedCells(ledger, cells).map((s) => s.cellId)).toEqual(['cell_c']);
  });

  it('counts a suspended cell as neither new nor due', () => {
    expect(newCells(ledger, cells).map((s) => s.cellId)).not.toContain('cell_c');
  });
});

describe('ledgerCounts', () => {
  it('splits the cells into the five buckets', () => {
    const counts = ledgerCounts(ledger, cells, flat, d('2025-03-04'));
    expect(counts).toEqual({
      known: 4,
      introduced: 2,
      due: 1,
      mature: 1,
      suspended: 1,
      untouched: 1,
    });
  });

  it('counts nothing for an empty candidate list', () => {
    expect(ledgerCounts(ledger, [], flat, DAY).known).toBe(0);
  });

  it('counts every cell as untouched for a fresh ledger', () => {
    const counts = ledgerCounts(emptyLedger('lrn_one'), cells, flat, DAY);
    expect(counts.untouched).toBe(4);
    expect(counts.introduced).toBe(0);
  });
});

describe('nextDueDate', () => {
  it('finds the soonest due day', () => {
    expect(nextDueDate(ledger, cells)).toBe('2025-03-04');
  });

  it('ignores suspended and untouched cells', () => {
    expect(nextDueDate(ledger, ['cell_c', 'cell_d'])).toBeUndefined();
  });

  it('gives nothing for an empty ledger', () => {
    expect(nextDueDate(emptyLedger('lrn_one'), cells)).toBeUndefined();
  });
});
