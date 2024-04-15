import { ValidationError } from '../errors.js';

/**
 * A calendar day with no zone attached. Study days are civil days: a learner in
 * Lisbon and a learner in Seoul both get a queue for "the fourth of March", and
 * the instant that boundary falls on is resolved later, once per learner.
 */
export type CivilDate = string & { readonly __civilDate: true };

const PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;
const DAY_MS = 86_400_000;

export function isLeapYear(year: number): boolean {
  return (year % 4 === 0 && year % 100 !== 0) || year % 400 === 0;
}

export function daysInMonth(year: number, month: number): number {
  switch (month) {
    case 1:
    case 3:
    case 5:
    case 7:
    case 8:
    case 10:
    case 12:
      return 31;
    case 4:
    case 6:
    case 9:
    case 11:
      return 30;
    case 2:
      return isLeapYear(year) ? 29 : 28;
    default:
      throw ValidationError.field('month', `is not a month: ${month}`);
  }
}

export function civilDate(year: number, month: number, day: number): CivilDate {
  if (!Number.isInteger(year) || year < 1583 || year > 9999) {
    throw ValidationError.field('year', `is out of range: ${year}`);
  }
  if (!Number.isInteger(month) || month < 1 || month > 12) {
    throw ValidationError.field('month', `is out of range: ${month}`);
  }
  const last = daysInMonth(year, month);
  if (!Number.isInteger(day) || day < 1 || day > last) {
    throw ValidationError.field('day', `is out of range for ${year}-${month}: ${day}`);
  }
  return format(year, month, day);
}

function format(year: number, month: number, day: number): CivilDate {
  const y = String(year).padStart(4, '0');
  const m = String(month).padStart(2, '0');
  const d = String(day).padStart(2, '0');
  return `${y}-${m}-${d}` as CivilDate;
}

export function parseCivilDate(value: string, path = 'date'): CivilDate {
  const match = PATTERN.exec(value);
  if (match === null) {
    throw ValidationError.field(path, `is not an iso calendar date: ${value}`);
  }
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  try {
    return civilDate(year, month, day);
  } catch {
    throw ValidationError.field(path, `is not a real calendar date: ${value}`);
  }
}

export function isCivilDate(value: unknown): value is CivilDate {
  if (typeof value !== 'string') {
    return false;
  }
  try {
    parseCivilDate(value);
    return true;
  } catch {
    return false;
  }
}

export interface CivilParts {
  readonly year: number;
  readonly month: number;
  readonly day: number;
}

export function partsOf(date: CivilDate): CivilParts {
  const match = PATTERN.exec(date);
  if (match === null) {
    throw ValidationError.field('date', `is not an iso calendar date: ${date}`);
  }
  return { year: Number(match[1]), month: Number(match[2]), day: Number(match[3]) };
}

/** Days since 1970-01-01, computed through UTC so no local zone can shift it. */
export function toEpochDay(date: CivilDate): number {
  const { year, month, day } = partsOf(date);
  return Math.round(Date.UTC(year, month - 1, day) / DAY_MS);
}

export function fromEpochDay(epochDay: number): CivilDate {
  if (!Number.isInteger(epochDay)) {
    throw ValidationError.field('epochDay', `must be a whole number of days: ${epochDay}`);
  }
  const utc = new Date(epochDay * DAY_MS);
  return format(utc.getUTCFullYear(), utc.getUTCMonth() + 1, utc.getUTCDate());
}

export function addDays(date: CivilDate, days: number): CivilDate {
  return fromEpochDay(toEpochDay(date) + days);
}

export function daysBetween(from: CivilDate, to: CivilDate): number {
  return toEpochDay(to) - toEpochDay(from);
}

export function compareCivil(a: CivilDate, b: CivilDate): number {
  return a < b ? -1 : a > b ? 1 : 0;
}

export function minCivil(a: CivilDate, b: CivilDate): CivilDate {
  return a <= b ? a : b;
}

export function maxCivil(a: CivilDate, b: CivilDate): CivilDate {
  return a >= b ? a : b;
}

/** Monday is 1 and Sunday is 7, matching iso 8601 rather than the Date api. */
export function weekday(date: CivilDate): number {
  const shifted = (((toEpochDay(date) + 3) % 7) + 7) % 7;
  return shifted + 1;
}

export function startOfWeek(date: CivilDate): CivilDate {
  return addDays(date, 1 - weekday(date));
}

export function addMonths(date: CivilDate, months: number): CivilDate {
  const { year, month, day } = partsOf(date);
  const zeroBased = year * 12 + (month - 1) + months;
  const targetYear = Math.floor(zeroBased / 12);
  const targetMonth = zeroBased - targetYear * 12 + 1;
  const clamped = Math.min(day, daysInMonth(targetYear, targetMonth));
  return civilDate(targetYear, targetMonth, clamped);
}

export function today(now: Date = new Date()): CivilDate {
  return format(now.getUTCFullYear(), now.getUTCMonth() + 1, now.getUTCDate());
}
