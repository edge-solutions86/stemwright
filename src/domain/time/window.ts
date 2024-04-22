import { ValidationError } from '../errors.js';
import type { CivilDate } from './civil.js';
import {
  addDays,
  compareCivil,
  daysBetween,
  fromEpochDay,
  parseCivilDate,
  maxCivil,
  minCivil,
  toEpochDay,
} from './civil.js';

/**
 * A closed range of study days. Both ends are inclusive, which is what an
 * assignment means when it says "week of the fourth through the tenth", and it
 * removes the empty range as a representable value.
 */
export interface DayRange {
  readonly from: CivilDate;
  readonly to: CivilDate;
}

export function dayRange(from: CivilDate, to: CivilDate): DayRange {
  if (compareCivil(from, to) > 0) {
    throw ValidationError.field('range', `starts after it ends: ${from} to ${to}`);
  }
  return { from, to };
}

export function singleDay(day: CivilDate): DayRange {
  return { from: day, to: day };
}

export function rangeLength(range: DayRange): number {
  return daysBetween(range.from, range.to) + 1;
}

export function rangeContains(range: DayRange, day: CivilDate): boolean {
  return compareCivil(range.from, day) <= 0 && compareCivil(day, range.to) <= 0;
}

export function rangesOverlap(a: DayRange, b: DayRange): boolean {
  return compareCivil(a.from, b.to) <= 0 && compareCivil(b.from, a.to) <= 0;
}

export function intersectRanges(a: DayRange, b: DayRange): DayRange | undefined {
  if (!rangesOverlap(a, b)) {
    return undefined;
  }
  return { from: maxCivil(a.from, b.from), to: minCivil(a.to, b.to) };
}

/** The smallest range holding both, gaps included. Used for report headers. */
export function hullOfRanges(a: DayRange, b: DayRange): DayRange {
  return { from: minCivil(a.from, b.from), to: maxCivil(a.to, b.to) };
}

export function shiftRange(range: DayRange, days: number): DayRange {
  return { from: addDays(range.from, days), to: addDays(range.to, days) };
}

export function* eachDay(range: DayRange): Generator<CivilDate> {
  const last = toEpochDay(range.to);
  for (let day = toEpochDay(range.from); day <= last; day += 1) {
    yield fromEpochDay(day);
  }
}

export function daysOf(range: DayRange): CivilDate[] {
  return [...eachDay(range)];
}

export function formatRange(range: DayRange): string {
  return range.from === range.to ? range.from : `${range.from}/${range.to}`;
}

export function parseRange(value: string, path = 'range'): DayRange {
  const parts = value.split('/');
  if (parts.length === 1) {
    return singleDay(parseCivilDate(parts[0]!, path));
  }
  if (parts.length !== 2) {
    throw ValidationError.field(path, `is not a day or a day pair: ${value}`);
  }
  return dayRange(
    parseCivilDate(parts[0]!, `${path}.from`),
    parseCivilDate(parts[1]!, `${path}.to`),
  );
}

/**
 * Merges overlapping or touching ranges into the smallest covering set, sorted.
 * Shield windows and enrolment windows both arrive as unsorted lists that may
 * abut, and a queue asking "is this day shielded" wants one pass over these.
 */
export function normaliseRanges(ranges: readonly DayRange[]): DayRange[] {
  if (ranges.length === 0) {
    return [];
  }
  const sorted = [...ranges].sort((a, b) => compareCivil(a.from, b.from));
  const merged: DayRange[] = [];
  let current = sorted[0]!;
  for (const next of sorted.slice(1)) {
    const touches = daysBetween(current.to, next.from) <= 1;
    if (touches) {
      current = { from: current.from, to: maxCivil(current.to, next.to) };
    } else {
      merged.push(current);
      current = next;
    }
  }
  merged.push(current);
  return merged;
}

export function coveredBy(ranges: readonly DayRange[], day: CivilDate): boolean {
  return ranges.some((range) => rangeContains(range, day));
}

export function subtractRange(range: DayRange, cut: DayRange): DayRange[] {
  const overlap = intersectRanges(range, cut);
  if (overlap === undefined) {
    return [range];
  }
  const out: DayRange[] = [];
  if (compareCivil(range.from, overlap.from) < 0) {
    out.push({ from: range.from, to: addDays(overlap.from, -1) });
  }
  if (compareCivil(overlap.to, range.to) < 0) {
    out.push({ from: addDays(overlap.to, 1), to: range.to });
  }
  return out;
}
