import { describe, expect, it } from 'vitest';
import { NOTHING_BURIED, buryCell } from '../../src/domain/drill/burial.js';
import { deck } from '../../src/domain/drill/deck.js';
import { resolveMembership } from '../../src/domain/drill/membership.js';
import { drillPolicy } from '../../src/domain/drill/policy.js';
import {
  assembleQueue,
  excludedFor,
  newCount,
  queueCells,
  queueLength,
  reviewCount,
  whyExcluded,
} from '../../src/domain/drill/queue.js';
import { ladder } from '../../src/domain/mastery/ladder.js';
import { buildLedger, emptyLedger } from '../../src/domain/mastery/ledger.js';
import {
  newStrength,
  recordCorrect,
  recordLapse,
  suspend,
} from '../../src/domain/mastery/strength.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { drillContext, puella } from '../support/drill.js';
import { latinNoun } from '../support/profiles.js';

const d = (value: string) => parseCivilDate(value);
const TERM = d('2024-10-01');
const flat = ladder({ key: 'flat', rungs: [1, 3, 7], spread: 0 });

const membership = resolveMembership(drillContext, deck(latinNoun, { key: 'all' }), TERM);
const cellOf = (bundle: string, lexemeId = puella.id) =>
  membership.cells.find((cell) => cell.lexemeId === lexemeId && cell.bundle === bundle)!;

const seenOn = (cellId: string, day: string) => recordCorrect(newStrength(cellId), flat, d(day));

describe('assembleQueue', () => {
  it('introduces new cells up to the budget when nothing is due', () => {
    const queue = assembleQueue(
      drillContext,
      membership,
      drillPolicy({ newPerDay: 4, perLexemeCap: 10, rowSpacing: 0 }),
      emptyLedger('lrn_one'),
      TERM,
    );
    expect(queueLength(queue)).toBe(4);
    expect(newCount(queue)).toBe(4);
    expect(reviewCount(queue)).toBe(0);
  });

  it('marks the cells the new budget left out', () => {
    const queue = assembleQueue(
      drillContext,
      membership,
      drillPolicy({ newPerDay: 2, perLexemeCap: 10, rowSpacing: 0 }),
      emptyLedger('lrn_one'),
      TERM,
    );
    expect(excludedFor(queue, 'new-budget')).toHaveLength(28);
  });

  it('puts reviews before new cells', () => {
    const ledger = buildLedger('lrn_one', [seenOn(cellOf('case=gen;number=sg').id, '2024-09-20')]);
    const queue = assembleQueue(
      drillContext,
      membership,
      drillPolicy({ newPerDay: 2, perLexemeCap: 10, rowSpacing: 0 }),
      ledger,
      TERM,
    );
    expect(queue.entries[0]?.kind).toBe('review');
    expect(queue.entries[0]?.cell.bundle).toBe('case=gen;number=sg');
  });

  it('sorts reviews with the most overdue first', () => {
    const ledger = buildLedger('lrn_one', [
      seenOn(cellOf('case=gen;number=sg').id, '2024-09-28'),
      seenOn(cellOf('case=dat;number=sg').id, '2024-09-10'),
    ]);
    const queue = assembleQueue(
      drillContext,
      membership,
      drillPolicy({ newPerDay: 0, perLexemeCap: 10, rowSpacing: 0 }),
      ledger,
      TERM,
    );
    expect(queueCells(queue).map((cell) => cell.bundle)).toEqual([
      'case=dat;number=sg',
      'case=gen;number=sg',
    ]);
    expect(queue.entries[0]?.overdueDays).toBe(20);
  });

  it('honours the review budget separately from the new budget', () => {
    const ledger = buildLedger('lrn_one', [
      seenOn(cellOf('case=gen;number=sg').id, '2024-09-20'),
      seenOn(cellOf('case=dat;number=sg').id, '2024-09-20'),
      seenOn(cellOf('case=acc;number=sg').id, '2024-09-20'),
    ]);
    const queue = assembleQueue(
      drillContext,
      membership,
      drillPolicy({ newPerDay: 1, reviewsPerDay: 2, perLexemeCap: 10, rowSpacing: 0 }),
      ledger,
      TERM,
    );
    expect(reviewCount(queue)).toBe(2);
    expect(newCount(queue)).toBe(1);
    expect(excludedFor(queue, 'review-budget')).toHaveLength(1);
  });
});

describe('caps and spacing', () => {
  it('stops taking cells of one lexeme at the cap', () => {
    const queue = assembleQueue(
      drillContext,
      membership,
      drillPolicy({ newPerDay: 30, perLexemeCap: 2, rowSpacing: 0 }),
      emptyLedger('lrn_one'),
      TERM,
    );
    const perLexeme = new Map<string, number>();
    for (const cell of queueCells(queue)) {
      perLexeme.set(cell.lexemeId, (perLexeme.get(cell.lexemeId) ?? 0) + 1);
    }
    expect([...perLexeme.values()].every((count) => count <= 2)).toBe(true);
    expect(excludedFor(queue, 'lexeme-cap').length).toBeGreaterThan(0);
  });

  it('keeps two cells of one row apart', () => {
    const queue = assembleQueue(
      drillContext,
      membership,
      drillPolicy({ newPerDay: 30, perLexemeCap: 10, rowSpacing: 2 }),
      emptyLedger('lrn_one'),
      TERM,
    );
    const rows = queueCells(queue).map((cell) => `${cell.lexemeId}|${cell.bundle.split(';')[0]!}`);
    for (let index = 1; index < rows.length; index += 1) {
      expect(rows[index]).not.toBe(rows[index - 1]);
    }
  });

  it('takes the row dimension it is told to use', () => {
    const queue = assembleQueue(
      drillContext,
      membership,
      drillPolicy({ newPerDay: 30, perLexemeCap: 10, rowSpacing: 3 }),
      emptyLedger('lrn_one'),
      TERM,
      { rowAcross: 'case' },
    );
    expect(excludedFor(queue, 'row-spacing').length).toBeGreaterThan(0);
  });
});

describe('exclusions', () => {
  it('leaves out a suspended cell and says so', () => {
    const target = cellOf('case=gen;number=sg');
    const ledger = buildLedger('lrn_one', [
      suspend(recordLapse(newStrength(target.id), flat, TERM)),
    ]);
    const queue = assembleQueue(
      drillContext,
      membership,
      drillPolicy({ newPerDay: 30, perLexemeCap: 10, rowSpacing: 0 }),
      ledger,
      TERM,
    );
    expect(whyExcluded(queue, target.id)).toBe('suspended');
    expect(queueCells(queue)).not.toContainEqual(target);
  });

  it('leaves out a buried cell and says so', () => {
    const target = cellOf('case=gen;number=sg');
    const buried = buryCell(drillContext, NOTHING_BURIED, target, TERM);
    const queue = assembleQueue(
      drillContext,
      membership,
      drillPolicy({ newPerDay: 30, perLexemeCap: 10, rowSpacing: 0 }),
      emptyLedger('lrn_one'),
      TERM,
      { buried },
    );
    expect(whyExcluded(queue, target.id)).toBe('buried');
    expect(excludedFor(queue, 'buried')).toHaveLength(3);
  });

  it('empties the whole queue on a rest day and says why', () => {
    const queue = assembleQueue(
      drillContext,
      membership,
      drillPolicy({ restDays: [2] }),
      emptyLedger('lrn_one'),
      TERM,
    );
    expect(queueLength(queue)).toBe(0);
    expect(excludedFor(queue, 'rest-day')).toHaveLength(30);
  });

  it('marks a cell that is neither due nor new as not due', () => {
    const target = cellOf('case=gen;number=sg');
    const ledger = buildLedger('lrn_one', [seenOn(target.id, '2024-10-01')]);
    const queue = assembleQueue(
      drillContext,
      membership,
      drillPolicy({ newPerDay: 0, perLexemeCap: 10, rowSpacing: 0 }),
      ledger,
      TERM,
    );
    expect(whyExcluded(queue, target.id)).toBe('not-due');
  });

  it('accounts for every cell of the membership', () => {
    const queue = assembleQueue(
      drillContext,
      membership,
      drillPolicy({ newPerDay: 3, perLexemeCap: 10, rowSpacing: 0 }),
      emptyLedger('lrn_one'),
      TERM,
    );
    expect(queueLength(queue) + queue.excluded.size).toBe(membership.cells.length);
  });
});

describe('queue metadata', () => {
  it('carries the day and the deck it was built for', () => {
    const queue = assembleQueue(
      drillContext,
      membership,
      drillPolicy(),
      emptyLedger('lrn_one'),
      TERM,
    );
    expect(queue.on).toBe('2024-10-01');
    expect(queue.deckKey).toBe('all');
  });

  it('is empty when the membership is', () => {
    const nothing = resolveMembership(
      drillContext,
      deck(latinNoun, { key: 'none', scope: { cells: 'case=nom,case!=nom' } }),
      TERM,
    );
    const queue = assembleQueue(drillContext, nothing, drillPolicy(), emptyLedger('lrn_one'), TERM);
    expect(queueLength(queue)).toBe(0);
    expect(queue.excluded.size).toBe(0);
  });
});
