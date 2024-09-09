import { describe, expect, it } from 'vitest';
import {
  NOTHING_BURIED,
  blockedByBurial,
  burialSize,
  buryCell,
  isBuried,
  respectsSpacing,
  rowKeyOf,
  survivingBurial,
  twinCellsOf,
  unbury,
  valueInRow,
} from '../../src/domain/drill/burial.js';
import { deck } from '../../src/domain/drill/deck.js';
import { resolveMembership } from '../../src/domain/drill/membership.js';
import { cellIdFor } from '../../src/domain/paradigm/cell.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { drillContext, puella } from '../support/drill.js';
import { latinNoun } from '../support/profiles.js';

const d = (value: string) => parseCivilDate(value);
const TERM = d('2024-10-01');
const LATER = d('2025-03-01');

const membership = resolveMembership(drillContext, deck(latinNoun, { key: 'all' }), TERM);
const cellOf = (lexemeId: string, bundle: string) =>
  membership.cells.find((cell) => cell.lexemeId === lexemeId && cell.bundle === bundle)!;

const genSg = cellOf(puella.id, 'case=gen;number=sg');
const datSg = cellOf(puella.id, 'case=dat;number=sg');
const accSg = cellOf(puella.id, 'case=acc;number=sg');

describe('twinCellsOf', () => {
  it('names the cells the syncretism map ties to this one', () => {
    const twins = twinCellsOf(drillContext, genSg, TERM);
    expect(twins).toContain(cellIdFor(puella.id, 'case=dat;number=sg'));
    expect(twins).toContain(cellIdFor(puella.id, 'case=nom;number=pl'));
    expect(twins).toHaveLength(2);
  });

  it('never names the cell itself', () => {
    expect(twinCellsOf(drillContext, genSg, TERM)).not.toContain(genSg.id);
  });

  it('gives nothing for a cell in no group', () => {
    expect(twinCellsOf(drillContext, accSg, TERM)).toEqual([]);
  });

  it('gives nothing before the profile was published', () => {
    expect(twinCellsOf(drillContext, genSg, d('2024-01-01'))).toEqual([]);
  });

  it('reads a different set once a later revision is in force', () => {
    const bellumNomSg = membership.cells.find(
      (cell) => cell.bundle === 'case=nom;number=sg' && cell.lexemeId !== puella.id,
    )!;
    expect(twinCellsOf(drillContext, bellumNomSg, TERM)).toEqual([]);
    expect(twinCellsOf(drillContext, bellumNomSg, LATER)).toHaveLength(1);
  });
});

describe('buryCell', () => {
  it('buries the cell and its twins', () => {
    const set = buryCell(drillContext, NOTHING_BURIED, genSg, TERM);
    expect(isBuried(set, genSg.id)).toBe(true);
    expect(isBuried(set, datSg.id)).toBe(true);
    expect(burialSize(set)).toBe(3);
  });

  it('buries only the cell itself when it has no twins', () => {
    const set = buryCell(drillContext, NOTHING_BURIED, accSg, TERM);
    expect(burialSize(set)).toBe(1);
  });

  it('accumulates across several answers', () => {
    const once = buryCell(drillContext, NOTHING_BURIED, genSg, TERM);
    const twice = buryCell(drillContext, once, accSg, TERM);
    expect(burialSize(twice)).toBe(4);
  });

  it('leaves the previous set alone', () => {
    buryCell(drillContext, NOTHING_BURIED, genSg, TERM);
    expect(burialSize(NOTHING_BURIED)).toBe(0);
  });

  it('unburies one cell and tolerates one that was never buried', () => {
    const set = buryCell(drillContext, NOTHING_BURIED, genSg, TERM);
    expect(burialSize(unbury(set, datSg.id))).toBe(2);
    expect(unbury(set, 'cell_zzzzzz')).toBe(set);
  });
});

describe('blockedByBurial and survivingBurial', () => {
  const set = buryCell(drillContext, NOTHING_BURIED, genSg, TERM);
  const candidates = [genSg, datSg, accSg];

  it('splits the candidates into blocked and surviving', () => {
    expect(blockedByBurial(set, candidates).map((cell) => cell.bundle)).toEqual([
      'case=gen;number=sg',
      'case=dat;number=sg',
    ]);
    expect(survivingBurial(set, candidates).map((cell) => cell.bundle)).toEqual([
      'case=acc;number=sg',
    ]);
  });

  it('lets everything through when nothing is buried', () => {
    expect(survivingBurial(NOTHING_BURIED, candidates)).toHaveLength(3);
  });
});

describe('rowKeyOf', () => {
  it('drops the dimension the row runs across', () => {
    expect(rowKeyOf(latinNoun, genSg, 'number')).toBe(`${puella.id}|case=gen`);
  });

  it('puts two cells of one row under the same key', () => {
    const genPl = cellOf(puella.id, 'case=gen;number=pl');
    expect(rowKeyOf(latinNoun, genSg, 'number')).toBe(rowKeyOf(latinNoun, genPl, 'number'));
  });

  it('keeps two lexemes apart even in the same row', () => {
    const otherGenSg = membership.cells.find(
      (cell) => cell.bundle === 'case=gen;number=sg' && cell.lexemeId !== puella.id,
    )!;
    expect(rowKeyOf(latinNoun, genSg, 'number')).not.toBe(
      rowKeyOf(latinNoun, otherGenSg, 'number'),
    );
  });

  it('reads the value the row runs across', () => {
    expect(valueInRow(latinNoun, genSg, 'number')).toBe('sg');
    expect(valueInRow(latinNoun, genSg, 'gender')).toBeUndefined();
  });
});

describe('respectsSpacing', () => {
  const genPl = cellOf(puella.id, 'case=gen;number=pl');

  it('is always true when the gap is zero', () => {
    expect(respectsSpacing(latinNoun, [genSg], genPl, 'number', 0)).toBe(true);
  });

  it('refuses a cell whose row is inside the window', () => {
    expect(respectsSpacing(latinNoun, [genSg], genPl, 'number', 2)).toBe(false);
  });

  it('allows it once the row has slid out of the window', () => {
    const asked = [genSg, accSg, datSg];
    expect(respectsSpacing(latinNoun, asked, genPl, 'number', 2)).toBe(true);
  });

  it('allows a row that has not been asked at all', () => {
    expect(respectsSpacing(latinNoun, [accSg], genPl, 'number', 5)).toBe(true);
  });

  it('allows anything when nothing has been asked yet', () => {
    expect(respectsSpacing(latinNoun, [], genPl, 'number', 5)).toBe(true);
  });
});
