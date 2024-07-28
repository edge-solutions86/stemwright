import { ProblemCollector, ValidationError } from '../errors.js';
import type { FeatureSchema } from './schema.js';
import { dimensionIndex, dimensionOf, hasValue, requiredDimensions, valueIndex } from './schema.js';

/**
 * A feature bundle is one point in the space a schema describes: nominative,
 * singular, masculine. It is stored as an ordered list of pairs rather than a
 * plain object so the order is the schema order rather than whatever order the
 * caller happened to type, which is what makes the canonical key stable.
 */
export interface FeaturePair {
  readonly dimension: string;
  readonly value: string;
}

export interface FeatureBundle {
  readonly pairs: readonly FeaturePair[];
}

export type FeatureInput = Readonly<Record<string, string>>;

/** The bundle that fixes nothing. Shared because it is immutable and common. */
export const EMPTY_BUNDLE: FeatureBundle = { pairs: [] };

export function featureBundle(schema: FeatureSchema, input: FeatureInput): FeatureBundle {
  const problems = new ProblemCollector();
  const pairs: FeaturePair[] = [];

  for (const [dimension, value] of Object.entries(input)) {
    if (dimensionOf(schema, dimension) === undefined) {
      problems.add(dimension, `is not a dimension of ${schema.language}`);
      continue;
    }
    if (!hasValue(schema, dimension, value)) {
      problems.add(dimension, `does not admit the value ${value}`);
      continue;
    }
    pairs.push({ dimension, value });
  }

  problems.throwIfAny(`bundle is not valid for ${schema.language}`);
  pairs.sort((a, b) => dimensionIndex(schema, a.dimension) - dimensionIndex(schema, b.dimension));
  return { pairs };
}

/** Builds without checking. Only the codec uses this, on data it already wrote. */
export function unsafeBundle(pairs: readonly FeaturePair[]): FeatureBundle {
  return { pairs: [...pairs] };
}

export function valueOf(bundle: FeatureBundle, dimension: string): string | undefined {
  return bundle.pairs.find((pair) => pair.dimension === dimension)?.value;
}

export function requireValue(bundle: FeatureBundle, dimension: string): string {
  const value = valueOf(bundle, dimension);
  if (value === undefined) {
    throw ValidationError.field('bundle', `has no value for ${dimension}`);
  }
  return value;
}

export function dimensionsOf(bundle: FeatureBundle): string[] {
  return bundle.pairs.map((pair) => pair.dimension);
}

export function bundleSize(bundle: FeatureBundle): number {
  return bundle.pairs.length;
}

/**
 * The canonical key. Two bundles that mean the same thing produce the same
 * string, which is what a cell id is derived from and what a syncretism map is
 * keyed on. Never change this format without a journal migration.
 */
export function bundleKey(bundle: FeatureBundle): string {
  return bundle.pairs.map((pair) => `${pair.dimension}=${pair.value}`).join(';');
}

export function parseBundleKey(schema: FeatureSchema, key: string): FeatureBundle {
  if (key === '') {
    return { pairs: [] };
  }
  const input: Record<string, string> = {};
  for (const part of key.split(';')) {
    const equals = part.indexOf('=');
    if (equals <= 0) {
      throw ValidationError.field('bundleKey', `is not a dimension value pair: ${part}`);
    }
    const dimension = part.slice(0, equals);
    if (dimension in input) {
      throw ValidationError.field('bundleKey', `names ${dimension} twice`);
    }
    input[dimension] = part.slice(equals + 1);
  }
  return featureBundle(schema, input);
}

export function bundleEquals(a: FeatureBundle, b: FeatureBundle): boolean {
  return bundleKey(a) === bundleKey(b);
}

/** True when every pair in the smaller bundle appears in the larger one. */
export function bundleSubsumes(broader: FeatureBundle, narrower: FeatureBundle): boolean {
  return broader.pairs.every((pair) => valueOf(narrower, pair.dimension) === pair.value);
}

export function withFeature(
  schema: FeatureSchema,
  bundle: FeatureBundle,
  dimension: string,
  value: string,
): FeatureBundle {
  const input: Record<string, string> = {};
  for (const pair of bundle.pairs) {
    input[pair.dimension] = pair.value;
  }
  input[dimension] = value;
  return featureBundle(schema, input);
}

export function withoutFeature(bundle: FeatureBundle, dimension: string): FeatureBundle {
  return { pairs: bundle.pairs.filter((pair) => pair.dimension !== dimension) };
}

/** A bundle is complete when it fixes every dimension the schema requires. */
export function isComplete(schema: FeatureSchema, bundle: FeatureBundle): boolean {
  return requiredDimensions(schema).every(
    (dimension) => valueOf(bundle, dimension.key) !== undefined,
  );
}

export function missingDimensions(schema: FeatureSchema, bundle: FeatureBundle): string[] {
  return requiredDimensions(schema)
    .filter((dimension) => valueOf(bundle, dimension.key) === undefined)
    .map((dimension) => dimension.key);
}

/**
 * Orders bundles the way a paradigm table reads: by the first dimension the
 * schema declares, then the next, with a missing value sorting last.
 */
export function compareBundles(schema: FeatureSchema, a: FeatureBundle, b: FeatureBundle): number {
  for (const dimension of schema.dimensions) {
    const left = valueOf(a, dimension.key);
    const right = valueOf(b, dimension.key);
    if (left === right) {
      continue;
    }
    if (left === undefined) {
      return 1;
    }
    if (right === undefined) {
      return -1;
    }
    return valueIndex(schema, dimension.key, left) - valueIndex(schema, dimension.key, right);
  }
  return 0;
}

/** Every complete bundle the schema can express, in table order. */
export function enumerateBundles(schema: FeatureSchema): FeatureBundle[] {
  let bundles: FeatureBundle[] = [{ pairs: [] }];
  for (const dimension of schema.dimensions) {
    const grown: FeatureBundle[] = [];
    for (const bundle of bundles) {
      for (const value of dimension.values) {
        grown.push({ pairs: [...bundle.pairs, { dimension: dimension.key, value: value.key }] });
      }
    }
    bundles = grown;
  }
  return bundles;
}
