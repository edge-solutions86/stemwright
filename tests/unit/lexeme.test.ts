import { describe, expect, it } from 'vitest';
import { featureBundle } from '../../src/domain/features/bundle.js';
import {
  citationForm,
  compareLexemes,
  contradictsInherent,
  hasTag,
  inherentKey,
  lexemeIdFor,
  primaryGloss,
  sameLemma,
} from '../../src/domain/lexicon/lexeme.js';
import { stemSet } from '../../src/domain/lexicon/stem.js';
import { isId } from '../../src/domain/identifiers.js';
import { problemMessages } from '../support/problems.js';
import { ager, bellum, latinFullSchema, makeLexeme, puella } from '../support/lexemes.js';

describe('lexeme', () => {
  it('keeps the lemma, class and glosses', () => {
    expect(puella.lemma).toBe('puella');
    expect(puella.classKey).toBe('first-declension');
    expect(puella.glosses).toEqual(['girl']);
    expect(isId('lexeme', puella.id)).toBe(true);
  });

  it('defaults the homograph index to one', () => {
    expect(puella.homograph).toBe(1);
  });

  it('takes the language from the class registry', () => {
    expect(puella.language).toBe('latin');
  });

  it('refuses a class the registry does not have', () => {
    expect(
      problemMessages(() =>
        makeLexeme({
          lemma: 'nauta',
          classKey: 'fourth-declension',
          stems: stemSet([{ role: 'base', form: 'naut' }]),
        }),
      ),
    ).toEqual(['classKey is not a class of latin: fourth-declension']);
  });

  it('refuses an inherent feature the schema does not know', () => {
    expect(
      problemMessages(() =>
        makeLexeme({
          lemma: 'nauta',
          classKey: 'first-declension',
          stems: stemSet([{ role: 'base', form: 'naut' }]),
          inherent: { tense: 'pres' },
        }),
      ),
    ).toEqual(['inherent.tense is not a dimension of latin']);
  });

  it('refuses a homograph index below one', () => {
    expect(
      problemMessages(() =>
        makeLexeme({
          lemma: 'malus',
          classKey: 'second-declension',
          stems: stemSet([{ role: 'base', form: 'mal' }]),
          homograph: 0,
        }),
      ),
    ).toEqual(['homograph must be a positive whole number: 0']);
  });

  it('refuses a repeated tag', () => {
    expect(
      problemMessages(() =>
        makeLexeme({
          lemma: 'nauta',
          classKey: 'first-declension',
          stems: stemSet([{ role: 'base', form: 'naut' }]),
          tags: ['core', 'core'],
        }),
      ),
    ).toEqual(['tags holds the same tag twice']);
  });

  it('refuses a blank lemma', () => {
    expect(() =>
      makeLexeme({
        lemma: '   ',
        classKey: 'first-declension',
        stems: stemSet([{ role: 'base', form: 'x' }]),
      }),
    ).toThrow(/lemma must not be blank/);
  });
});

describe('lexemeIdFor', () => {
  it('is stable for the same lemma and homograph', () => {
    expect(lexemeIdFor('latin', 'puella', 1)).toBe(lexemeIdFor('latin', 'puella', 1));
    expect(lexemeIdFor('latin', 'puella', 1)).toBe(puella.id);
  });

  it('ignores case and surrounding space in the lemma', () => {
    expect(lexemeIdFor('latin', '  PUELLA ', 1)).toBe(lexemeIdFor('latin', 'puella', 1));
  });

  it('separates homographs and languages', () => {
    expect(lexemeIdFor('latin', 'malus', 1)).not.toBe(lexemeIdFor('latin', 'malus', 2));
    expect(lexemeIdFor('latin', 'malus', 1)).not.toBe(lexemeIdFor('greek', 'malus', 1));
  });
});

describe('citationForm', () => {
  it('is the bare lemma for the first homograph', () => {
    expect(citationForm(puella)).toBe('puella');
  });

  it('marks a later homograph', () => {
    const second = makeLexeme({
      lemma: 'malus',
      classKey: 'second-declension',
      stems: stemSet([{ role: 'base', form: 'mal' }]),
      homograph: 2,
      glosses: ['apple tree'],
    });
    expect(citationForm(second)).toBe('malus (2)');
  });
});

describe('glosses and tags', () => {
  it('reads the first gloss as the primary one', () => {
    expect(primaryGloss(bellum)).toBe('war');
    expect(primaryGloss(ager)).toBe('field');
  });

  it('gives nothing when there are no glosses', () => {
    const bare = makeLexeme({
      lemma: 'res',
      classKey: 'third-declension',
      stems: stemSet([{ role: 'base', form: 'r' }]),
    });
    expect(primaryGloss(bare)).toBeUndefined();
  });

  it('answers whether a tag is present', () => {
    expect(hasTag(puella, 'core')).toBe(true);
    expect(hasTag(puella, 'rare')).toBe(false);
  });
});

describe('contradictsInherent', () => {
  it('is false when the bundle says nothing about the inherent dimension', () => {
    const bundle = featureBundle(latinFullSchema, { case: 'gen', number: 'sg' });
    expect(contradictsInherent(puella, bundle)).toBe(false);
  });

  it('is false when the bundle agrees', () => {
    const bundle = featureBundle(latinFullSchema, { case: 'gen', gender: 'fem' });
    expect(contradictsInherent(puella, bundle)).toBe(false);
  });

  it('is true when the bundle asks for another value', () => {
    const bundle = featureBundle(latinFullSchema, { case: 'gen', gender: 'masc' });
    expect(contradictsInherent(puella, bundle)).toBe(true);
  });

  it('is never true for a lexeme with no inherent features', () => {
    const bare = makeLexeme({
      lemma: 'res',
      classKey: 'third-declension',
      stems: stemSet([{ role: 'base', form: 'r' }]),
    });
    const bundle = featureBundle(latinFullSchema, { gender: 'neut' });
    expect(contradictsInherent(bare, bundle)).toBe(false);
  });
});

describe('comparison helpers', () => {
  it('reads the inherent key back', () => {
    expect(inherentKey(puella)).toBe('gender=fem');
  });

  it('matches lemmas without regard to case', () => {
    const shouted = makeLexeme({
      lemma: 'PUELLA',
      classKey: 'first-declension',
      stems: stemSet([{ role: 'base', form: 'puell' }]),
    });
    expect(sameLemma(puella, shouted)).toBe(true);
    expect(sameLemma(puella, bellum)).toBe(false);
  });

  it('sorts by lemma then homograph', () => {
    const sorted = [bellum, puella, ager].sort(compareLexemes).map((entry) => entry.lemma);
    expect(sorted).toEqual(['ager', 'bellum', 'puella']);
  });
});
