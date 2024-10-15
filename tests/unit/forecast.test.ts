import { describe, expect, it } from 'vitest';
import { deck } from '../../src/domain/drill/deck.js';
import { resolveMembership } from '../../src/domain/drill/membership.js';
import { drillPolicy } from '../../src/domain/drill/policy.js';
import { ladder } from '../../src/domain/mastery/ladder.js';
import { buildLedger, emptyLedger } from '../../src/domain/mastery/ledger.js';
import { newStrength, recordCorrect, suspend } from '../../src/domain/mastery/strength.js';
import {
  busiestDay,
  daysOverCeiling,
  daysToClearBacklog,
  forecast,
  optimisticProjection,
  spilledOnRestDays,
  totalDue,
} from '../../src/domain/report/forecast.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { dayRange } from '../../src/domain/time/window.js';
import { drillContext, puella } from '../support/drill.js';
import { latinNoun } from '../support/profiles.js';

const d = (value: string) => parseCivilDate(value);
const r = (from: string, to: string) => dayRange(d(from), d(to));
const TERM = d('2024-10-01');
const flat = ladder({ key: 'flat', rungs: [1, 3, 7, 16], spread: 0 });

const membership = resolveMembership(drillContext, deck(latinNoun, { key: 'all' }), TERM);
const cellOf = (bundle: string) =>
  membership.cells.find((cell) => cell.lexemeId === puella.id && cell.bundle === bundle)!;

const seenOn = (cellId: string, day: string) => recordCorrect(newStrength(cellId), flat, d(day));

const ledger = buildLedger('lrn_one', [
  seenOn(cellOf('case=nom;number=sg').id, '2024-10-01'),
  seenOn(cellOf('case=gen;number=sg').id, '2024-10-01'),
  seenOn(cellOf('case=dat;number=sg').id, '2024-10-04'),
]);

describe('forecast', () => {
  it('counts the reviews falling on each day', () => {
    const days = forecast(membership.cells, ledger, r('2024-10-02', '2024-10-06'), drillPolicy());
    expect(days.map((day) => day.due)).toEqual([2, 0, 0, 1, 0]);
  });

  it('covers every day of the range', () => {
    const days = forecast(membership.cells, ledger, r('2024-10-02', '2024-10-06'), drillPolicy());
    expect(days).toHaveLength(5);
    expect(days[0]?.on).toBe('2024-10-02');
  });

  it('folds anything already overdue onto the first day', () => {
    const days = forecast(membership.cells, ledger, r('2024-10-10', '2024-10-12'), drillPolicy());
    expect(days[0]?.due).toBe(3);
  });

  it('ignores suspended cells', () => {
    const target = cellOf('case=nom;number=sg').id;
    const suspended = buildLedger('lrn_one', [suspend(seenOn(target, '2024-10-01'))]);
    const days = forecast(
      membership.cells,
      suspended,
      r('2024-10-01', '2024-10-05'),
      drillPolicy(),
    );
    expect(totalDue(days)).toBe(0);
  });

  it('ignores cells nobody has answered', () => {
    const empty = emptyLedger('lrn_one');
    const days = forecast(membership.cells, empty, r('2024-10-01', '2024-10-05'), drillPolicy());
    expect(totalDue(days)).toBe(0);
  });

  it('marks a day over the review ceiling', () => {
    const policy = drillPolicy({ reviewsPerDay: 1 });
    const days = forecast(membership.cells, ledger, r('2024-10-02', '2024-10-06'), policy);
    expect(daysOverCeiling(days).map((day) => day.on)).toEqual(['2024-10-02']);
  });

  it('marks a rest day and counts what falls on it', () => {
    const policy = drillPolicy({ restDays: [3] });
    const days = forecast(membership.cells, ledger, r('2024-10-02', '2024-10-06'), policy);
    expect(days[0]?.resting).toBe(true);
    expect(spilledOnRestDays(days)).toBe(2);
  });
});

describe('busiestDay', () => {
  it('names the day with the most due', () => {
    const days = forecast(membership.cells, ledger, r('2024-10-02', '2024-10-06'), drillPolicy());
    expect(busiestDay(days)?.on).toBe('2024-10-02');
  });

  it('gives nothing for an empty forecast', () => {
    expect(busiestDay([])).toBeUndefined();
  });
});

describe('optimisticProjection', () => {
  it('introduces new cells at the budget until they run out', () => {
    const policy = drillPolicy({ newPerDay: 4, reviewsPerDay: 100 });
    const days = optimisticProjection(
      membership.cells.slice(0, 6),
      emptyLedger('lrn_one'),
      flat,
      policy,
      r('2024-10-01', '2024-10-04'),
    );
    expect(days.map((day) => day.introductions)).toEqual([4, 2, 0, 0]);
  });

  it('brings each introduction back along the ladder', () => {
    const policy = drillPolicy({ newPerDay: 1, reviewsPerDay: 100 });
    const days = optimisticProjection(
      membership.cells.slice(0, 1),
      emptyLedger('lrn_one'),
      flat,
      policy,
      r('2024-10-01', '2024-10-07'),
    );
    expect(days.map((day) => day.reviews)).toEqual([0, 1, 0, 0, 1, 0, 0]);
  });

  it('does nothing at all on a rest day', () => {
    const policy = drillPolicy({ newPerDay: 2, restDays: [3] });
    const days = optimisticProjection(
      membership.cells.slice(0, 4),
      emptyLedger('lrn_one'),
      flat,
      policy,
      r('2024-10-01', '2024-10-03'),
    );
    expect(days[1]).toEqual({ on: '2024-10-02', reviews: 0, introductions: 0 });
    expect(days[0]?.introductions).toBe(2);
    expect(days[2]?.introductions).toBe(2);
  });

  it('skips a suspended cell entirely', () => {
    const strengths = membership.cells.slice(0, 3).map((cell) => suspend(newStrength(cell.id)));
    const suspended = buildLedger('lrn_one', strengths);
    const days = optimisticProjection(
      membership.cells.slice(0, 3),
      suspended,
      flat,
      drillPolicy(),
      r('2024-10-01', '2024-10-03'),
    );
    expect(days.every((day) => day.introductions === 0 && day.reviews === 0)).toBe(true);
  });

  it('carries an existing due date into the projection', () => {
    const one = buildLedger('lrn_one', [seenOn(cellOf('case=nom;number=sg').id, '2024-10-01')]);
    const days = optimisticProjection(
      [cellOf('case=nom;number=sg')],
      one,
      flat,
      drillPolicy({ newPerDay: 0 }),
      r('2024-10-01', '2024-10-03'),
    );
    expect(days.map((day) => day.reviews)).toEqual([0, 1, 0]);
  });
});

describe('daysToClearBacklog', () => {
  it('finds the first quiet day', () => {
    const policy = drillPolicy({ newPerDay: 2, reviewsPerDay: 100 });
    const days = optimisticProjection(
      membership.cells.slice(0, 2),
      emptyLedger('lrn_one'),
      flat,
      policy,
      r('2024-10-01', '2024-10-05'),
    );
    expect(daysToClearBacklog(days)).toBe(2);
  });

  it('gives the whole span when no day is quiet', () => {
    const days = optimisticProjection(
      membership.cells,
      emptyLedger('lrn_one'),
      flat,
      drillPolicy({ newPerDay: 1 }),
      r('2024-10-01', '2024-10-03'),
    );
    expect(daysToClearBacklog(days)).toBe(3);
  });
});
