import { describe, expect, it } from 'vitest';
import { bundleKey, featureBundle } from '../../src/domain/features/bundle.js';
import {
  EMPTY_SELECTOR,
  describeSelector,
  expandSelector,
  formatSelector,
  intersectSelectors,
  isEmptySelection,
  matchesSelector,
  parseSelector,
  selectBundles,
  selectorCardinality,
} from '../../src/domain/features/selector.js';
import { problemMessages } from '../support/problems.js';
import { latinAdjectiveSchema, latinNounSchema } from '../support/schemas.js';

const parse = (text: string) => parseSelector(latinNounSchema, text);

describe('parseSelector', () => {
  it('reads a single clause', () => {
    expect(formatSelector(parse('number=pl'))).toBe('number=pl');
  });

  it('reads several values separated by pipes', () => {
    expect(formatSelector(parse('case=nom|gen'))).toBe('case=gen|nom');
  });

  it('reads a negated clause', () => {
    expect(formatSelector(parse('case!=voc'))).toBe('case!=voc');
  });

  it('puts clauses into schema order', () => {
    expect(formatSelector(parse('number=pl,case=dat'))).toBe('case=dat,number=pl');
  });

  it('reads an empty string as the selector that matches everything', () => {
    expect(parse('')).toEqual(EMPTY_SELECTOR);
  });

  it('refuses an empty clause and a clause with no equals sign', () => {
    expect(() => parse('case=nom,')).toThrow(/empty clause/);
    expect(() => parse('casenom')).toThrow(/dimension and values/);
    expect(() => parse('=nom')).toThrow(/dimension and values/);
  });

  it('refuses an unknown dimension or value', () => {
    expect(problemMessages(() => parse('tense=pres'))).toEqual([
      'tense is not a dimension of latin',
    ]);
    expect(problemMessages(() => parse('case=inst'))).toEqual([
      'case does not admit the value inst',
    ]);
  });

  it('refuses the same dimension constrained twice the same way', () => {
    expect(problemMessages(() => parse('case=nom,case=gen'))).toEqual([
      'case is constrained twice in the same direction',
    ]);
  });

  it('allows a positive and a negative clause on one dimension', () => {
    expect(formatSelector(parse('case=nom|gen|dat,case!=dat'))).toBe('case=dat|gen|nom,case!=dat');
  });

  it('refuses a clause with no values at all', () => {
    expect(problemMessages(() => parse('case='))).toEqual(['case must name at least one value']);
  });
});

describe('matchesSelector', () => {
  const nomSg = featureBundle(latinNounSchema, { case: 'nom', number: 'sg' });
  const genPl = featureBundle(latinNounSchema, { case: 'gen', number: 'pl' });

  it('matches everything when it has no clauses', () => {
    expect(matchesSelector(EMPTY_SELECTOR, nomSg)).toBe(true);
    expect(matchesSelector(EMPTY_SELECTOR, featureBundle(latinNounSchema, {}))).toBe(true);
  });

  it('matches on a listed value', () => {
    expect(matchesSelector(parse('case=nom|gen'), nomSg)).toBe(true);
    expect(matchesSelector(parse('case=dat'), nomSg)).toBe(false);
  });

  it('inverts a negated clause', () => {
    expect(matchesSelector(parse('case!=nom'), nomSg)).toBe(false);
    expect(matchesSelector(parse('case!=nom'), genPl)).toBe(true);
  });

  it('needs every clause to hold', () => {
    expect(matchesSelector(parse('case=gen,number=pl'), genPl)).toBe(true);
    expect(matchesSelector(parse('case=gen,number=sg'), genPl)).toBe(false);
  });

  it('does not match a bundle that leaves the dimension open', () => {
    const caseOnly = featureBundle(latinNounSchema, { case: 'gen' });
    expect(matchesSelector(parse('number=pl'), caseOnly)).toBe(false);
    expect(matchesSelector(parse('number!=pl'), caseOnly)).toBe(false);
  });
});

describe('selectBundles and expandSelector', () => {
  it('filters a list down to what matches', () => {
    const bundles = [
      featureBundle(latinNounSchema, { case: 'nom', number: 'sg' }),
      featureBundle(latinNounSchema, { case: 'nom', number: 'pl' }),
    ];
    expect(selectBundles(parse('number=pl'), bundles).map(bundleKey)).toEqual([
      'case=nom;number=pl',
    ]);
  });

  it('expands against the whole schema in table order', () => {
    const expanded = expandSelector(latinNounSchema, parse('number=pl'));
    expect(expanded).toHaveLength(6);
    expect(bundleKey(expanded[0]!)).toBe('case=nom;number=pl');
  });
});

describe('selectorCardinality', () => {
  it('counts the whole space when unconstrained', () => {
    expect(selectorCardinality(latinNounSchema, EMPTY_SELECTOR)).toBe(12);
  });

  it('multiplies the allowed values of each dimension', () => {
    expect(selectorCardinality(latinNounSchema, parse('case=nom|gen'))).toBe(4);
    expect(selectorCardinality(latinNounSchema, parse('case=nom|gen,number=sg'))).toBe(2);
  });

  it('counts a negation as everything else', () => {
    expect(selectorCardinality(latinNounSchema, parse('case!=voc'))).toBe(10);
  });

  it('agrees with what expansion produces', () => {
    for (const text of ['', 'case=nom', 'case!=voc,number=pl', 'case=nom|gen|dat']) {
      const selection = parse(text);
      expect(expandSelector(latinNounSchema, selection)).toHaveLength(
        selectorCardinality(latinNounSchema, selection),
      );
    }
  });

  it('reports an empty selection when a negation excludes everything', () => {
    const all = parse('case!=nom|gen|dat|acc|abl|voc');
    expect(isEmptySelection(latinNounSchema, all)).toBe(true);
    expect(isEmptySelection(latinNounSchema, EMPTY_SELECTOR)).toBe(false);
  });
});

describe('intersectSelectors', () => {
  const narrow = (a: string, b: string) =>
    formatSelector(intersectSelectors(latinNounSchema, parse(a), parse(b)));

  it('keeps clauses that only one side names', () => {
    expect(narrow('case=nom', 'number=pl')).toBe('case=nom,number=pl');
  });

  it('takes the shared values of a repeated dimension', () => {
    expect(narrow('case=nom|gen|dat', 'case=gen|dat|acc')).toBe('case=dat|gen');
  });

  it('unions the excluded values of a repeated negation', () => {
    expect(narrow('case!=nom', 'case!=gen')).toBe('case!=gen|nom');
  });

  it('produces an empty selection when the two do not overlap', () => {
    const result = intersectSelectors(latinNounSchema, parse('case=nom'), parse('case=gen'));
    expect(isEmptySelection(latinNounSchema, result)).toBe(true);
  });

  it('is unchanged by intersecting with the everything selector', () => {
    expect(narrow('case=nom|gen', '')).toBe('case=gen|nom');
  });
});

describe('describeSelector', () => {
  it('says so when nothing is constrained', () => {
    expect(describeSelector(latinNounSchema, EMPTY_SELECTOR)).toBe('every cell in latin');
  });

  it('reads clauses out in words', () => {
    const selection = parseSelector(latinAdjectiveSchema, 'case=nom|gen,gender!=neut');
    expect(describeSelector(latinAdjectiveSchema, selection)).toBe(
      'case of gen or nom, gender other than neut',
    );
  });
});
