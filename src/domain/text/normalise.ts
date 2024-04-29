import { ValidationError } from '../errors.js';

/**
 * Learner input arrives from a browser field, a csv column or a cli argument,
 * and the three disagree about normalisation form, invisible marks and what
 * counts as a space. Everything that will be compared goes through here first,
 * so a comparison never has to ask where its input came from.
 *
 * Nothing in this file is written as a literal invisible character. Every code
 * point that cannot be seen in an editor is named by its number, because a
 * paste that silently loses one would otherwise change behaviour with no
 * visible diff.
 */

/** Zero width space, zero width non joiner, zero width joiner, word joiner, byte order mark. */
const ZERO_WIDTH = new Set([0x200b, 0x200c, 0x200d, 0x2060, 0xfeff]);

const DELETE = 0x7f;
const UNIT_SEPARATOR = 0x1f;
const TAB = 0x09;
const LINE_FEED = 0x0a;
const CARRIAGE_RETURN = 0x0d;

export type NormalForm = 'NFC' | 'NFD' | 'NFKC' | 'NFKD';

export function toForm(value: string, form: NormalForm): string {
  return value.normalize(form);
}

function isDroppable(point: number): boolean {
  if (ZERO_WIDTH.has(point)) {
    return true;
  }
  if (point === TAB || point === LINE_FEED || point === CARRIAGE_RETURN) {
    return false;
  }
  return point <= UNIT_SEPARATOR || point === DELETE;
}

/** Removes marks the learner cannot see and never meant to type. */
export function stripInvisible(value: string): string {
  let out = '';
  for (const point of value) {
    if (!isDroppable(point.codePointAt(0) ?? 0)) {
      out += point;
    }
  }
  return out;
}

/**
 * Collapses every run of any space character to one plain space and trims.
 * The regular expression class already covers the no break space, the en and em
 * spaces and the ideographic space, which is exactly the set a learner can
 * produce by accident on a phone keyboard.
 */
export function collapseSpaces(value: string): string {
  return value.replace(/\s+/gu, ' ').trim();
}

/**
 * The single entry point. Order matters: invisible marks go first so a zero
 * width joiner cannot survive inside a run of spaces, then spaces collapse,
 * then the result is put in one normalisation form.
 */
export function normaliseInput(value: string, form: NormalForm = 'NFC'): string {
  return toForm(collapseSpaces(stripInvisible(value)), form);
}

export function isBlank(value: string): boolean {
  return normaliseInput(value) === '';
}

export function requireText(path: string, value: string, maxLength = 512): string {
  const cleaned = normaliseInput(value);
  if (cleaned === '') {
    throw ValidationError.field(path, 'must not be blank');
  }
  if (cleaned.length > maxLength) {
    throw ValidationError.field(path, `must be at most ${maxLength} characters`);
  }
  return cleaned;
}

/** Code points, not utf-16 units, so a surrogate pair counts once. */
export function codePoints(value: string): string[] {
  return [...value];
}

export function codePointLength(value: string): number {
  return codePoints(value).length;
}

/**
 * Splits into base plus trailing combining marks. A Greek vowel with a breathing
 * and an accent is one unit to a learner and three code points to the platform,
 * and every tolerance rule in the orthography layer is stated over these units.
 */
export function markClusters(value: string): string[] {
  const clusters: string[] = [];
  for (const point of codePoints(toForm(value, 'NFD'))) {
    if (clusters.length > 0 && /\p{M}/u.test(point)) {
      clusters[clusters.length - 1] += point;
    } else {
      clusters.push(point);
    }
  }
  return clusters;
}

export function baseOf(cluster: string): string {
  return codePoints(cluster)[0] ?? '';
}

export function marksOf(cluster: string): string[] {
  return codePoints(cluster).slice(1);
}

export function hasMarks(value: string): boolean {
  return /\p{M}/u.test(toForm(value, 'NFD'));
}

/** Drops every combining mark, leaving the bare letters. */
export function stripMarks(value: string): string {
  return toForm(toForm(value, 'NFD').replace(/\p{M}/gu, ''), 'NFC');
}
