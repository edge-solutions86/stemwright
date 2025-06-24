import { describe, expect, it } from 'vitest';
import { ValidationError } from '../../src/domain/errors.js';
import {
  GREEK_BETA_CODE,
  NO_TRANSLITERATION,
  isTransliterable,
  matchesTransliterated,
  requireRule,
  transliterate,
  transliterationTable,
} from '../../src/domain/ortho/transliteration.js';
import { problemMessages } from '../support/problems.js';

describe('transliterationTable', () => {
  it('sorts the rules longest first', () => {
    const table = transliterationTable('x', [
      { from: 't', to: 'τ' },
      { from: 'th', to: 'θ' },
    ]);
    expect(table.rules[0]?.from).toBe('th');
  });

  it('lower cases and trims the source side', () => {
    const table = transliterationTable('x', [{ from: '  TH  ', to: 'θ' }]);
    expect(table.rules[0]?.from).toBe('th');
  });

  it('refuses a blank source', () => {
    expect(problemMessages(() => transliterationTable('x', [{ from: '   ', to: 'θ' }]))).toEqual([
      'rules.0.from must not be blank',
    ]);
  });

  it('refuses the same source mapped twice', () => {
    expect(
      problemMessages(() =>
        transliterationTable('x', [
          { from: 'th', to: 'θ' },
          { from: 'TH', to: 'τ' },
        ]),
      ),
    ).toEqual(['rules.1.from is mapped twice: th']);
  });

  it('builds an empty table', () => {
    expect(transliterationTable('x', []).rules).toEqual([]);
  });
});

describe('transliterate', () => {
  it('maps a single letter', () => {
    expect(transliterate(GREEK_BETA_CODE, 'a')).toBe('α');
  });

  it('prefers a longer match', () => {
    expect(transliterate(GREEK_BETA_CODE, 'th')).toBe('θ');
    expect(transliterate(GREEK_BETA_CODE, 'ht')).toBe('ητ');
  });

  it('maps a whole word', () => {
    expect(transliterate(GREEK_BETA_CODE, 'anthrwpos')).toBe('ανθρωποσ');
  });

  /**
   * The table has one sigma and it is the medial one. Which shape a sigma takes
   * at the end of a word is a spelling question, and the fold that already
   * answers it is the right place for it rather than a second rule here that
   * would have to know where the word ends.
   */
  it('leaves the final sigma to the folding step', () => {
    expect(transliterate(GREEK_BETA_CODE, 'logos').endsWith('σ')).toBe(true);
  });

  it('leaves a character it has no rule for alone', () => {
    expect(transliterate(GREEK_BETA_CODE, 'a1')).toBe('α1');
  });

  it('ignores case and surrounding space', () => {
    expect(transliterate(GREEK_BETA_CODE, '  LOGOS ')).toBe('λογοσ');
  });

  it('changes nothing under an empty table', () => {
    expect(transliterate(NO_TRANSLITERATION, 'anthrwpos')).toBe('anthrwpos');
  });

  it('maps an empty string to an empty string', () => {
    expect(transliterate(GREEK_BETA_CODE, '')).toBe('');
  });
});

describe('matchesTransliterated', () => {
  it('accepts a transliterated answer', () => {
    expect(matchesTransliterated(GREEK_BETA_CODE, 'λογος', 'logos')).toBe(false);
    expect(matchesTransliterated(GREEK_BETA_CODE, 'λογοσ', 'logos')).toBe(true);
  });

  it('ignores accents on the expected form', () => {
    expect(matchesTransliterated(GREEK_BETA_CODE, 'λόγοσ', 'logos')).toBe(true);
  });

  it('refuses an answer that transliterates to something else', () => {
    expect(matchesTransliterated(GREEK_BETA_CODE, 'λογοσ', 'nomos')).toBe(false);
  });

  it('is always false under an empty table', () => {
    expect(matchesTransliterated(NO_TRANSLITERATION, 'logos', 'logos')).toBe(false);
  });
});

describe('isTransliterable', () => {
  it('is true when every letter has a rule', () => {
    expect(isTransliterable(GREEK_BETA_CODE, 'logos')).toBe(true);
  });

  it('is false when something has none', () => {
    expect(isTransliterable(GREEK_BETA_CODE, 'log0s')).toBe(false);
  });

  it('is false for an empty string', () => {
    expect(isTransliterable(GREEK_BETA_CODE, '')).toBe(false);
  });
});

describe('requireRule', () => {
  it('finds a rule and refuses one that is not there', () => {
    expect(requireRule(GREEK_BETA_CODE, 'th').to).toBe('θ');
    expect(() => requireRule(GREEK_BETA_CODE, 'q')).toThrow(ValidationError);
    expect(() => requireRule(GREEK_BETA_CODE, 'q')).toThrow(/not in the greek table/);
  });
});

describe('the greek table', () => {
  it('covers every letter of the alphabet a first year needs', () => {
    for (const letter of 'abgdezhiklmnoprstuw') {
      expect(isTransliterable(GREEK_BETA_CODE, letter)).toBe(true);
    }
  });

  it('maps the digraphs a beta code reader expects', () => {
    expect(transliterate(GREEK_BETA_CODE, 'ph')).toBe('φ');
    expect(transliterate(GREEK_BETA_CODE, 'ch')).toBe('χ');
    expect(transliterate(GREEK_BETA_CODE, 'ps')).toBe('ψ');
    expect(transliterate(GREEK_BETA_CODE, 'ks')).toBe('ξ');
  });
});
