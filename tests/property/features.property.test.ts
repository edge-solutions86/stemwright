import { describe, expect, it } from 'vitest';
import {
  bundleKey,
  bundleSubsumes,
  compareBundles,
  enumerateBundles,
  featureBundle,
  parseBundleKey,
  withFeature,
  withoutFeature,
} from '../../src/domain/features/bundle.js';
import {
  ambiguousTokens,
  formatBundle,
  formatUnambiguous,
  parseBundleNotation,
} from '../../src/domain/features/notation.js';
import type { FeatureSchema } from '../../src/domain/features/schema.js';
import { schemaCardinality } from '../../src/domain/features/schema.js';
import {
  expandSelector,
  formatSelector,
  matchesSelector,
  parseSelector,
  selectorCardinality,
} from '../../src/domain/features/selector.js';
import type { Random } from '../support/random.js';
import { forEachDraw } from '../support/random.js';
import { greekVerbSchema, latinAdjectiveSchema, latinNounSchema } from '../support/schemas.js';

const schemas: FeatureSchema[] = [latinNounSchema, latinAdjectiveSchema, greekVerbSchema];

function selectorFor(schema: FeatureSchema, random: Random) {
  const clauses = random
    .sample(schema.dimensions, random.int(1, 2))
    .map((dimension) => {
      const values = random.sample(dimension.values, random.int(1, dimension.values.length));
      const negated = random.bool(0.3);
      const listed = values.map((value) => value.key).join('|');
      return `${dimension.key}${negated ? '!=' : '='}${listed}`;
    })
    .join(',');
  return parseSelector(schema, clauses);
}

describe('bundle properties', () => {
  it('round trips every complete bundle through its canonical key', () => {
    forEachDraw(60, 1301, (random) => {
      const schema = random.pick(schemas);
      const bundle = random.pick(enumerateBundles(schema));
      const key = bundleKey(bundle);
      expect(bundleKey(parseBundleKey(schema, key))).toBe(key);
    });
  });

  /**
   * The plain printer is not round trip safe and is not meant to be. The greek
   * schema abbreviates the imperfect to imp, which is also the key of the
   * imperative mood, so a bare token is genuinely ambiguous there. This draw
   * found that on the twenty sixth try, which is why the qualifying printer
   * exists and is what the round trip is asserted over.
   */
  it('round trips every complete bundle through the unambiguous notation', () => {
    forEachDraw(60, 4409, (random) => {
      const schema = random.pick(schemas);
      const bundle = random.pick(enumerateBundles(schema));
      const printed = formatUnambiguous(schema, bundle);
      expect(bundleKey(parseBundleNotation(schema, printed))).toBe(bundleKey(bundle));
    });
  });

  it('prints a plain notation that reads back only when nothing is ambiguous', () => {
    const bundle = enumerateBundles(latinNounSchema)[3]!;
    const printed = formatBundle(latinNounSchema, bundle, 'key');
    expect(bundleKey(parseBundleNotation(latinNounSchema, printed))).toBe(bundleKey(bundle));
    expect(ambiguousTokens(greekVerbSchema)).toContain('imp');
  });

  it('puts pairs into schema order however the input was ordered', () => {
    forEachDraw(60, 7717, (random) => {
      const schema = random.pick(schemas);
      const bundle = random.pick(enumerateBundles(schema));
      const input: Record<string, string> = {};
      for (const pair of random.shuffle(bundle.pairs)) {
        input[pair.dimension] = pair.value;
      }
      expect(bundleKey(featureBundle(schema, input))).toBe(bundleKey(bundle));
    });
  });

  it('is subsumed by anything it was built from by removing a pair', () => {
    forEachDraw(60, 2213, (random) => {
      const schema = random.pick(schemas);
      const bundle = random.pick(enumerateBundles(schema));
      const dimension = random.pick(bundle.pairs).dimension;
      expect(bundleSubsumes(withoutFeature(bundle, dimension), bundle)).toBe(true);
    });
  });

  it('carries a pair that was just added', () => {
    forEachDraw(60, 8831, (random) => {
      const schema = random.pick(schemas);
      const bundle = random.pick(enumerateBundles(schema));
      const dimension = random.pick(schema.dimensions);
      const value = random.pick(dimension.values).key;
      const grown = withFeature(schema, bundle, dimension.key, value);
      expect(bundleKey(grown)).toContain(`${dimension.key}=${value}`);
    });
  });

  it('orders bundles consistently in both directions', () => {
    forEachDraw(40, 6203, (random) => {
      const schema = random.pick(schemas);
      const bundles = enumerateBundles(schema);
      const first = random.pick(bundles);
      const second = random.pick(bundles);
      const sign = (value: number) => (value > 0 ? 1 : value < 0 ? -1 : 0);
      const forward = compareBundles(schema, first, second);
      const backward = compareBundles(schema, second, first);
      expect(sign(forward)).toBe(sign(-backward));
    });
  });

  it('enumerates exactly as many bundles as the cardinality says', () => {
    for (const schema of schemas) {
      expect(enumerateBundles(schema)).toHaveLength(schemaCardinality(schema));
    }
  });
});

describe('selector properties', () => {
  it('expands to exactly as many bundles as its cardinality', () => {
    forEachDraw(80, 3319, (random) => {
      const schema = random.pick(schemas);
      const selection = selectorFor(schema, random);
      expect(expandSelector(schema, selection)).toHaveLength(
        selectorCardinality(schema, selection),
      );
    });
  });

  it('matches every bundle it expanded to', () => {
    forEachDraw(60, 5501, (random) => {
      const schema = random.pick(schemas);
      const selection = selectorFor(schema, random);
      for (const bundle of expandSelector(schema, selection)) {
        expect(matchesSelector(selection, bundle)).toBe(true);
      }
    });
  });

  it('round trips through its written form', () => {
    forEachDraw(60, 9137, (random) => {
      const schema = random.pick(schemas);
      const selection = selectorFor(schema, random);
      const printed = formatSelector(selection);
      expect(formatSelector(parseSelector(schema, printed))).toBe(printed);
    });
  });
});
