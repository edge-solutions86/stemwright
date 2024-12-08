import { groupBy } from '../collections.js';
import { parseBundleKey } from '../features/bundle.js';
import type { FeatureSchema } from '../features/schema.js';
import { matchesSelector } from '../features/selector.js';
import type { ClassRegistry } from '../lexicon/inflection-class.js';
import { isDescendantOf } from '../lexicon/inflection-class.js';
import type { Lexeme } from '../lexicon/lexeme.js';
import type { Lexicon } from '../lexicon/lexicon.js';
import { allLexemes } from '../lexicon/lexicon.js';
import type { ProfileBook } from '../paradigm/book.js';
import { bindingFor, profileFor } from '../paradigm/book.js';
import type { Cell } from '../paradigm/cell.js';
import { expandCells } from '../paradigm/cell.js';
import type { CivilDate } from '../time/civil.js';
import type { Deck } from './deck.js';

/**
 * Turning a deck back into cells. Everything the drill layer does starts here,
 * and it depends on the day: the profile in force decides which slots exist, so
 * the same deck can hold a different number of cells this term than last.
 */
export interface DrillContext {
  readonly schema: FeatureSchema;
  readonly classes: ClassRegistry;
  readonly book: ProfileBook;
  readonly lexicon: Lexicon;
}

export type SkipReason =
  | 'out-of-scope'
  | 'unbound-class'
  | 'profile-not-published'
  | 'no-cell-in-scope';

export interface SkippedLexeme {
  readonly lexemeId: string;
  readonly lemma: string;
  readonly reason: SkipReason;
}

export interface Membership {
  readonly deckKey: string;
  readonly on: CivilDate;
  readonly cells: readonly Cell[];
  readonly lexemes: readonly Lexeme[];
  readonly skipped: readonly SkippedLexeme[];
}

function inLexemeScope(deck: Deck, classes: ClassRegistry, entry: Lexeme): boolean {
  const { classKeys, tags, lexemeIds } = deck.scope;
  if (lexemeIds.length > 0 && !lexemeIds.includes(entry.id)) {
    return false;
  }
  if (
    classKeys.length > 0 &&
    !classKeys.some(
      (classKey) =>
        entry.classKey === classKey || isDescendantOf(classes, entry.classKey, classKey),
    )
  ) {
    return false;
  }
  return tags.every((tag) => entry.tags.includes(tag));
}

/**
 * The parts of the scope intersect, and a lexeme that fails any of them is out.
 * A lexeme that is in scope but yields no cells is reported separately, because
 * the two are different editorial problems: the first is a deck that is narrower
 * than the editor thought, the second is a profile that is missing or a cell
 * selector nothing satisfies.
 */
export function resolveMembership(context: DrillContext, deck: Deck, on: CivilDate): Membership {
  const cells: Cell[] = [];
  const lexemes: Lexeme[] = [];
  const skipped: SkippedLexeme[] = [];

  for (const entry of allLexemes(context.lexicon)) {
    if (!inLexemeScope(deck, context.classes, entry)) {
      skipped.push({ lexemeId: entry.id, lemma: entry.lemma, reason: 'out-of-scope' });
      continue;
    }

    if (bindingFor(context.book, context.classes, entry.classKey) === undefined) {
      skipped.push({ lexemeId: entry.id, lemma: entry.lemma, reason: 'unbound-class' });
      continue;
    }

    const resolved = profileFor(context.book, context.classes, entry.classKey, on);
    if (resolved === undefined) {
      skipped.push({ lexemeId: entry.id, lemma: entry.lemma, reason: 'profile-not-published' });
      continue;
    }

    const expanded = expandCells(context.schema, entry, resolved.revision.profile).filter((cell) =>
      matchesSelector(deck.scope.cells, parseBundleKey(context.schema, cell.bundle)),
    );

    if (expanded.length === 0) {
      skipped.push({ lexemeId: entry.id, lemma: entry.lemma, reason: 'no-cell-in-scope' });
      continue;
    }

    lexemes.push(entry);
    cells.push(...expanded);
  }

  return { deckKey: deck.key, on, cells, lexemes, skipped };
}

export function membershipSize(membership: Membership): number {
  return membership.cells.length;
}

export function isEmptyMembership(membership: Membership): boolean {
  return membership.cells.length === 0;
}

export function skippedFor(membership: Membership, reason: SkipReason): SkippedLexeme[] {
  return membership.skipped.filter((entry) => entry.reason === reason);
}

/**
 * The reasons worth telling an editor about. `out-of-scope` is not one: a deck
 * excluding most of the lexicon is the normal case, and reporting it would bury
 * the two reasons that mean something is broken.
 */
export function membershipWarnings(membership: Membership): string[] {
  const warnings: string[] = [];
  const unbound = skippedFor(membership, 'unbound-class');
  if (unbound.length > 0) {
    warnings.push(`${unbound.length} lexemes have no paradigm bound to their class`);
  }
  const unpublished = skippedFor(membership, 'profile-not-published');
  if (unpublished.length > 0) {
    warnings.push(
      `${unpublished.length} lexemes use a profile with no revision in force on ${membership.on}`,
    );
  }
  const empty = skippedFor(membership, 'no-cell-in-scope');
  if (empty.length > 0) {
    warnings.push(`${empty.length} lexemes have no cell matching the deck selector`);
  }
  if (membership.cells.length === 0) {
    warnings.push('the deck selects nothing at all');
  }
  return warnings;
}

export function cellsByLexeme(membership: Membership): ReadonlyMap<string, Cell[]> {
  return groupBy(membership.cells, (cell) => cell.lexemeId);
}

export function bundlesCovered(membership: Membership): string[] {
  return [...new Set(membership.cells.map((cell) => cell.bundle))].sort();
}
