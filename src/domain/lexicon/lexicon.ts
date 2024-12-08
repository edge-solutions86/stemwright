import { groupSorted } from '../collections.js';
import { ConflictError, NotFoundError } from '../errors.js';
import type { Id } from '../identifiers.js';
import type { ClassRegistry } from './inflection-class.js';
import { classChain, isDescendantOf } from './inflection-class.js';
import type { Lexeme } from './lexeme.js';
import { compareLexemes } from './lexeme.js';

/**
 * An immutable collection of lexemes with the indexes the drill layer needs.
 * Rebuilt rather than mutated: a session holds one for the length of a request
 * and the store hands out a fresh one after a write, which keeps a queue from
 * changing under a learner mid session.
 */
export interface Lexicon {
  readonly language: string;
  readonly byId: ReadonlyMap<string, Lexeme>;
  readonly byLemma: ReadonlyMap<string, readonly Lexeme[]>;
  readonly byClass: ReadonlyMap<string, readonly Lexeme[]>;
  readonly byTag: ReadonlyMap<string, readonly Lexeme[]>;
}

export function buildLexicon(language: string, entries: readonly Lexeme[]): Lexicon {
  const byId = new Map<string, Lexeme>();
  for (const entry of entries) {
    if (byId.has(entry.id)) {
      throw new ConflictError(`lexeme ${entry.id} appears twice in the lexicon`);
    }
    byId.set(entry.id, entry);
  }

  const tagged = entries.flatMap((entry) => entry.tags.map((tag) => ({ tag, entry })));

  return {
    language,
    byId,
    byLemma: groupSorted(entries, (entry) => entry.lemma.toLowerCase(), compareLexemes),
    byClass: groupSorted(entries, (entry) => entry.classKey, compareLexemes),
    byTag: new Map(
      [
        ...groupSorted(
          tagged,
          (row) => row.tag,
          (a, b) => compareLexemes(a.entry, b.entry),
        ),
      ].map(([tag, rows]) => [tag, rows.map((row) => row.entry)]),
    ),
  };
}

export function emptyLexicon(language: string): Lexicon {
  return buildLexicon(language, []);
}

export function lexiconSize(lexicon: Lexicon): number {
  return lexicon.byId.size;
}

export function allLexemes(lexicon: Lexicon): Lexeme[] {
  return [...lexicon.byId.values()].sort(compareLexemes);
}

export function lexemeById(lexicon: Lexicon, id: Id<'lexeme'> | string): Lexeme | undefined {
  return lexicon.byId.get(id);
}

export function requireLexeme(lexicon: Lexicon, id: Id<'lexeme'> | string): Lexeme {
  const found = lexicon.byId.get(id);
  if (found === undefined) {
    throw new NotFoundError('lexeme', id);
  }
  return found;
}

/** Every homograph of a lemma, in homograph order. */
export function lexemesByLemma(lexicon: Lexicon, lemma: string): readonly Lexeme[] {
  return lexicon.byLemma.get(lemma.toLowerCase()) ?? [];
}

export function nextHomograph(lexicon: Lexicon, lemma: string): number {
  const existing = lexemesByLemma(lexicon, lemma);
  return existing.reduce((highest, entry) => Math.max(highest, entry.homograph), 0) + 1;
}

export function lexemesByClass(lexicon: Lexicon, classKey: string): readonly Lexeme[] {
  return lexicon.byClass.get(classKey) ?? [];
}

/**
 * Every lexeme of a class or of any class beneath it. This is the lookup a deck
 * uses, because a deck scoped to the second declension means the neuters and
 * the r stems too.
 */
export function lexemesUnderClass(
  lexicon: Lexicon,
  classes: ClassRegistry,
  classKey: string,
): Lexeme[] {
  return allLexemes(lexicon).filter(
    (entry) => entry.classKey === classKey || isDescendantOf(classes, entry.classKey, classKey),
  );
}

export function lexemesByTag(lexicon: Lexicon, tag: string): readonly Lexeme[] {
  return lexicon.byTag.get(tag) ?? [];
}

export function tagsOf(lexicon: Lexicon): string[] {
  return [...lexicon.byTag.keys()].sort();
}

export function addLexeme(lexicon: Lexicon, entry: Lexeme): Lexicon {
  if (lexicon.byId.has(entry.id)) {
    throw new ConflictError(
      `lexeme ${entry.lemma} with homograph ${entry.homograph} is already in the lexicon`,
    );
  }
  return buildLexicon(lexicon.language, [...lexicon.byId.values(), entry]);
}

export function replaceLexeme(lexicon: Lexicon, entry: Lexeme): Lexicon {
  if (!lexicon.byId.has(entry.id)) {
    throw new NotFoundError('lexeme', entry.id);
  }
  const kept = [...lexicon.byId.values()].filter((candidate) => candidate.id !== entry.id);
  return buildLexicon(lexicon.language, [...kept, entry]);
}

export function removeLexeme(lexicon: Lexicon, id: Id<'lexeme'> | string): Lexicon {
  if (!lexicon.byId.has(id)) {
    throw new NotFoundError('lexeme', id);
  }
  const kept = [...lexicon.byId.values()].filter((candidate) => candidate.id !== id);
  return buildLexicon(lexicon.language, kept);
}

export interface LexiconStats {
  readonly total: number;
  readonly byPartOfSpeech: ReadonlyMap<string, number>;
  readonly classesUsed: number;
  readonly untagged: number;
  readonly withoutGloss: number;
}

export function lexiconStats(lexicon: Lexicon, classes: ClassRegistry): LexiconStats {
  const byPartOfSpeech = new Map<string, number>();
  let untagged = 0;
  let withoutGloss = 0;

  for (const entry of lexicon.byId.values()) {
    const chain = classChain(classes, entry.classKey);
    const part = chain[0]?.partOfSpeech ?? 'unknown';
    byPartOfSpeech.set(part, (byPartOfSpeech.get(part) ?? 0) + 1);
    if (entry.tags.length === 0) {
      untagged += 1;
    }
    if (entry.glosses.length === 0) {
      withoutGloss += 1;
    }
  }

  return {
    total: lexicon.byId.size,
    byPartOfSpeech,
    classesUsed: lexicon.byClass.size,
    untagged,
    withoutGloss,
  };
}
