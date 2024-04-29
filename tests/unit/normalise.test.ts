import { describe, expect, it } from 'vitest';
import {
  baseOf,
  codePointLength,
  codePoints,
  collapseSpaces,
  hasMarks,
  isBlank,
  markClusters,
  marksOf,
  normaliseInput,
  requireText,
  stripInvisible,
  stripMarks,
  toForm,
} from '../../src/domain/text/normalise.js';
import { ValidationError } from '../../src/domain/errors.js';

const cp = (point: number) => String.fromCodePoint(point);
const ZWSP = cp(0x200b);
const ZWJ = cp(0x200d);
const BOM = cp(0xfeff);
const NBSP = cp(0x00a0);
const IDEOGRAPHIC_SPACE = cp(0x3000);
const COMBINING_ACUTE = cp(0x0301);
const COMBINING_PSILI = cp(0x0313);

const ALPHA = 'α';
const ALPHA_TONOS = 'ά';
const ALPHA_THEN_TONOS = ALPHA + COMBINING_ACUTE;
const ALPHA_PSILI = 'ἀ';
const ALPHA_PSILI_OXIA = 'ἄ';

describe('toForm', () => {
  it('composes and decomposes the same letter', () => {
    expect(toForm(ALPHA_THEN_TONOS, 'NFC')).toBe(ALPHA_TONOS);
    expect(toForm(ALPHA_TONOS, 'NFD')).toBe(ALPHA_THEN_TONOS);
  });
});

describe('stripInvisible', () => {
  it('drops zero width marks', () => {
    expect(stripInvisible(`a${ZWSP}b${ZWJ}c${BOM}`)).toBe('abc');
  });

  it('drops control characters', () => {
    expect(stripInvisible(`a${cp(0x0007)}b${cp(0x007f)}`)).toBe('ab');
  });

  it('keeps tab, newline and carriage return for the space pass to handle', () => {
    expect(stripInvisible('a\tb\nc\r')).toBe('a\tb\nc\r');
  });
});

describe('collapseSpaces', () => {
  it('turns any run of spacing into one plain space', () => {
    expect(collapseSpaces('  a \t\n b c  ')).toBe('a b c');
  });

  it('empties a string of nothing but spacing', () => {
    expect(collapseSpaces(` ${IDEOGRAPHIC_SPACE}\t `)).toBe('');
  });

  it('treats a no break space as spacing', () => {
    expect(collapseSpaces(`a${NBSP}b`)).toBe('a b');
  });
});

describe('normaliseInput', () => {
  it('runs invisible stripping before space collapsing', () => {
    expect(normaliseInput(`a ${ZWSP} b`)).toBe('a b');
  });

  it('composes by default and decomposes on request', () => {
    expect(normaliseInput(ALPHA_THEN_TONOS)).toBe(ALPHA_TONOS);
    expect(normaliseInput(ALPHA_TONOS, 'NFD')).toBe(ALPHA_THEN_TONOS);
  });

  it('makes two spellings of the same word compare equal', () => {
    expect(normaliseInput(`  ${ALPHA_THEN_TONOS}${ZWSP}  `)).toBe(normaliseInput(ALPHA_TONOS));
  });
});

describe('isBlank', () => {
  it('is true for spacing and invisible marks only', () => {
    expect(isBlank('   ')).toBe(true);
    expect(isBlank(`${ZWSP}${BOM}`)).toBe(true);
    expect(isBlank('')).toBe(true);
  });

  it('is false as soon as anything visible is there', () => {
    expect(isBlank(' a ')).toBe(false);
  });
});

describe('requireText', () => {
  it('returns the cleaned value', () => {
    expect(requireText('lemma', '  oi  kos  ')).toBe('oi kos');
  });

  it('refuses blanks and names the path', () => {
    expect(() => requireText('lemma', ` ${ZWSP} `)).toThrow(ValidationError);
    expect(() => requireText('lemma', '')).toThrow(/lemma must not be blank/);
  });

  it('refuses text over the cap it was given', () => {
    expect(() => requireText('gloss', 'a'.repeat(9), 8)).toThrow(/at most 8 characters/);
    expect(requireText('gloss', 'a'.repeat(8), 8)).toHaveLength(8);
  });
});

describe('codePoints', () => {
  it('counts a surrogate pair once', () => {
    const withAstral = `a${cp(0x1d7d9)}b`;
    expect(codePointLength(withAstral)).toBe(3);
    expect(withAstral.length).toBe(4);
  });

  it('splits into whole code points', () => {
    expect(codePoints(cp(0x1d7d9))).toEqual([cp(0x1d7d9)]);
  });
});

describe('markClusters', () => {
  it('binds trailing marks to the letter before them', () => {
    const clusters = markClusters(`${ALPHA_PSILI}ν`);
    expect(clusters).toHaveLength(2);
    expect(baseOf(clusters[0]!)).toBe(ALPHA);
    expect(marksOf(clusters[0]!)).toEqual([COMBINING_PSILI]);
  });

  it('holds two marks on one base', () => {
    const clusters = markClusters(ALPHA_PSILI_OXIA);
    expect(clusters).toHaveLength(1);
    expect(marksOf(clusters[0]!)).toHaveLength(2);
  });

  it('leaves an unmarked word one cluster per letter', () => {
    expect(markClusters('logos')).toHaveLength(5);
  });

  it('does not lose a leading mark', () => {
    expect(markClusters(`${COMBINING_ACUTE}a`)).toHaveLength(2);
  });
});

describe('hasMarks and stripMarks', () => {
  it('sees a mark whether the input was composed or not', () => {
    expect(hasMarks(ALPHA_TONOS)).toBe(true);
    expect(hasMarks(ALPHA_THEN_TONOS)).toBe(true);
    expect(hasMarks(ALPHA)).toBe(false);
  });

  it('strips back to the bare letters', () => {
    expect(stripMarks(`${ALPHA_PSILI_OXIA}ν`)).toBe(`${ALPHA}ν`);
    expect(stripMarks('resumé')).toBe('resume');
  });

  it('leaves an unmarked string alone', () => {
    expect(stripMarks('logos')).toBe('logos');
  });
});
