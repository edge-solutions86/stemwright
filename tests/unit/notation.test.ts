import { describe, expect, it } from 'vitest';
import { bundleKey, featureBundle } from '../../src/domain/features/bundle.js';
import {
  ambiguousTokens,
  describeBundle,
  formatBundle,
  formatUnambiguous,
  isAmbiguousToken,
  parseBundleNotation,
} from '../../src/domain/features/notation.js';
import { featureSchema } from '../../src/domain/features/schema.js';
import { greekVerbSchema, latinAdjectiveSchema, latinNounSchema } from '../support/schemas.js';

describe('formatBundle', () => {
  const bundle = featureBundle(latinAdjectiveSchema, {
    case: 'gen',
    number: 'pl',
    gender: 'neut',
  });

  it('prints abbreviations joined by dots by default', () => {
    expect(formatBundle(latinAdjectiveSchema, bundle)).toBe('gen.pl.neu');
  });

  it('prints full keys on request', () => {
    expect(formatBundle(latinAdjectiveSchema, bundle, 'key')).toBe('gen.pl.neut');
  });

  it('prints labels separated by spaces', () => {
    expect(formatBundle(latinAdjectiveSchema, bundle, 'label')).toBe('Gen Pl Neut');
  });

  it('prints an empty bundle as an empty string', () => {
    expect(formatBundle(latinNounSchema, featureBundle(latinNounSchema, {}))).toBe('');
  });
});

describe('parseBundleNotation', () => {
  it('reads a dotted notation into a bundle', () => {
    const bundle = parseBundleNotation(latinNounSchema, 'gen.pl');
    expect(bundleKey(bundle)).toBe('case=gen;number=pl');
  });

  it('accepts full keys as well as abbreviations', () => {
    expect(bundleKey(parseBundleNotation(greekVerbSchema, 'aor.subj.mid.second'))).toBe(
      bundleKey(parseBundleNotation(greekVerbSchema, 'aor.subj.mid.2')),
    );
  });

  it('is case insensitive and tolerates surrounding space', () => {
    expect(bundleKey(parseBundleNotation(latinNounSchema, '  GEN.PL '))).toBe('case=gen;number=pl');
  });

  it('reads an empty notation as an empty bundle', () => {
    expect(bundleKey(parseBundleNotation(latinNounSchema, ''))).toBe('');
  });

  it('refuses an empty token', () => {
    expect(() => parseBundleNotation(latinNounSchema, 'gen..pl')).toThrow(/empty token/);
  });

  it('refuses a token no dimension admits', () => {
    expect(() => parseBundleNotation(latinNounSchema, 'gen.inst')).toThrow(
      /is not a feature value in latin/,
    );
  });

  it('refuses to fix the same dimension twice', () => {
    expect(() => parseBundleNotation(latinNounSchema, 'gen.nom')).toThrow(/fixes case twice/);
  });

  it('round trips whatever it printed', () => {
    const bundle = featureBundle(latinAdjectiveSchema, {
      case: 'abl',
      number: 'sg',
      gender: 'fem',
    });
    const printed = formatBundle(latinAdjectiveSchema, bundle, 'key');
    expect(bundleKey(parseBundleNotation(latinAdjectiveSchema, printed))).toBe(bundleKey(bundle));
  });
});

describe('ambiguity', () => {
  const shared = featureSchema({
    language: 'shared',
    dimensions: [
      { key: 'tense', values: ['pres', 'perf'] },
      { key: 'aspect', values: ['perf', 'imperf'] },
    ],
  });

  it('lists the tokens more than one dimension admits', () => {
    expect(ambiguousTokens(shared)).toEqual(['per', 'perf']);
    expect(ambiguousTokens(latinNounSchema)).toEqual([]);
  });

  it('answers for a single token', () => {
    expect(isAmbiguousToken(shared, 'perf')).toBe(true);
    expect(isAmbiguousToken(shared, 'pres')).toBe(false);
  });

  it('refuses a bare ambiguous token and names both dimensions', () => {
    expect(() => parseBundleNotation(shared, 'perf')).toThrow(/ambiguous between tense, aspect/);
  });

  it('accepts the same token once it is qualified', () => {
    expect(bundleKey(parseBundleNotation(shared, 'aspect:perf'))).toBe('aspect=perf');
    expect(bundleKey(parseBundleNotation(shared, 'tense:perf'))).toBe('tense=perf');
  });

  it('refuses a qualifier naming a dimension that is not there', () => {
    expect(() => parseBundleNotation(shared, 'mood:perf')).toThrow(/does not have: mood/);
  });

  it('refuses a qualified value the dimension does not admit', () => {
    expect(() => parseBundleNotation(shared, 'tense:imperf')).toThrow(
      /tense does not admit imperf/,
    );
  });
});

describe('formatUnambiguous', () => {
  const shared = featureSchema({
    language: 'shared',
    dimensions: [
      { key: 'tense', values: ['pres', 'perf'] },
      { key: 'aspect', values: ['perf', 'imperf'] },
    ],
  });

  it('qualifies only the tokens that need it', () => {
    const bundle = featureBundle(shared, { tense: 'pres', aspect: 'perf' });
    expect(formatUnambiguous(shared, bundle)).toBe('pre.aspect:per');
  });

  it('prints a plain notation when nothing is ambiguous', () => {
    const bundle = featureBundle(latinNounSchema, { case: 'dat', number: 'pl' });
    expect(formatUnambiguous(latinNounSchema, bundle)).toBe('dat.pl');
  });

  it('always parses back to the bundle it was given', () => {
    for (const tense of ['pres', 'perf']) {
      for (const aspect of ['perf', 'imperf']) {
        const bundle = featureBundle(shared, { tense, aspect });
        const printed = formatUnambiguous(shared, bundle);
        expect(bundleKey(parseBundleNotation(shared, printed))).toBe(bundleKey(bundle));
      }
    }
  });
});

describe('describeBundle', () => {
  it('reads a bundle out in words in schema order', () => {
    const bundle = featureBundle(greekVerbSchema, {
      voice: 'mid',
      tense: 'aor',
      mood: 'subj',
      person: 'second',
    });
    expect(describeBundle(greekVerbSchema, bundle)).toBe(
      'tense aor, mood subj, voice mid, person second',
    );
  });

  it('says so when nothing is fixed', () => {
    expect(describeBundle(latinNounSchema, featureBundle(latinNounSchema, {}))).toBe(
      'no features fixed',
    );
  });
});
