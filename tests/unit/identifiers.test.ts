import { describe, expect, it } from 'vitest';
import {
  ID_PREFIXES,
  asId,
  asSlug,
  createAllocator,
  derivedId,
  digest,
  isId,
  isSlug,
  kindOf,
  slugify,
} from '../../src/domain/identifiers.js';
import { ValidationError } from '../../src/domain/errors.js';

describe('isId', () => {
  it('accepts a well formed id of the asked for kind', () => {
    expect(isId('lexeme', 'lex_9fx2ab')).toBe(true);
    expect(isId('deck', 'deck_00zz11')).toBe(true);
  });

  it('rejects the right shape under the wrong prefix', () => {
    expect(isId('lexeme', 'deck_9fx2ab')).toBe(false);
  });

  it('rejects a body that is too short, too long or not lower alphanumeric', () => {
    expect(isId('lexeme', 'lex_9fx2a')).toBe(false);
    expect(isId('lexeme', `lex_${'a'.repeat(33)}`)).toBe(false);
    expect(isId('lexeme', 'lex_9FX2AB')).toBe(false);
    expect(isId('lexeme', 'lex_9fx2-ab')).toBe(false);
  });

  it('rejects anything that is not a string', () => {
    expect(isId('lexeme', 42)).toBe(false);
    expect(isId('lexeme', null)).toBe(false);
    expect(isId('lexeme', undefined)).toBe(false);
  });
});

describe('asId', () => {
  it('returns the value when it parses', () => {
    expect(asId('profile', 'prof_abc123')).toBe('prof_abc123');
  });

  it('names the kind in the failure', () => {
    expect(() => asId('profile', 'lex_abc123')).toThrow(ValidationError);
    expect(() => asId('profile', 'lex_abc123')).toThrow(/well formed profile id/);
  });
});

describe('kindOf', () => {
  it('reads the kind back off the prefix', () => {
    expect(kindOf('cell_aabbcc')).toBe('cell');
    expect(kindOf('asg_aabbcc')).toBe('assignment');
  });

  it('gives nothing for an unknown prefix or a malformed body', () => {
    expect(kindOf('what_aabbcc')).toBeUndefined();
    expect(kindOf('cell_AA')).toBeUndefined();
    expect(kindOf('nounderscore')).toBeUndefined();
    expect(kindOf('_leading')).toBeUndefined();
  });

  it('covers every prefix the module declares', () => {
    for (const kind of Object.keys(ID_PREFIXES)) {
      const id = derivedId(kind as keyof typeof ID_PREFIXES, 'sample');
      expect(kindOf(id)).toBe(kind);
    }
  });
});

describe('digest', () => {
  it('is stable for the same parts', () => {
    expect(digest('a', 'b')).toBe(digest('a', 'b'));
  });

  it('separates inputs that differ', () => {
    expect(digest('nom.sg')).not.toBe(digest('nom.pl'));
    expect(digest('ab', 'c')).not.toBe(digest('a', 'bd'));
  });

  it('is twelve lower alphanumeric characters', () => {
    expect(digest('anything at all')).toMatch(/^[0-9a-z]{12}$/);
    expect(digest('')).toMatch(/^[0-9a-z]{12}$/);
  });
});

describe('derivedId', () => {
  it('depends only on the parts', () => {
    const first = derivedId('cell', 'lex_aaaaaa', 'nom.sg');
    const second = derivedId('cell', 'lex_aaaaaa', 'nom.sg');
    expect(first).toBe(second);
    expect(isId('cell', first)).toBe(true);
  });

  it('refuses to derive from nothing', () => {
    expect(() => derivedId('cell')).toThrow(ValidationError);
  });
});

describe('createAllocator', () => {
  it('hands out distinct ids of the asked for kind', () => {
    const allocator = createAllocator('run-1');
    const ids = [allocator.next('lexeme'), allocator.next('lexeme'), allocator.next('deck')];
    expect(new Set(ids).size).toBe(3);
    expect(isId('lexeme', ids[0]!)).toBe(true);
    expect(isId('deck', ids[2]!)).toBe(true);
  });

  it('separates two runs with different seeds', () => {
    const a = createAllocator('run-a').next('attempt');
    const b = createAllocator('run-b').next('attempt');
    expect(a).not.toBe(b);
  });

  it('resumes from a start counter', () => {
    const fresh = createAllocator('run-c');
    fresh.next('session');
    const expected = fresh.next('session');
    expect(createAllocator('run-c', 1).next('session')).toBe(expected);
  });
});

describe('slugs', () => {
  it('accepts dotted and dashed lower case keys', () => {
    expect(asSlug('class', 'o-stem')).toBe('o-stem');
    expect(asSlug('feature', 'case.nom')).toBe('case.nom');
    expect(isSlug('a1')).toBe(true);
  });

  it('trims and lower cases before checking', () => {
    expect(asSlug('class', '  A-Stem  ')).toBe('a-stem');
  });

  it('refuses leading digits, doubled separators and empty parts', () => {
    expect(isSlug('1stem')).toBe(false);
    expect(isSlug('o--stem')).toBe(false);
    expect(isSlug('o-')).toBe(false);
    expect(isSlug('')).toBe(false);
    expect(() => asSlug('class', 'o stem')).toThrow(ValidationError);
  });
});

describe('slugify', () => {
  it('folds diacritics and collapses separators', () => {
    expect(slugify('Ǎ-stem  (Attic)')).toBe('a-stem-attic');
    expect(slugify('genitive/dative')).toBe('genitive-dative');
  });

  it('always returns something a slug check accepts', () => {
    for (const input of ['', '   ', '123', '???', 'Ω', '2nd declension']) {
      expect(isSlug(slugify(input))).toBe(true);
    }
  });
});
