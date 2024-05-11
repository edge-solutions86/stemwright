import { ValidationError } from '../errors.js';
import { normaliseInput } from '../text/normalise.js';
import type { FeatureBundle } from './bundle.js';
import { featureBundle, valueOf } from './bundle.js';
import type { FeatureSchema } from './schema.js';
import { dimensionOf } from './schema.js';

/**
 * The short notation a teacher writes on a board: `gen.pl`, `aor.subj.mid.2sg`.
 * It is what a deck definition, a cli argument and an import file all use, so it
 * has to round trip and it has to refuse anything ambiguous rather than guess.
 *
 * A token is looked up across every dimension. If exactly one dimension admits
 * it the token is unqualified and fine. If two do, the writer has to say which,
 * as `case:nom`. This is not a nicety: Greek `pres` is a tense and `pass` is a
 * voice, but `perf` is a tense in one schema and an aspect in another, and a
 * silent pick would put the cell in the wrong row of the table.
 */
export type BundleStyle = 'abbreviation' | 'key' | 'label';

export function formatBundle(
  schema: FeatureSchema,
  bundle: FeatureBundle,
  style: BundleStyle = 'abbreviation',
): string {
  const parts: string[] = [];
  for (const pair of bundle.pairs) {
    const dimension = dimensionOf(schema, pair.dimension);
    const value = dimension?.values.find((candidate) => candidate.key === pair.value);
    if (dimension === undefined || value === undefined) {
      throw ValidationError.field(
        'bundle',
        `holds ${pair.dimension}=${pair.value} which the schema lost`,
      );
    }
    parts.push(
      style === 'abbreviation' ? value.abbreviation : style === 'key' ? value.key : value.label,
    );
  }
  return style === 'label' ? parts.join(' ') : parts.join('.');
}

interface TokenMatch {
  readonly dimension: string;
  readonly value: string;
}

function matchesForToken(schema: FeatureSchema, token: string): TokenMatch[] {
  const matches: TokenMatch[] = [];
  for (const dimension of schema.dimensions) {
    for (const value of dimension.values) {
      if (value.key === token || value.abbreviation === token) {
        matches.push({ dimension: dimension.key, value: value.key });
      }
    }
  }
  return matches;
}

function resolveQualified(schema: FeatureSchema, token: string, path: string): TokenMatch {
  const colon = token.indexOf(':');
  const dimensionKey = token.slice(0, colon);
  const valueToken = token.slice(colon + 1);
  const dimension = dimensionOf(schema, dimensionKey);
  if (dimension === undefined) {
    throw ValidationError.field(
      path,
      `names a dimension the schema does not have: ${dimensionKey}`,
    );
  }
  const value = dimension.values.find(
    (candidate) => candidate.key === valueToken || candidate.abbreviation === valueToken,
  );
  if (value === undefined) {
    throw ValidationError.field(path, `${dimensionKey} does not admit ${valueToken}`);
  }
  return { dimension: dimension.key, value: value.key };
}

export function parseBundleNotation(
  schema: FeatureSchema,
  notation: string,
  path = 'bundle',
): FeatureBundle {
  const cleaned = normaliseInput(notation).toLowerCase();
  if (cleaned === '') {
    return featureBundle(schema, {});
  }

  const input: Record<string, string> = {};
  for (const token of cleaned.split('.')) {
    if (token === '') {
      throw ValidationError.field(path, `has an empty token: ${notation}`);
    }
    const match = token.includes(':')
      ? resolveQualified(schema, token, path)
      : resolveBare(schema, token, path);
    if (match.dimension in input) {
      throw ValidationError.field(path, `fixes ${match.dimension} twice`);
    }
    input[match.dimension] = match.value;
  }
  return featureBundle(schema, input);
}

function resolveBare(schema: FeatureSchema, token: string, path: string): TokenMatch {
  const matches = matchesForToken(schema, token);
  if (matches.length === 0) {
    throw ValidationError.field(path, `is not a feature value in ${schema.language}: ${token}`);
  }
  if (matches.length > 1) {
    const named = matches.map((match) => match.dimension).join(', ');
    throw ValidationError.field(
      path,
      `is ambiguous between ${named}, write it as one of them followed by a colon: ${token}`,
    );
  }
  return matches[0]!;
}

/** True when the schema can express a token in more than one dimension. */
export function isAmbiguousToken(schema: FeatureSchema, token: string): boolean {
  return matchesForToken(schema, token).length > 1;
}

export function ambiguousTokens(schema: FeatureSchema): string[] {
  const counts = new Map<string, number>();
  for (const dimension of schema.dimensions) {
    for (const value of dimension.values) {
      for (const token of new Set([value.key, value.abbreviation])) {
        counts.set(token, (counts.get(token) ?? 0) + 1);
      }
    }
  }
  return [...counts.entries()]
    .filter(([, count]) => count > 1)
    .map(([token]) => token)
    .sort();
}

/**
 * Prints a notation that will parse back to the same bundle even when some of
 * its tokens are ambiguous, by qualifying exactly those tokens and no others.
 */
export function formatUnambiguous(schema: FeatureSchema, bundle: FeatureBundle): string {
  const ambiguous = new Set(ambiguousTokens(schema));
  const parts: string[] = [];
  for (const pair of bundle.pairs) {
    const dimension = dimensionOf(schema, pair.dimension);
    const value = dimension?.values.find((candidate) => candidate.key === pair.value);
    if (value === undefined) {
      throw ValidationError.field('bundle', `holds a value the schema lost: ${pair.value}`);
    }
    const token = value.abbreviation;
    parts.push(ambiguous.has(token) ? `${pair.dimension}:${token}` : token);
  }
  return parts.join('.');
}

export function describeBundle(schema: FeatureSchema, bundle: FeatureBundle): string {
  const described = schema.dimensions
    .map((dimension) => {
      const value = valueOf(bundle, dimension.key);
      if (value === undefined) {
        return undefined;
      }
      const label = dimension.values.find((candidate) => candidate.key === value)?.label ?? value;
      return `${dimension.label.toLowerCase()} ${label.toLowerCase()}`;
    })
    .filter((part): part is string => part !== undefined);
  return described.length === 0 ? 'no features fixed' : described.join(', ');
}
