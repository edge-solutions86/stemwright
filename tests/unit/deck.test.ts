import { describe, expect, it } from 'vitest';
import {
  EMPTY_SCOPE,
  deck,
  deckIdFor,
  describeScope,
  isWholeLanguage,
  scopeIsEmpty,
  withScope,
} from '../../src/domain/drill/deck.js';
import { formatSelector } from '../../src/domain/features/selector.js';
import { isId } from '../../src/domain/identifiers.js';
import { problemMessages } from '../support/problems.js';
import { latinNoun } from '../support/profiles.js';

const build = (input: Parameters<typeof deck>[1]) => deck(latinNoun, input);

const firstYear = build({
  key: 'first-year',
  label: 'First year nouns',
  scope: {
    classKeys: ['first-declension', 'second-declension'],
    tags: ['core'],
    cells: 'number=sg',
  },
  toleranceLevel: 'standard',
  note: 'the nouns from the first six chapters',
});

describe('deck', () => {
  it('keeps the key, label and language', () => {
    expect(firstYear.key).toBe('first-year');
    expect(firstYear.label).toBe('First year nouns');
    expect(firstYear.language).toBe('latin');
    expect(isId('deck', firstYear.id)).toBe(true);
  });

  it('derives a stable id from the language and the key', () => {
    expect(firstYear.id).toBe(deckIdFor('latin', 'first-year'));
  });

  it('defaults the label to the key', () => {
    expect(build({ key: 'plain' }).label).toBe('plain');
  });

  it('parses the cell selector', () => {
    expect(formatSelector(firstYear.scope.cells)).toBe('number=sg');
  });

  it('leaves the tolerance level absent when none is asked for', () => {
    expect(build({ key: 'plain' }).toleranceLevel).toBeUndefined();
  });

  it('refuses a repeated class, tag or lexeme', () => {
    expect(problemMessages(() => build({ key: 'x', scope: { classKeys: ['a', 'a'] } }))).toEqual([
      'scope.classKeys names the same class twice',
    ]);
    expect(problemMessages(() => build({ key: 'x', scope: { tags: ['a', 'a'] } }))).toEqual([
      'scope.tags names the same tag twice',
    ]);
    expect(
      problemMessages(() => build({ key: 'x', scope: { lexemeIds: ['lex_a', 'lex_a'] } })),
    ).toEqual(['scope.lexemeIds names the same lexeme twice']);
  });

  it('refuses a cell selector the schema rejects', () => {
    expect(problemMessages(() => build({ key: 'x', scope: { cells: 'tense=pres' } }))).toEqual([
      'scope.cells tense is not a dimension of latin',
    ]);
  });

  it('refuses a key that is not a slug', () => {
    expect(() => build({ key: 'First Year' })).toThrow(/not a slug/);
  });
});

describe('scopeIsEmpty', () => {
  it('is true for the empty scope', () => {
    expect(scopeIsEmpty(EMPTY_SCOPE)).toBe(true);
    expect(isWholeLanguage(build({ key: 'plain' }))).toBe(true);
  });

  it('is false as soon as any part is set', () => {
    expect(scopeIsEmpty(firstYear.scope)).toBe(false);
    expect(isWholeLanguage(build({ key: 'x', scope: { cells: 'number=pl' } }))).toBe(false);
    expect(isWholeLanguage(build({ key: 'y', scope: { tags: ['core'] } }))).toBe(false);
  });
});

describe('describeScope', () => {
  it('says so when nothing is constrained', () => {
    expect(describeScope(EMPTY_SCOPE)).toBe('everything in the language');
  });

  it('reads every part of the scope out', () => {
    expect(describeScope(firstYear.scope)).toBe(
      'classes first-declension or second-declension, tagged core, cells where number=sg',
    );
  });

  it('counts named lexemes rather than listing them', () => {
    const named = build({ key: 'x', scope: { lexemeIds: ['lex_aaaaaa', 'lex_bbbbbb'] } });
    expect(describeScope(named.scope)).toBe('2 named lexemes');
  });
});

describe('withScope', () => {
  it('replaces only the parts it is given', () => {
    const widened = withScope(latinNoun, firstYear, { cells: 'number=pl' });
    expect(formatSelector(widened.scope.cells)).toBe('number=pl');
    expect(widened.scope.classKeys).toEqual(['first-declension', 'second-declension']);
    expect(widened.scope.tags).toEqual(['core']);
  });

  it('keeps the tolerance level and the note', () => {
    const widened = withScope(latinNoun, firstYear, { tags: ['advanced'] });
    expect(widened.toleranceLevel).toBe('standard');
    expect(widened.note).toBe('the nouns from the first six chapters');
  });

  it('leaves the original deck alone', () => {
    withScope(latinNoun, firstYear, { tags: [] });
    expect(firstYear.scope.tags).toEqual(['core']);
  });

  it('keeps the same id, because the key did not change', () => {
    expect(withScope(latinNoun, firstYear, { tags: [] }).id).toBe(firstYear.id);
  });
});
