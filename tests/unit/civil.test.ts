import { describe, expect, it } from 'vitest';
import {
  addDays,
  addMonths,
  civilDate,
  compareCivil,
  daysBetween,
  daysInMonth,
  fromEpochDay,
  isCivilDate,
  isLeapYear,
  maxCivil,
  minCivil,
  parseCivilDate,
  partsOf,
  startOfWeek,
  toEpochDay,
  today,
  weekday,
} from '../../src/domain/time/civil.js';
import { ValidationError } from '../../src/domain/errors.js';

describe('isLeapYear', () => {
  it('follows the gregorian rule at every branch', () => {
    expect(isLeapYear(2024)).toBe(true);
    expect(isLeapYear(2025)).toBe(false);
    expect(isLeapYear(1900)).toBe(false);
    expect(isLeapYear(2000)).toBe(true);
  });
});

describe('daysInMonth', () => {
  it('knows february in both kinds of year', () => {
    expect(daysInMonth(2024, 2)).toBe(29);
    expect(daysInMonth(2023, 2)).toBe(28);
  });

  it('knows the thirty and thirty one day months', () => {
    expect(daysInMonth(2024, 4)).toBe(30);
    expect(daysInMonth(2024, 12)).toBe(31);
  });

  it('refuses a month outside one to twelve', () => {
    expect(() => daysInMonth(2024, 0)).toThrow(ValidationError);
    expect(() => daysInMonth(2024, 13)).toThrow(ValidationError);
  });
});

describe('civilDate', () => {
  it('pads to the iso form', () => {
    expect(civilDate(2024, 4, 8)).toBe('2024-04-08');
  });

  it('refuses a day the month does not have', () => {
    expect(() => civilDate(2023, 2, 29)).toThrow(/day is out of range/);
    expect(() => civilDate(2024, 2, 29)).not.toThrow();
  });

  it('refuses non integers and years outside the gregorian window', () => {
    expect(() => civilDate(2024.5, 1, 1)).toThrow(ValidationError);
    expect(() => civilDate(1500, 1, 1)).toThrow(ValidationError);
    expect(() => civilDate(2024, 1, 1.5)).toThrow(ValidationError);
  });
});

describe('parseCivilDate', () => {
  it('reads a well formed date', () => {
    expect(parseCivilDate('2025-02-28')).toBe('2025-02-28');
  });

  it('refuses a shape that is not the iso one', () => {
    expect(() => parseCivilDate('2025-2-8')).toThrow(/iso calendar date/);
    expect(() => parseCivilDate('2025-02-28T00:00:00Z')).toThrow(/iso calendar date/);
    expect(() => parseCivilDate('')).toThrow(ValidationError);
  });

  it('separates a bad shape from a date that cannot exist', () => {
    expect(() => parseCivilDate('2025-02-30')).toThrow(/not a real calendar date/);
  });

  it('names the path it was given', () => {
    expect(() => parseCivilDate('nope', 'assignment.dueOn')).toThrow(/assignment\.dueOn/);
  });
});

describe('isCivilDate', () => {
  it('is true only for a real iso day', () => {
    expect(isCivilDate('2024-04-08')).toBe(true);
    expect(isCivilDate('2024-04-31')).toBe(false);
    expect(isCivilDate(20240408)).toBe(false);
  });
});

describe('partsOf', () => {
  it('splits the string back into numbers', () => {
    expect(partsOf(civilDate(2025, 12, 31))).toEqual({ year: 2025, month: 12, day: 31 });
  });
});

describe('epoch day round trip', () => {
  it('puts the epoch at zero', () => {
    expect(toEpochDay(civilDate(1970, 1, 1))).toBe(0);
    expect(fromEpochDay(0)).toBe('1970-01-01');
  });

  it('round trips across a leap day and a century boundary', () => {
    for (const date of ['2024-02-28', '2024-02-29', '2024-03-01', '2000-02-29', '1999-12-31']) {
      const parsed = parseCivilDate(date);
      expect(fromEpochDay(toEpochDay(parsed))).toBe(parsed);
    }
  });

  it('refuses a fractional epoch day', () => {
    expect(() => fromEpochDay(1.5)).toThrow(ValidationError);
  });
});

describe('addDays and daysBetween', () => {
  it('crosses a month end', () => {
    expect(addDays(parseCivilDate('2024-01-31'), 1)).toBe('2024-02-01');
  });

  it('crosses a leap day', () => {
    expect(addDays(parseCivilDate('2024-02-28'), 1)).toBe('2024-02-29');
    expect(addDays(parseCivilDate('2023-02-28'), 1)).toBe('2023-03-01');
  });

  it('goes backwards', () => {
    expect(addDays(parseCivilDate('2024-03-01'), -1)).toBe('2024-02-29');
  });

  it('counts the gap in both directions', () => {
    const from = parseCivilDate('2024-04-08');
    const to = parseCivilDate('2024-05-08');
    expect(daysBetween(from, to)).toBe(30);
    expect(daysBetween(to, from)).toBe(-30);
    expect(daysBetween(from, from)).toBe(0);
  });
});

describe('ordering helpers', () => {
  it('compares as calendar order', () => {
    const early = parseCivilDate('2024-04-08');
    const late = parseCivilDate('2024-11-01');
    expect(compareCivil(early, late)).toBe(-1);
    expect(compareCivil(late, early)).toBe(1);
    expect(compareCivil(early, early)).toBe(0);
    expect(minCivil(early, late)).toBe(early);
    expect(maxCivil(early, late)).toBe(late);
  });
});

describe('weekday', () => {
  it('puts monday at one and sunday at seven', () => {
    expect(weekday(parseCivilDate('2024-04-08'))).toBe(1);
    expect(weekday(parseCivilDate('2024-04-14'))).toBe(7);
  });

  it('walks a whole week without repeating', () => {
    const start = parseCivilDate('2024-04-08');
    const seen = new Set<number>();
    for (let offset = 0; offset < 7; offset += 1) {
      seen.add(weekday(addDays(start, offset)));
    }
    expect(seen.size).toBe(7);
  });

  it('stays right well before the epoch', () => {
    expect(weekday(parseCivilDate('1969-07-20'))).toBe(7);
  });
});

describe('startOfWeek', () => {
  it('returns the monday of the containing week', () => {
    expect(startOfWeek(parseCivilDate('2024-04-11'))).toBe('2024-04-08');
    expect(startOfWeek(parseCivilDate('2024-04-08'))).toBe('2024-04-08');
    expect(startOfWeek(parseCivilDate('2024-04-14'))).toBe('2024-04-08');
  });
});

describe('addMonths', () => {
  it('clamps to the last day of a shorter month', () => {
    expect(addMonths(parseCivilDate('2024-01-31'), 1)).toBe('2024-02-29');
    expect(addMonths(parseCivilDate('2023-01-31'), 1)).toBe('2023-02-28');
  });

  it('crosses years in both directions', () => {
    expect(addMonths(parseCivilDate('2024-11-15'), 3)).toBe('2025-02-15');
    expect(addMonths(parseCivilDate('2024-02-15'), -3)).toBe('2023-11-15');
  });
});

describe('today', () => {
  it('reads the utc day off the clock it is handed', () => {
    expect(today(new Date('2025-03-09T23:30:00Z'))).toBe('2025-03-09');
    expect(today(new Date('2025-03-10T00:30:00Z'))).toBe('2025-03-10');
  });
});
