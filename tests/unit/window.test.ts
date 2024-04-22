import { describe, expect, it } from 'vitest';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import {
  coveredBy,
  dayRange,
  daysOf,
  eachDay,
  formatRange,
  hullOfRanges,
  intersectRanges,
  normaliseRanges,
  parseRange,
  rangeContains,
  rangeLength,
  rangesOverlap,
  shiftRange,
  singleDay,
  subtractRange,
} from '../../src/domain/time/window.js';
import { ValidationError } from '../../src/domain/errors.js';

const d = (value: string) => parseCivilDate(value);
const r = (from: string, to: string) => dayRange(d(from), d(to));

describe('dayRange', () => {
  it('accepts a single day range', () => {
    expect(rangeLength(r('2024-05-01', '2024-05-01'))).toBe(1);
  });

  it('refuses a range that ends before it starts', () => {
    expect(() => r('2024-05-02', '2024-05-01')).toThrow(ValidationError);
  });

  it('counts both ends in', () => {
    expect(rangeLength(r('2024-05-01', '2024-05-07'))).toBe(7);
  });
});

describe('rangeContains', () => {
  const week = r('2024-05-06', '2024-05-12');

  it('holds both endpoints', () => {
    expect(rangeContains(week, d('2024-05-06'))).toBe(true);
    expect(rangeContains(week, d('2024-05-12'))).toBe(true);
  });

  it('excludes the days either side', () => {
    expect(rangeContains(week, d('2024-05-05'))).toBe(false);
    expect(rangeContains(week, d('2024-05-13'))).toBe(false);
  });
});

describe('overlap and intersection', () => {
  it('counts a shared endpoint as an overlap', () => {
    expect(rangesOverlap(r('2024-05-01', '2024-05-05'), r('2024-05-05', '2024-05-09'))).toBe(true);
  });

  it('does not count adjacency as an overlap', () => {
    expect(rangesOverlap(r('2024-05-01', '2024-05-04'), r('2024-05-05', '2024-05-09'))).toBe(false);
  });

  it('intersects to the tighter pair of bounds', () => {
    expect(intersectRanges(r('2024-05-01', '2024-05-10'), r('2024-05-04', '2024-05-20'))).toEqual(
      r('2024-05-04', '2024-05-10'),
    );
  });

  it('gives nothing when they miss', () => {
    expect(
      intersectRanges(r('2024-05-01', '2024-05-02'), r('2024-05-04', '2024-05-05')),
    ).toBeUndefined();
  });

  it('hulls across the gap', () => {
    expect(hullOfRanges(r('2024-05-01', '2024-05-02'), r('2024-05-20', '2024-05-21'))).toEqual(
      r('2024-05-01', '2024-05-21'),
    );
  });
});

describe('shiftRange', () => {
  it('moves both ends by the same number of days', () => {
    expect(shiftRange(r('2024-02-27', '2024-02-28'), 2)).toEqual(r('2024-02-29', '2024-03-01'));
  });

  it('moves backwards too', () => {
    expect(shiftRange(r('2024-03-01', '2024-03-02'), -1)).toEqual(r('2024-02-29', '2024-03-01'));
  });
});

describe('eachDay', () => {
  it('walks every day inclusive', () => {
    expect(daysOf(r('2024-04-29', '2024-05-02'))).toEqual([
      '2024-04-29',
      '2024-04-30',
      '2024-05-01',
      '2024-05-02',
    ]);
  });

  it('yields exactly one day for a single day range', () => {
    expect([...eachDay(singleDay(d('2024-06-01')))]).toEqual(['2024-06-01']);
  });

  it('yields as many days as the length reports', () => {
    const range = r('2024-01-01', '2024-12-31');
    expect(daysOf(range)).toHaveLength(rangeLength(range));
  });
});

describe('formatting and parsing', () => {
  it('prints a single day without a slash', () => {
    expect(formatRange(singleDay(d('2024-07-04')))).toBe('2024-07-04');
  });

  it('round trips a pair', () => {
    const range = r('2024-07-01', '2024-07-31');
    expect(parseRange(formatRange(range))).toEqual(range);
  });

  it('reads a bare day as a single day range', () => {
    expect(parseRange('2024-07-04')).toEqual(singleDay(d('2024-07-04')));
  });

  it('refuses three parts and names the failing end', () => {
    expect(() => parseRange('2024-01-01/2024-01-02/2024-01-03')).toThrow(/day or a day pair/);
    expect(() => parseRange('2024-01-01/nope', 'window')).toThrow(/window\.to/);
  });
});

describe('normaliseRanges', () => {
  it('returns nothing for nothing', () => {
    expect(normaliseRanges([])).toEqual([]);
  });

  it('sorts and merges overlapping windows', () => {
    const merged = normaliseRanges([r('2024-05-10', '2024-05-12'), r('2024-05-01', '2024-05-11')]);
    expect(merged).toEqual([r('2024-05-01', '2024-05-12')]);
  });

  it('merges windows that only touch', () => {
    const merged = normaliseRanges([r('2024-05-01', '2024-05-04'), r('2024-05-05', '2024-05-06')]);
    expect(merged).toEqual([r('2024-05-01', '2024-05-06')]);
  });

  it('keeps a real gap apart', () => {
    const merged = normaliseRanges([r('2024-05-01', '2024-05-04'), r('2024-05-06', '2024-05-07')]);
    expect(merged).toHaveLength(2);
  });

  it('keeps a window swallowed by an earlier one', () => {
    const merged = normaliseRanges([r('2024-05-01', '2024-05-30'), r('2024-05-10', '2024-05-12')]);
    expect(merged).toEqual([r('2024-05-01', '2024-05-30')]);
  });
});

describe('coveredBy', () => {
  const shields = [r('2024-05-01', '2024-05-03'), r('2024-06-01', '2024-06-02')];

  it('is true inside any window', () => {
    expect(coveredBy(shields, d('2024-05-02'))).toBe(true);
    expect(coveredBy(shields, d('2024-06-02'))).toBe(true);
  });

  it('is false in the gap and outside', () => {
    expect(coveredBy(shields, d('2024-05-20'))).toBe(false);
    expect(coveredBy([], d('2024-05-20'))).toBe(false);
  });
});

describe('subtractRange', () => {
  it('leaves the range alone when the cut misses', () => {
    const range = r('2024-05-01', '2024-05-10');
    expect(subtractRange(range, r('2024-06-01', '2024-06-02'))).toEqual([range]);
  });

  it('splits into two when the cut is inside', () => {
    expect(subtractRange(r('2024-05-01', '2024-05-10'), r('2024-05-04', '2024-05-05'))).toEqual([
      r('2024-05-01', '2024-05-03'),
      r('2024-05-06', '2024-05-10'),
    ]);
  });

  it('trims one end when the cut overhangs', () => {
    expect(subtractRange(r('2024-05-01', '2024-05-10'), r('2024-04-20', '2024-05-03'))).toEqual([
      r('2024-05-04', '2024-05-10'),
    ]);
  });

  it('returns nothing when the cut swallows the range', () => {
    expect(subtractRange(r('2024-05-04', '2024-05-05'), r('2024-05-01', '2024-05-10'))).toEqual([]);
  });
});
