import { describe, expect, it } from 'vitest';
import {
  SLOW_LADDER,
  STANDARD_LADDER,
  clampRung,
  climb,
  describeLadder,
  intervalAt,
  isAtTop,
  ladder,
  lapse,
  rungCount,
  spreadInterval,
  topRung,
} from '../../src/domain/mastery/ladder.js';
import { problemMessages } from '../support/problems.js';

describe('ladder', () => {
  it('keeps the rungs it was given', () => {
    expect(rungCount(STANDARD_LADDER)).toBe(8);
    expect(topRung(STANDARD_LADDER)).toBe(7);
  });

  it('defaults the label, the lapse drop and the spread', () => {
    const built = ladder({ key: 'x', rungs: [1, 2] });
    expect(built.label).toBe('x');
    expect(built.lapseDrop).toBe(2);
    expect(built.spread).toBeCloseTo(0.1);
  });

  it('refuses an empty ladder', () => {
    expect(problemMessages(() => ladder({ key: 'x', rungs: [] }))).toEqual([
      'rungs must hold at least one rung',
    ]);
  });

  it('refuses rungs that do not increase', () => {
    expect(problemMessages(() => ladder({ key: 'x', rungs: [3, 3] }))).toEqual([
      'rungs.1 must be longer than the rung before it: 3',
    ]);
    expect(problemMessages(() => ladder({ key: 'x', rungs: [3, 2] }))).toHaveLength(1);
  });

  it('refuses a rung below a day or a fractional one', () => {
    expect(problemMessages(() => ladder({ key: 'x', rungs: [0] }))).toEqual([
      'rungs.0 must be a whole number of days above zero: 0',
    ]);
    expect(problemMessages(() => ladder({ key: 'x', rungs: [1.5] }))).toHaveLength(1);
  });

  it('refuses a lapse drop below one', () => {
    expect(problemMessages(() => ladder({ key: 'x', rungs: [1, 2], lapseDrop: 0 }))).toEqual([
      'lapseDrop must be a whole number above zero: 0',
    ]);
  });

  it('refuses a spread outside nought to one half', () => {
    expect(problemMessages(() => ladder({ key: 'x', rungs: [1, 2], spread: 0.9 }))).toEqual([
      'spread must be between zero and one half: 0.9',
    ]);
    expect(problemMessages(() => ladder({ key: 'x', rungs: [1, 2], spread: -0.1 }))).toHaveLength(
      1,
    );
  });

  it('accepts a spread of exactly nought or one half', () => {
    expect(ladder({ key: 'x', rungs: [1, 2], spread: 0 }).spread).toBe(0);
    expect(ladder({ key: 'x', rungs: [1, 2], spread: 0.5 }).spread).toBe(0.5);
  });
});

describe('clampRung', () => {
  it('holds a rung inside the ladder', () => {
    expect(clampRung(STANDARD_LADDER, 3)).toBe(3);
  });

  it('clamps below and above', () => {
    expect(clampRung(STANDARD_LADDER, -5)).toBe(0);
    expect(clampRung(STANDARD_LADDER, 99)).toBe(7);
  });

  it('truncates a fractional rung and rescues a broken one', () => {
    expect(clampRung(STANDARD_LADDER, 2.9)).toBe(2);
    expect(clampRung(STANDARD_LADDER, Number.NaN)).toBe(0);
  });
});

describe('intervalAt', () => {
  it('reads the days off the rung', () => {
    expect(intervalAt(STANDARD_LADDER, 0)).toBe(1);
    expect(intervalAt(STANDARD_LADDER, 3)).toBe(16);
  });

  it('clamps rather than falling off either end', () => {
    expect(intervalAt(STANDARD_LADDER, -1)).toBe(1);
    expect(intervalAt(STANDARD_LADDER, 99)).toBe(340);
  });
});

describe('climb and lapse', () => {
  it('climbs one rung at a time', () => {
    expect(climb(STANDARD_LADDER, 0)).toBe(1);
    expect(climb(STANDARD_LADDER, 3)).toBe(4);
  });

  it('stops climbing at the top', () => {
    expect(climb(STANDARD_LADDER, 7)).toBe(7);
    expect(isAtTop(STANDARD_LADDER, 7)).toBe(true);
    expect(isAtTop(STANDARD_LADDER, 6)).toBe(false);
  });

  it('drops by the configured number of rungs', () => {
    expect(lapse(STANDARD_LADDER, 5)).toBe(3);
    expect(lapse(SLOW_LADDER, 5)).toBe(4);
  });

  it('never drops below the bottom', () => {
    expect(lapse(STANDARD_LADDER, 1)).toBe(0);
    expect(lapse(STANDARD_LADDER, 0)).toBe(0);
  });

  it('does not send a long known cell back to the first rung', () => {
    expect(intervalAt(STANDARD_LADDER, lapse(STANDARD_LADDER, 7))).toBe(75);
  });
});

describe('spreadInterval', () => {
  it('is the plain interval when the ladder does not spread', () => {
    const flat = ladder({ key: 'flat', rungs: [1, 3, 7], spread: 0 });
    expect(spreadInterval(flat, 2, 'cell_a')).toBe(7);
  });

  it('leaves a one day interval alone', () => {
    expect(spreadInterval(STANDARD_LADDER, 0, 'cell_a')).toBe(1);
  });

  it('is the same for the same seed', () => {
    expect(spreadInterval(STANDARD_LADDER, 5, 'cell_a')).toBe(
      spreadInterval(STANDARD_LADDER, 5, 'cell_a'),
    );
  });

  it('separates two cells at the same rung', () => {
    const spread = new Set(
      ['a', 'b', 'c', 'd', 'e', 'f'].map((seed) => spreadInterval(STANDARD_LADDER, 6, seed)),
    );
    expect(spread.size).toBeGreaterThan(1);
  });

  it('stays inside the configured swing', () => {
    for (const seed of ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h']) {
      const value = spreadInterval(STANDARD_LADDER, 6, seed);
      expect(value).toBeGreaterThanOrEqual(160 - 16);
      expect(value).toBeLessThanOrEqual(160 + 16);
    }
  });

  it('never returns less than a day', () => {
    const wide = ladder({ key: 'wide', rungs: [1, 2], spread: 0.5 });
    for (const seed of ['a', 'b', 'c', 'd']) {
      expect(spreadInterval(wide, 1, seed)).toBeGreaterThanOrEqual(1);
    }
  });
});

describe('describeLadder', () => {
  it('shows the first rungs and the last one', () => {
    expect(describeLadder(STANDARD_LADDER)).toBe(
      'Standard waits 1, 3, 7, 16, up to 340 days and drops 2 rungs on a lapse',
    );
  });

  it('shows a short ladder in full', () => {
    expect(describeLadder(ladder({ key: 'short', rungs: [1, 4] }))).toBe(
      'short waits 1, 4 days and drops 2 rungs on a lapse',
    );
  });
});
