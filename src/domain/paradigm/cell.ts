import { ValidationError } from '../errors.js';
import type { FeatureBundle } from '../features/bundle.js';
import { bundleKey, parseBundleKey } from '../features/bundle.js';
import { formatBundle } from '../features/notation.js';
import type { FeatureSchema } from '../features/schema.js';
import type { Id } from '../identifiers.js';
import { derivedId } from '../identifiers.js';
import type { Lexeme } from '../lexicon/lexeme.js';
import { citationForm, contradictsInherent } from '../lexicon/lexeme.js';
import type { ParadigmProfile } from './profile.js';

/**
 * A cell is what gets drilled: this lexeme in this bundle. It is derived, not
 * stored as an editorial object, so a lexicon edit that adds a slot to a
 * profile grows the cell set of every lexeme in that class at once.
 *
 * The id is derived from the lexeme id and the canonical bundle key, so the
 * same cell keeps the same id across a re expansion. Mastery is keyed on that
 * id, and an allocated id would silently reset every learner's history the
 * first time a profile was reloaded.
 */
export interface Cell {
  readonly id: Id<'cell'>;
  readonly lexemeId: string;
  readonly bundle: string;
  readonly profileKey: string;
}

export function cellIdFor(lexemeId: string, bundle: string): Id<'cell'> {
  return derivedId('cell', lexemeId, bundle);
}

export function cell(lexemeId: string, bundle: string, profileKey: string): Cell {
  return { id: cellIdFor(lexemeId, bundle), lexemeId, bundle, profileKey };
}

/**
 * Every cell the profile defines for a lexeme, skipping the bundles the
 * lexeme's inherent features rule out. A masculine noun has no feminine
 * genitive, and offering one as a drill question is offering a question with no
 * right answer.
 */
export function expandCells(
  schema: FeatureSchema,
  entry: Lexeme,
  profile: ParadigmProfile,
): Cell[] {
  const cells: Cell[] = [];
  for (const slotKey of profile.slots.keys()) {
    const bundle = parseBundleKey(schema, slotKey);
    if (contradictsInherent(entry, bundle)) {
      continue;
    }
    cells.push(cell(entry.id, slotKey, profile.key));
  }
  return cells;
}

export function skippedBundles(
  schema: FeatureSchema,
  entry: Lexeme,
  profile: ParadigmProfile,
): string[] {
  return [...profile.slots.keys()].filter((slotKey) =>
    contradictsInherent(entry, parseBundleKey(schema, slotKey)),
  );
}

/** A short human reference such as `puella/gen.sg`, used in the cli and in logs. */
export function formatCellRef(schema: FeatureSchema, entry: Lexeme, bundle: FeatureBundle): string {
  return `${citationForm(entry)}/${formatBundle(schema, bundle)}`;
}

export interface CellRef {
  readonly lemma: string;
  readonly homograph: number;
  readonly notation: string;
}

/**
 * Reads `puella/gen.sg` and `malus(2)/nom.pl` back into their parts. The lemma
 * is left as text rather than resolved here, because resolving it needs a
 * lexicon and this module is meant to stay usable without one.
 */
export function parseCellRef(text: string, path = 'cell'): CellRef {
  const slash = text.lastIndexOf('/');
  if (slash <= 0 || slash === text.length - 1) {
    throw ValidationError.field(path, `is not a lemma and a bundle: ${text}`);
  }
  const head = text.slice(0, slash).trim();
  const notation = text.slice(slash + 1).trim();

  const match = /^(.+?)\s*\((\d+)\)$/.exec(head);
  if (match === null) {
    return { lemma: head, homograph: 1, notation };
  }
  const homograph = Number(match[2]);
  if (homograph < 1) {
    throw ValidationError.field(path, `has a homograph index below one: ${text}`);
  }
  return { lemma: match[1]!.trim(), homograph, notation };
}

export function sameCell(a: Cell, b: Cell): boolean {
  return a.lexemeId === b.lexemeId && a.bundle === b.bundle;
}

export function compareCells(a: Cell, b: Cell): number {
  const byLexeme = a.lexemeId.localeCompare(b.lexemeId);
  return byLexeme !== 0 ? byLexeme : a.bundle.localeCompare(b.bundle);
}

export function cellsOfLexeme(cells: readonly Cell[], lexemeId: string): Cell[] {
  return cells.filter((candidate) => candidate.lexemeId === lexemeId);
}

export function cellsInBundle(cells: readonly Cell[], bundle: FeatureBundle): Cell[] {
  const key = bundleKey(bundle);
  return cells.filter((candidate) => candidate.bundle === key);
}

export function indexCells(cells: readonly Cell[]): ReadonlyMap<string, Cell> {
  const index = new Map<string, Cell>();
  for (const candidate of cells) {
    index.set(candidate.id, candidate);
  }
  return index;
}
