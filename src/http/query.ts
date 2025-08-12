import { ValidationError } from '../domain/errors.js';
import type { CivilDate } from '../domain/time/civil.js';
import { parseCivilDate } from '../domain/time/civil.js';
import type { DayRange } from '../domain/time/window.js';
import { dayRange } from '../domain/time/window.js';

/**
 * Reading the query string.
 *
 * Two route modules had their own copy of each of these and they had already
 * drifted: one of them named the missing course `course` and the other named it
 * `courseKey`, so the same mistake reached a client under two different
 * messages depending on which endpoint it hit.
 */
export function dayFrom(
  query: Readonly<Record<string, string>>,
  name = 'on',
): CivilDate | undefined {
  const value = query[name];
  return value === undefined || value === '' ? undefined : parseCivilDate(value, name);
}

export function requireDay(query: Readonly<Record<string, string>>, name = 'on'): CivilDate {
  const value = dayFrom(query, name);
  if (value === undefined) {
    throw ValidationError.field(name, 'must be given as an iso calendar date');
  }
  return value;
}

export function requireCourseKey(query: Readonly<Record<string, string>>): string {
  const value = query['course'];
  if (value === undefined || value === '') {
    throw ValidationError.field('course', 'must be given so permissions can be checked');
  }
  return value;
}

export function rangeFrom(query: Readonly<Record<string, string>>): DayRange {
  const from = query['from'];
  const to = query['to'];
  if (from === undefined || to === undefined) {
    throw ValidationError.field('range', 'needs both a from and a to day');
  }
  return dayRange(parseCivilDate(from, 'from'), parseCivilDate(to, 'to'));
}

export function flagFrom(query: Readonly<Record<string, string>>, name: string): boolean {
  const value = query[name];
  return value === '' || value === 'true' || value === '1';
}

export function positiveFrom(
  query: Readonly<Record<string, string>>,
  name: string,
): number | undefined {
  const value = query[name];
  if (value === undefined || value === '') {
    return undefined;
  }
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < 1) {
    throw ValidationError.field(name, `must be a whole number above zero: ${value}`);
  }
  return parsed;
}
