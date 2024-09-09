import { parseBundleKey, valueOf } from '../features/bundle.js';
import type { FeatureSchema } from '../features/schema.js';
import type { ClassRegistry } from '../lexicon/inflection-class.js';
import type { Lexicon } from '../lexicon/lexicon.js';
import { lexemeById } from '../lexicon/lexicon.js';
import type { ProfileBook } from '../paradigm/book.js';
import { profileFor } from '../paradigm/book.js';
import type { Cell } from '../paradigm/cell.js';
import { cellIdFor } from '../paradigm/cell.js';
import { twinsOf } from '../paradigm/syncretism.js';
import type { CivilDate } from '../time/civil.js';
import type { DrillContext } from './membership.js';

/**
 * Burial is the rule that stops a session asking the same question twice in
 * different clothes. If `puellae` answers the genitive singular, the dative
 * singular and the nominative plural, asking all three in one sitting teaches
 * the learner that the answer is always `puellae`, which is the opposite of
 * what a paradigm drill is for.
 *
 * Which cells are twins is not a fact about the cell. It comes from the
 * syncretism map of the profile revision in force on the day, so the same
 * session run against last term's revision buries a different set.
 */
export interface BurialSet {
  readonly buried: ReadonlySet<string>;
}

export const NOTHING_BURIED: BurialSet = { buried: new Set() };

export function twinCellsOf(context: DrillContext, cell: Cell, on: CivilDate): string[] {
  const entry = lexemeById(context.lexicon, cell.lexemeId);
  if (entry === undefined) {
    return [];
  }
  const resolved = profileFor(context.book, context.classes, entry.classKey, on);
  if (resolved === undefined) {
    return [];
  }
  return twinsOf(resolved.revision.syncretism, cell.bundle).map((bundle) =>
    cellIdFor(cell.lexemeId, bundle),
  );
}

/**
 * Burying a cell buries the cell itself as well as its twins. A session that
 * buried only the twins would be free to ask the very cell it just asked.
 */
export function buryCell(
  context: DrillContext,
  set: BurialSet,
  cell: Cell,
  on: CivilDate,
): BurialSet {
  const buried = new Set(set.buried);
  buried.add(cell.id);
  for (const twin of twinCellsOf(context, cell, on)) {
    buried.add(twin);
  }
  return { buried };
}

export function isBuried(set: BurialSet, cellId: string): boolean {
  return set.buried.has(cellId);
}

export function burialSize(set: BurialSet): number {
  return set.buried.size;
}

export function unbury(set: BurialSet, cellId: string): BurialSet {
  if (!set.buried.has(cellId)) {
    return set;
  }
  const buried = new Set(set.buried);
  buried.delete(cellId);
  return { buried };
}

/**
 * The row a cell sits in, for the spacing rule. A row is the cell's bundle with
 * one dimension dropped, so two cells of the same lexeme differing only in
 * number are in the same row and will not be asked back to back.
 */
export function rowKeyOf(schema: FeatureSchema, cell: Cell, acrossDimension: string): string {
  const bundle = parseBundleKey(schema, cell.bundle);
  const parts = bundle.pairs
    .filter((pair) => pair.dimension !== acrossDimension)
    .map((pair) => `${pair.dimension}=${pair.value}`);
  return `${cell.lexemeId}|${parts.join(';')}`;
}

export function valueInRow(
  schema: FeatureSchema,
  cell: Cell,
  acrossDimension: string,
): string | undefined {
  return valueOf(parseBundleKey(schema, cell.bundle), acrossDimension);
}

/**
 * Checks the spacing rule against the tail of what has already been asked. A
 * gap of nought means the rule is off, and a candidate whose row has not been
 * seen at all always passes.
 */
export function respectsSpacing(
  schema: FeatureSchema,
  asked: readonly Cell[],
  candidate: Cell,
  acrossDimension: string,
  gap: number,
): boolean {
  if (gap <= 0) {
    return true;
  }
  const wanted = rowKeyOf(schema, candidate, acrossDimension);
  const window = asked.slice(Math.max(0, asked.length - gap));
  return !window.some((cell) => rowKeyOf(schema, cell, acrossDimension) === wanted);
}

export interface BurialContext {
  readonly schema: FeatureSchema;
  readonly classes: ClassRegistry;
  readonly book: ProfileBook;
  readonly lexicon: Lexicon;
}

/** Every cell of the list that a twin of an already asked cell would block. */
export function blockedByBurial(set: BurialSet, cells: readonly Cell[]): Cell[] {
  return cells.filter((cell) => isBuried(set, cell.id));
}

export function survivingBurial(set: BurialSet, cells: readonly Cell[]): Cell[] {
  return cells.filter((cell) => !isBuried(set, cell.id));
}
