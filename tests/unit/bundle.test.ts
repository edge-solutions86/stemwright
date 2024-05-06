import { describe, expect, it } from 'vitest';
import {
  bundleEquals,
  bundleKey,
  bundleSize,
  bundleSubsumes,
  compareBundles,
  dimensionsOf,
  enumerateBundles,
  featureBundle,
  isComplete,
  missingDimensions,
  parseBundleKey,
  requireValue,
  unsafeBundle,
  valueOf,
  withFeature,
  withoutFeature,
} from '../../src/domain/features/bundle.js';
import { ValidationError } from '../../src/domain/errors.js';
import { problemMessages } from '../support/problems.js';
import { greekVerbSchema, latinAdjectiveSchema, latinNounSchema } from '../support/schemas.js';

describe('featureBundle', () => {
  it('puts the pairs into schema order whatever order they arrived in', () => {
    const bundle = featureBundle(latinAdjectiveSchema, {
      gender: 'fem',
      number: 'pl',
      case: 'dat',
    });
    expect(dimensionsOf(bundle)).toEqual(['case', 'number', 'gender']);
  });

  it('refuses a dimension the schema does not have', () => {
    expect(problemMessages(() => featureBundle(latinNounSchema, { tense: 'pres' }))).toEqual([
      'tense is not a dimension of latin',
    ]);
  });

  it('refuses a value the dimension does not admit', () => {
    expect(problemMessages(() => featureBundle(latinNounSchema, { case: 'inst' }))).toEqual([
      'case does not admit the value inst',
    ]);
  });

  it('reports every bad pair at once', () => {
    try {
      featureBundle(latinNounSchema, { tense: 'pres', case: 'inst' });
      expect.unreachable('the bundle should not have built');
    } catch (error) {
      expect((error as ValidationError).details).toHaveLength(2);
    }
  });

  it('accepts an empty bundle', () => {
    expect(bundleSize(featureBundle(latinNounSchema, {}))).toBe(0);
  });
});

describe('reading values', () => {
  const bundle = featureBundle(latinNounSchema, { case: 'gen', number: 'sg' });

  it('reads a value that is there', () => {
    expect(valueOf(bundle, 'case')).toBe('gen');
    expect(requireValue(bundle, 'number')).toBe('sg');
  });

  it('gives nothing for a dimension the bundle does not fix', () => {
    expect(valueOf(bundle, 'gender')).toBeUndefined();
    expect(() => requireValue(bundle, 'gender')).toThrow(/has no value for gender/);
  });
});

describe('bundleKey', () => {
  it('writes dimensions in schema order regardless of input order', () => {
    const a = featureBundle(latinAdjectiveSchema, { number: 'sg', case: 'nom', gender: 'masc' });
    const b = featureBundle(latinAdjectiveSchema, { gender: 'masc', case: 'nom', number: 'sg' });
    expect(bundleKey(a)).toBe('case=nom;number=sg;gender=masc');
    expect(bundleKey(a)).toBe(bundleKey(b));
  });

  it('writes an empty bundle as an empty string', () => {
    expect(bundleKey(featureBundle(latinNounSchema, {}))).toBe('');
  });

  it('round trips through the parser', () => {
    const bundle = featureBundle(greekVerbSchema, {
      tense: 'aor',
      mood: 'subj',
      voice: 'mid',
      person: 'second',
      number: 'pl',
    });
    expect(bundleKey(parseBundleKey(greekVerbSchema, bundleKey(bundle)))).toBe(bundleKey(bundle));
  });
});

describe('parseBundleKey', () => {
  it('reads an empty key as an empty bundle', () => {
    expect(bundleSize(parseBundleKey(latinNounSchema, ''))).toBe(0);
  });

  it('refuses a part with no equals sign', () => {
    expect(() => parseBundleKey(latinNounSchema, 'casenom')).toThrow(/dimension value pair/);
    expect(() => parseBundleKey(latinNounSchema, '=nom')).toThrow(/dimension value pair/);
  });

  it('refuses a repeated dimension', () => {
    expect(() => parseBundleKey(latinNounSchema, 'case=nom;case=gen')).toThrow(/names case twice/);
  });

  it('still validates against the schema', () => {
    expect(() => parseBundleKey(latinNounSchema, 'case=inst')).toThrow(ValidationError);
  });
});

describe('bundleEquals and bundleSubsumes', () => {
  const full = featureBundle(latinAdjectiveSchema, { case: 'nom', number: 'sg', gender: 'masc' });
  const partial = featureBundle(latinAdjectiveSchema, { case: 'nom', number: 'sg' });

  it('is equal only when the pairs match exactly', () => {
    expect(bundleEquals(full, full)).toBe(true);
    expect(bundleEquals(full, partial)).toBe(false);
  });

  it('subsumes when every pair of the broader bundle is present', () => {
    expect(bundleSubsumes(partial, full)).toBe(true);
    expect(bundleSubsumes(full, partial)).toBe(false);
  });

  it('is subsumed by the empty bundle', () => {
    const empty = featureBundle(latinAdjectiveSchema, {});
    expect(bundleSubsumes(empty, full)).toBe(true);
  });
});

describe('withFeature and withoutFeature', () => {
  const bundle = featureBundle(latinAdjectiveSchema, { case: 'nom', number: 'sg' });

  it('adds a pair and keeps schema order', () => {
    const grown = withFeature(latinAdjectiveSchema, bundle, 'gender', 'neut');
    expect(bundleKey(grown)).toBe('case=nom;number=sg;gender=neut');
  });

  it('replaces a pair that is already there', () => {
    const changed = withFeature(latinAdjectiveSchema, bundle, 'case', 'acc');
    expect(bundleKey(changed)).toBe('case=acc;number=sg');
  });

  it('leaves the original alone', () => {
    withFeature(latinAdjectiveSchema, bundle, 'gender', 'neut');
    expect(bundleKey(bundle)).toBe('case=nom;number=sg');
  });

  it('drops a pair and tolerates one that was not there', () => {
    expect(bundleKey(withoutFeature(bundle, 'number'))).toBe('case=nom');
    expect(bundleKey(withoutFeature(bundle, 'gender'))).toBe('case=nom;number=sg');
  });

  it('refuses to add a value the schema rejects', () => {
    expect(() => withFeature(latinAdjectiveSchema, bundle, 'gender', 'common')).toThrow(
      ValidationError,
    );
  });
});

describe('completeness', () => {
  it('needs every required dimension and ignores optional ones', () => {
    const bundle = featureBundle(greekVerbSchema, {
      tense: 'pres',
      mood: 'ind',
      voice: 'act',
      person: 'first',
    });
    expect(isComplete(greekVerbSchema, bundle)).toBe(true);
    expect(missingDimensions(greekVerbSchema, bundle)).toEqual([]);
  });

  it('names what is missing in schema order', () => {
    const bundle = featureBundle(greekVerbSchema, { voice: 'act' });
    expect(missingDimensions(greekVerbSchema, bundle)).toEqual(['tense', 'mood', 'person']);
    expect(isComplete(greekVerbSchema, bundle)).toBe(false);
  });
});

describe('compareBundles', () => {
  it('sorts by the first dimension then the next', () => {
    const sorted = [
      featureBundle(latinNounSchema, { case: 'gen', number: 'sg' }),
      featureBundle(latinNounSchema, { case: 'nom', number: 'pl' }),
      featureBundle(latinNounSchema, { case: 'nom', number: 'sg' }),
    ].sort((a, b) => compareBundles(latinNounSchema, a, b));
    expect(sorted.map(bundleKey)).toEqual([
      'case=nom;number=sg',
      'case=nom;number=pl',
      'case=gen;number=sg',
    ]);
  });

  it('sorts a missing value last', () => {
    const withNumber = featureBundle(latinNounSchema, { case: 'nom', number: 'sg' });
    const withoutNumber = featureBundle(latinNounSchema, { case: 'nom' });
    expect(compareBundles(latinNounSchema, withNumber, withoutNumber)).toBeLessThan(0);
    expect(compareBundles(latinNounSchema, withoutNumber, withNumber)).toBeGreaterThan(0);
  });

  it('calls identical bundles equal', () => {
    const bundle = featureBundle(latinNounSchema, { case: 'nom', number: 'sg' });
    expect(compareBundles(latinNounSchema, bundle, bundle)).toBe(0);
  });
});

describe('enumerateBundles', () => {
  it('produces the full cross product', () => {
    expect(enumerateBundles(latinNounSchema)).toHaveLength(12);
    expect(enumerateBundles(latinAdjectiveSchema)).toHaveLength(36);
  });

  it('starts at the first value of every dimension', () => {
    expect(bundleKey(enumerateBundles(latinNounSchema)[0]!)).toBe('case=nom;number=sg');
  });

  it('produces distinct keys', () => {
    const keys = enumerateBundles(latinAdjectiveSchema).map(bundleKey);
    expect(new Set(keys).size).toBe(keys.length);
  });
});

describe('unsafeBundle', () => {
  it('copies the pairs it is handed', () => {
    const pairs = [{ dimension: 'case', value: 'nom' }];
    const bundle = unsafeBundle(pairs);
    pairs.push({ dimension: 'number', value: 'sg' });
    expect(bundleSize(bundle)).toBe(1);
  });
});
