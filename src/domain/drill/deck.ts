import { ProblemCollector } from '../errors.js';
import type { Selector } from '../features/selector.js';
import { EMPTY_SELECTOR, formatSelector, parseSelector } from '../features/selector.js';
import type { FeatureSchema } from '../features/schema.js';
import type { Id } from '../identifiers.js';
import { asSlug, derivedId } from '../identifiers.js';
import { requireText } from '../text/normalise.js';

/**
 * A deck is a saved question about the paradigm space: which lexemes, and which
 * of their cells. It is not a list of cells. Stating it as a query is what lets
 * a lexicon import add fifty nouns to a first year deck without anybody editing
 * the deck, and it is why a deck can be wrong in a way a list cannot, by
 * selecting nothing.
 *
 * The scope has four parts and they combine as an intersection. A deck naming
 * both a class and a tag wants the lexemes that are in that class and carry
 * that tag, which is nearly always what a teacher means and never what a union
 * would give them.
 */
export interface DeckScope {
  readonly classKeys: readonly string[];
  readonly tags: readonly string[];
  readonly lexemeIds: readonly string[];
  readonly cells: Selector;
}

export interface Deck {
  readonly id: Id<'deck'>;
  readonly key: string;
  readonly label: string;
  readonly language: string;
  readonly scope: DeckScope;
  readonly toleranceLevel: string | undefined;
  readonly note: string | undefined;
}

export interface DeckScopeInput {
  readonly classKeys?: readonly string[];
  readonly tags?: readonly string[];
  readonly lexemeIds?: readonly string[];
  readonly cells?: string;
}

export interface DeckInput {
  readonly key: string;
  readonly label?: string;
  readonly scope?: DeckScopeInput;
  readonly toleranceLevel?: string;
  readonly note?: string;
}

export const EMPTY_SCOPE: DeckScope = {
  classKeys: [],
  tags: [],
  lexemeIds: [],
  cells: EMPTY_SELECTOR,
};

export function deckIdFor(language: string, key: string): Id<'deck'> {
  return derivedId('deck', language, key);
}

export function deck(schema: FeatureSchema, input: DeckInput): Deck {
  const problems = new ProblemCollector();
  const key = asSlug('deck.key', input.key);

  const classKeys = (input.scope?.classKeys ?? []).map((value, index) =>
    asSlug(`scope.classKeys.${index}`, value),
  );
  const tags = (input.scope?.tags ?? []).map((value, index) =>
    asSlug(`scope.tags.${index}`, value),
  );
  const lexemeIds = [...(input.scope?.lexemeIds ?? [])];

  if (new Set(classKeys).size !== classKeys.length) {
    problems.add('scope.classKeys', 'names the same class twice');
  }
  if (new Set(tags).size !== tags.length) {
    problems.add('scope.tags', 'names the same tag twice');
  }
  if (new Set(lexemeIds).size !== lexemeIds.length) {
    problems.add('scope.lexemeIds', 'names the same lexeme twice');
  }

  const cells =
    problems.absorbFlat('scope.cells', () =>
      parseSelector(schema, input.scope?.cells ?? '', 'scope.cells'),
    ) ?? EMPTY_SELECTOR;

  problems.throwIfAny(`deck ${key} is not usable`);

  return {
    id: deckIdFor(schema.language, key),
    key,
    label: input.label === undefined ? key : requireText('deck.label', input.label, 96),
    language: schema.language,
    scope: { classKeys, tags, lexemeIds, cells },
    toleranceLevel:
      input.toleranceLevel === undefined
        ? undefined
        : asSlug('deck.toleranceLevel', input.toleranceLevel),
    note: input.note === undefined ? undefined : requireText('deck.note', input.note, 500),
  };
}

export function scopeIsEmpty(scope: DeckScope): boolean {
  return (
    scope.classKeys.length === 0 &&
    scope.tags.length === 0 &&
    scope.lexemeIds.length === 0 &&
    scope.cells.clauses.length === 0
  );
}

/** A deck that constrains nothing takes the whole language, which is legal. */
export function isWholeLanguage(entry: Deck): boolean {
  return scopeIsEmpty(entry.scope);
}

export function describeScope(scope: DeckScope): string {
  const parts: string[] = [];
  if (scope.classKeys.length > 0) {
    parts.push(`classes ${scope.classKeys.join(' or ')}`);
  }
  if (scope.tags.length > 0) {
    parts.push(`tagged ${scope.tags.join(' and ')}`);
  }
  if (scope.lexemeIds.length > 0) {
    parts.push(`${scope.lexemeIds.length} named lexemes`);
  }
  if (scope.cells.clauses.length > 0) {
    parts.push(`cells where ${formatSelector(scope.cells)}`);
  }
  return parts.length === 0 ? 'everything in the language' : parts.join(', ');
}

export function withScope(schema: FeatureSchema, entry: Deck, scope: DeckScopeInput): Deck {
  return deck(schema, {
    key: entry.key,
    label: entry.label,
    scope: {
      classKeys: scope.classKeys ?? entry.scope.classKeys,
      tags: scope.tags ?? entry.scope.tags,
      lexemeIds: scope.lexemeIds ?? entry.scope.lexemeIds,
      cells: scope.cells ?? formatSelector(entry.scope.cells),
    },
    ...(entry.toleranceLevel === undefined ? {} : { toleranceLevel: entry.toleranceLevel }),
    ...(entry.note === undefined ? {} : { note: entry.note }),
  });
}
