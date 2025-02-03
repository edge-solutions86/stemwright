import { ValidationError } from '../domain/errors.js';
import type { FeatureBundle } from '../domain/features/bundle.js';
import { unsafeBundle } from '../domain/features/bundle.js';
import type { Selector } from '../domain/features/selector.js';
import type { StemSet } from '../domain/lexicon/stem.js';
import type { CivilDate } from '../domain/time/civil.js';
import { parseCivilDate } from '../domain/time/civil.js';
import type { DayRange } from '../domain/time/window.js';
import { dayRange } from '../domain/time/window.js';

/**
 * Turning domain values into json and back.
 *
 * Decoding is deliberately suspicious. Everything it reads came off a disk that
 * a previous version of this code wrote, and the one thing that is certain
 * about stored data is that some of it was written by a version that no longer
 * exists. So every decoder checks the shape it was handed rather than trusting
 * the type annotation, and a bad row fails with the field that was wrong rather
 * than with a type error three layers up.
 */
export type Json = string | number | boolean | null | Json[] | { [key: string]: Json };

export function isObject(value: unknown): value is Record<string, Json> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

export function requireObject(path: string, value: unknown): Record<string, Json> {
  if (!isObject(value)) {
    throw ValidationError.field(path, 'is not an object');
  }
  return value;
}

export function readString(row: Record<string, Json>, path: string, key: string): string {
  const value = row[key];
  if (typeof value !== 'string') {
    throw ValidationError.field(`${path}.${key}`, 'is not a string');
  }
  return value;
}

export function readOptionalString(
  row: Record<string, Json>,
  path: string,
  key: string,
): string | undefined {
  const value = row[key];
  if (value === undefined || value === null) {
    return undefined;
  }
  if (typeof value !== 'string') {
    throw ValidationError.field(`${path}.${key}`, 'is not a string');
  }
  return value;
}

export function readNumber(row: Record<string, Json>, path: string, key: string): number {
  const value = row[key];
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    throw ValidationError.field(`${path}.${key}`, 'is not a finite number');
  }
  return value;
}

export function readOptionalNumber(
  row: Record<string, Json>,
  path: string,
  key: string,
): number | undefined {
  const value = row[key];
  if (value === undefined || value === null) {
    return undefined;
  }
  return readNumber(row, path, key);
}

export function readBoolean(row: Record<string, Json>, path: string, key: string): boolean {
  const value = row[key];
  if (typeof value !== 'boolean') {
    throw ValidationError.field(`${path}.${key}`, 'is not a boolean');
  }
  return value;
}

export function readArray(row: Record<string, Json>, path: string, key: string): Json[] {
  const value = row[key];
  if (!Array.isArray(value)) {
    throw ValidationError.field(`${path}.${key}`, 'is not an array');
  }
  return value;
}

export function readStringArray(row: Record<string, Json>, path: string, key: string): string[] {
  return readArray(row, path, key).map((entry, index) => {
    if (typeof entry !== 'string') {
      throw ValidationError.field(`${path}.${key}.${index}`, 'is not a string');
    }
    return entry;
  });
}

export function readDate(row: Record<string, Json>, path: string, key: string): CivilDate {
  return parseCivilDate(readString(row, path, key), `${path}.${key}`);
}

export function readOptionalDate(
  row: Record<string, Json>,
  path: string,
  key: string,
): CivilDate | undefined {
  const value = readOptionalString(row, path, key);
  return value === undefined ? undefined : parseCivilDate(value, `${path}.${key}`);
}

export function encodeRange(range: DayRange): Json {
  return { from: range.from, to: range.to };
}

export function decodeRange(value: unknown, path: string): DayRange {
  const row = requireObject(path, value);
  return dayRange(readDate(row, path, 'from'), readDate(row, path, 'to'));
}

/**
 * A bundle is stored as its canonical key rather than as a list of pairs. It is
 * shorter, it is the same string the cell id was derived from, and it cannot
 * drift out of the order the schema declares.
 */
export function encodeBundle(bundle: FeatureBundle): Json {
  return bundle.pairs.map((pair) => `${pair.dimension}=${pair.value}`).join(';');
}

export function decodeBundle(value: unknown, path: string): FeatureBundle {
  if (typeof value !== 'string') {
    throw ValidationError.field(path, 'is not a bundle key');
  }
  if (value === '') {
    return unsafeBundle([]);
  }
  return unsafeBundle(
    value.split(';').map((part) => {
      const at = part.indexOf('=');
      if (at <= 0) {
        throw ValidationError.field(path, `is not a dimension value pair: ${part}`);
      }
      return { dimension: part.slice(0, at), value: part.slice(at + 1) };
    }),
  );
}

export function encodeStems(stems: StemSet): Json {
  return stems.stems.map((entry) => ({
    role: entry.role,
    form: entry.form,
    suppletive: entry.suppletive,
    ...(entry.note === undefined ? {} : { note: entry.note }),
  }));
}

export function encodeSelector(selection: Selector): Json {
  return selection.clauses.map((clause) => ({
    dimension: clause.dimension,
    values: [...clause.values],
    negated: clause.negated,
  }));
}

export function decodeSelectorClauses(
  value: unknown,
  path: string,
): { dimension: string; values: string[]; negated: boolean }[] {
  if (!Array.isArray(value)) {
    throw ValidationError.field(path, 'is not an array of clauses');
  }
  return value.map((entry, index) => {
    const row = requireObject(`${path}.${index}`, entry);
    return {
      dimension: readString(row, `${path}.${index}`, 'dimension'),
      values: readStringArray(row, `${path}.${index}`, 'values'),
      negated: readBoolean(row, `${path}.${index}`, 'negated'),
    };
  });
}
