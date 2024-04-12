import { ValidationError } from './errors.js';

/**
 * Ids are opaque strings with a kind prefix. The prefix is load bearing: a
 * journal entry names the kind nowhere else, and the http layer refuses a
 * `deck_` where a `lex_` belongs without reaching the store.
 */
export const ID_PREFIXES = {
  lexeme: 'lex',
  profile: 'prof',
  revision: 'rev',
  deck: 'deck',
  cell: 'cell',
  attempt: 'att',
  session: 'sess',
  course: 'crs',
  assignment: 'asg',
  learner: 'lrn',
} as const;

export type IdKind = keyof typeof ID_PREFIXES;

export type Id<K extends IdKind> = string & { readonly __idKind: K };

const BODY = /^[0-9a-z]{6,32}$/;
const SLUG = /^[a-z][a-z0-9]*(?:[-.][a-z0-9]+)*$/;

export function isId<K extends IdKind>(kind: K, value: unknown): value is Id<K> {
  if (typeof value !== 'string') {
    return false;
  }
  const prefix = ID_PREFIXES[kind];
  if (!value.startsWith(`${prefix}_`)) {
    return false;
  }
  return BODY.test(value.slice(prefix.length + 1));
}

export function asId<K extends IdKind>(kind: K, value: string): Id<K> {
  if (!isId(kind, value)) {
    throw ValidationError.field(`${kind}Id`, `is not a well formed ${kind} id: ${value}`);
  }
  return value;
}

export function kindOf(value: string): IdKind | undefined {
  const underscore = value.indexOf('_');
  if (underscore <= 0) {
    return undefined;
  }
  const prefix = value.slice(0, underscore);
  for (const [kind, candidate] of Object.entries(ID_PREFIXES)) {
    if (candidate === prefix && isId(kind as IdKind, value)) {
      return kind as IdKind;
    }
  }
  return undefined;
}

/**
 * A small non cryptographic hash. It exists so a cell id can be derived from
 * the lexeme and the bundle rather than allocated, which keeps a re expanded
 * paradigm stable across runs and makes journal replay idempotent.
 */
export function digest(...parts: readonly string[]): string {
  let h1 = 0x811c9dc5;
  let h2 = 0x01000193;
  const joined = parts.join('');
  for (let i = 0; i < joined.length; i += 1) {
    const c = joined.charCodeAt(i);
    h1 = Math.imul(h1 ^ c, 0x01000193) >>> 0;
    h2 = Math.imul(h2 + c + i, 0x85ebca6b) >>> 0;
  }
  const low = h1.toString(36).padStart(7, '0');
  const high = h2.toString(36).padStart(7, '0');
  return `${low}${high}`.slice(0, 12);
}

export function derivedId<K extends IdKind>(kind: K, ...parts: readonly string[]): Id<K> {
  if (parts.length === 0) {
    throw ValidationError.field('parts', 'must name at least one component');
  }
  return `${ID_PREFIXES[kind]}_${digest(...parts)}` as Id<K>;
}

/**
 * Allocated ids come from a counter plus a run token so two processes writing
 * the same journal do not collide. Nothing depends on them being sortable.
 */
export interface IdAllocator {
  next<K extends IdKind>(kind: K): Id<K>;
}

export function createAllocator(seed: string, start = 0): IdAllocator {
  const token = digest(seed).slice(0, 4);
  let counter = start;
  return {
    next<K extends IdKind>(kind: K): Id<K> {
      counter += 1;
      const tail = counter.toString(36).padStart(4, '0');
      return `${ID_PREFIXES[kind]}_${token}${tail}` as Id<K>;
    },
  };
}

/** Human typed keys such as an inflection class name or a feature value. */
export function asSlug(path: string, value: string): string {
  const trimmed = value.trim().toLowerCase();
  if (!SLUG.test(trimmed)) {
    throw ValidationError.field(path, `is not a slug: ${value}`);
  }
  return trimmed;
}

export function isSlug(value: string): boolean {
  return SLUG.test(value);
}

export function slugify(value: string): string {
  const folded = value
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
  if (folded === '' || /^[0-9]/.test(folded)) {
    return `x${folded === '' ? '' : `-${folded}`}`;
  }
  return folded;
}
