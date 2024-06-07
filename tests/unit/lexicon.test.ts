import { describe, expect, it } from 'vitest';
import {
  addLexeme,
  allLexemes,
  buildLexicon,
  emptyLexicon,
  lexemeById,
  lexemesByClass,
  lexemesByLemma,
  lexemesByTag,
  lexemesUnderClass,
  lexiconSize,
  lexiconStats,
  nextHomograph,
  removeLexeme,
  replaceLexeme,
  requireLexeme,
  tagsOf,
} from '../../src/domain/lexicon/lexicon.js';
import { ConflictError, NotFoundError } from '../../src/domain/errors.js';
import { stemSet } from '../../src/domain/lexicon/stem.js';
import { latinClasses } from '../support/classes.js';
import { ager, bellum, makeLexeme, puella } from '../support/lexemes.js';

const base = buildLexicon('latin', [puella, bellum, ager]);

const nauta = makeLexeme({
  lemma: 'nauta',
  classKey: 'first-declension',
  stems: stemSet([{ role: 'base', form: 'naut' }]),
  inherent: { gender: 'masc' },
  glosses: ['sailor'],
  tags: ['core'],
});

describe('buildLexicon', () => {
  it('indexes every entry by id', () => {
    expect(lexiconSize(base)).toBe(3);
    expect(lexemeById(base, puella.id)?.lemma).toBe('puella');
  });

  it('refuses the same id twice', () => {
    expect(() => buildLexicon('latin', [puella, puella])).toThrow(ConflictError);
  });

  it('builds an empty one', () => {
    expect(lexiconSize(emptyLexicon('latin'))).toBe(0);
    expect(allLexemes(emptyLexicon('latin'))).toEqual([]);
  });

  it('returns entries in lemma order', () => {
    expect(allLexemes(base).map((entry) => entry.lemma)).toEqual(['ager', 'bellum', 'puella']);
  });
});

describe('lookups', () => {
  it('finds by id and reports a miss', () => {
    expect(lexemeById(base, 'lex_zzzzzz')).toBeUndefined();
    expect(() => requireLexeme(base, 'lex_zzzzzz')).toThrow(NotFoundError);
  });

  it('finds every homograph of a lemma in order', () => {
    const second = makeLexeme({
      lemma: 'ager',
      classKey: 'second-declension',
      stems: stemSet([{ role: 'base', form: 'ager' }]),
      homograph: 2,
    });
    const grown = addLexeme(base, second);
    expect(lexemesByLemma(grown, 'ager').map((entry) => entry.homograph)).toEqual([1, 2]);
  });

  it('is case insensitive on the lemma', () => {
    expect(lexemesByLemma(base, 'PUELLA')).toHaveLength(1);
  });

  it('gives an empty list for a lemma nobody has', () => {
    expect(lexemesByLemma(base, 'vita')).toEqual([]);
  });

  it('indexes by class and by tag', () => {
    expect(lexemesByClass(base, 'first-declension').map((e) => e.lemma)).toEqual(['puella']);
    expect(lexemesByTag(base, 'core').map((e) => e.lemma)).toEqual(['puella']);
    expect(lexemesByTag(base, 'rare')).toEqual([]);
    expect(tagsOf(base)).toEqual(['chapter-one', 'core']);
  });
});

describe('nextHomograph', () => {
  it('starts at one for a lemma nobody has', () => {
    expect(nextHomograph(base, 'vita')).toBe(1);
  });

  it('steps past the highest one already there', () => {
    expect(nextHomograph(base, 'ager')).toBe(2);
  });
});

describe('lexemesUnderClass', () => {
  it('includes the class itself', () => {
    expect(lexemesUnderClass(base, latinClasses, 'first-declension').map((e) => e.lemma)).toEqual([
      'puella',
    ]);
  });

  it('includes every descendant class', () => {
    expect(lexemesUnderClass(base, latinClasses, 'second-declension').map((e) => e.lemma)).toEqual([
      'ager',
      'bellum',
    ]);
  });

  it('is empty for a class nothing uses', () => {
    expect(lexemesUnderClass(base, latinClasses, 'first-conjugation')).toEqual([]);
  });
});

describe('addLexeme, replaceLexeme and removeLexeme', () => {
  it('adds and leaves the original alone', () => {
    const grown = addLexeme(base, nauta);
    expect(lexiconSize(grown)).toBe(4);
    expect(lexiconSize(base)).toBe(3);
  });

  it('refuses to add an id that is already there', () => {
    expect(() => addLexeme(base, puella)).toThrow(ConflictError);
  });

  it('replaces an entry in place', () => {
    const changed = makeLexeme({
      lemma: 'puella',
      classKey: 'first-declension',
      stems: stemSet([{ role: 'base', form: 'puell' }]),
      inherent: { gender: 'fem' },
      glosses: ['girl', 'maiden'],
      tags: ['core'],
    });
    const updated = replaceLexeme(base, changed);
    expect(lexiconSize(updated)).toBe(3);
    expect(requireLexeme(updated, puella.id).glosses).toEqual(['girl', 'maiden']);
    expect(lexemesByTag(updated, 'chapter-one')).toEqual([]);
  });

  it('refuses to replace something that is not there', () => {
    expect(() => replaceLexeme(base, nauta)).toThrow(NotFoundError);
  });

  it('removes by id and refuses an id it does not hold', () => {
    expect(lexiconSize(removeLexeme(base, bellum.id))).toBe(2);
    expect(() => removeLexeme(base, nauta.id)).toThrow(NotFoundError);
  });
});

describe('lexiconStats', () => {
  it('counts by part of speech through the class chain', () => {
    const stats = lexiconStats(base, latinClasses);
    expect(stats.total).toBe(3);
    expect(stats.byPartOfSpeech.get('noun')).toBe(3);
    expect(stats.classesUsed).toBe(3);
  });

  it('counts entries with no tags and no glosses', () => {
    const bare = makeLexeme({
      lemma: 'res',
      classKey: 'third-declension',
      stems: stemSet([{ role: 'base', form: 'r' }]),
    });
    const stats = lexiconStats(addLexeme(base, bare), latinClasses);
    expect(stats.untagged).toBe(3);
    expect(stats.withoutGloss).toBe(1);
  });

  it('reports zeros for an empty lexicon', () => {
    const stats = lexiconStats(emptyLexicon('latin'), latinClasses);
    expect(stats.total).toBe(0);
    expect(stats.byPartOfSpeech.size).toBe(0);
  });
});
