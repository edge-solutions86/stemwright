import { ProblemCollector, ValidationError } from '../errors.js';
import { normaliseInput } from '../text/normalise.js';
import type { FeatureBundle } from './bundle.js';
import { enumerateBundles, valueOf } from './bundle.js';
import type { FeatureSchema } from './schema.js';
import { dimensionIndex, dimensionOf, hasValue } from './schema.js';

/**
 * A selector names a region of the paradigm rather than a single cell. Deck
 * membership, assignment scope and coverage reports are all stated with these,
 * because "the plural of every case" is what a syllabus actually says.
 *
 * Written form is comma separated clauses:
 *
 *     case=nom|gen,number=pl
 *     number=pl,gender!=neut
 *
 * A dimension the selector says nothing about is unconstrained, so an empty
 * selector matches everything. That is deliberate: a deck with no scope is the
 * whole paradigm, not an empty one.
 */
export interface SelectorClause {
  readonly dimension: string;
  readonly values: readonly string[];
  readonly negated: boolean;
}

export interface Selector {
  readonly clauses: readonly SelectorClause[];
}

export const EMPTY_SELECTOR: Selector = { clauses: [] };

export function selector(schema: FeatureSchema, clauses: readonly SelectorClause[]): Selector {
  const problems = new ProblemCollector();
  const seen = new Set<string>();
  for (const clause of clauses) {
    const key = `${clause.dimension}${clause.negated ? '!' : ''}`;
    if (dimensionOf(schema, clause.dimension) === undefined) {
      problems.add(clause.dimension, `is not a dimension of ${schema.language}`);
      continue;
    }
    if (seen.has(key)) {
      problems.add(clause.dimension, 'is constrained twice in the same direction');
    }
    seen.add(key);
    if (clause.values.length === 0) {
      problems.add(clause.dimension, 'must name at least one value');
    }
    for (const value of clause.values) {
      if (!hasValue(schema, clause.dimension, value)) {
        problems.add(clause.dimension, `does not admit the value ${value}`);
      }
    }
  }
  problems.throwIfAny(`selector is not valid for ${schema.language}`);

  const sorted = [...clauses].sort(
    (a, b) => dimensionIndex(schema, a.dimension) - dimensionIndex(schema, b.dimension),
  );
  return { clauses: sorted.map((clause) => ({ ...clause, values: [...clause.values].sort() })) };
}

export function parseSelector(schema: FeatureSchema, text: string, path = 'selector'): Selector {
  const cleaned = normaliseInput(text).toLowerCase();
  if (cleaned === '') {
    return EMPTY_SELECTOR;
  }
  const clauses: SelectorClause[] = [];
  for (const part of cleaned.split(',')) {
    const trimmed = part.trim();
    if (trimmed === '') {
      throw ValidationError.field(path, `has an empty clause: ${text}`);
    }
    const negated = trimmed.includes('!=');
    const separator = negated ? '!=' : '=';
    const at = trimmed.indexOf(separator);
    if (at <= 0) {
      throw ValidationError.field(path, `is not a dimension and values: ${trimmed}`);
    }
    const dimension = trimmed.slice(0, at).trim();
    const values = trimmed
      .slice(at + separator.length)
      .split('|')
      .map((value) => value.trim())
      .filter((value) => value !== '');
    clauses.push({ dimension, values, negated });
  }
  return selector(schema, clauses);
}

export function formatSelector(selection: Selector): string {
  return selection.clauses
    .map((clause) => `${clause.dimension}${clause.negated ? '!=' : '='}${clause.values.join('|')}`)
    .join(',');
}

/**
 * A clause a bundle says nothing about does not match. A selector asking for
 * `number=pl` is asking for cells that are plural, and a cell with no number at
 * all is not one of them.
 */
export function matchesSelector(selection: Selector, bundle: FeatureBundle): boolean {
  for (const clause of selection.clauses) {
    const value = valueOf(bundle, clause.dimension);
    if (value === undefined) {
      return false;
    }
    const listed = clause.values.includes(value);
    if (listed === clause.negated) {
      return false;
    }
  }
  return true;
}

export function selectBundles(
  selection: Selector,
  bundles: readonly FeatureBundle[],
): FeatureBundle[] {
  return bundles.filter((bundle) => matchesSelector(selection, bundle));
}

/** How many complete bundles of the schema the selector admits. */
export function selectorCardinality(schema: FeatureSchema, selection: Selector): number {
  return schema.dimensions.reduce((total, dimension) => {
    const clause = selection.clauses.find((candidate) => candidate.dimension === dimension.key);
    if (clause === undefined) {
      return total * dimension.values.length;
    }
    const allowed = dimension.values.filter((value) =>
      clause.negated ? !clause.values.includes(value.key) : clause.values.includes(value.key),
    );
    return total * allowed.length;
  }, 1);
}

export function isEmptySelection(schema: FeatureSchema, selection: Selector): boolean {
  return selectorCardinality(schema, selection) === 0;
}

/** Every complete bundle the selector admits, in table order. */
export function expandSelector(schema: FeatureSchema, selection: Selector): FeatureBundle[] {
  return selectBundles(selection, enumerateBundles(schema));
}

/**
 * The conjunction of two selectors. Used when an assignment narrows a deck: the
 * result is what both admit, and it can be empty, which callers must check.
 */
export function intersectSelectors(schema: FeatureSchema, a: Selector, b: Selector): Selector {
  const byKey = new Map<string, SelectorClause>();
  for (const clause of [...a.clauses, ...b.clauses]) {
    const key = `${clause.dimension}${clause.negated ? '!' : ''}`;
    const existing = byKey.get(key);
    if (existing === undefined) {
      byKey.set(key, clause);
      continue;
    }
    const merged = clause.negated
      ? [...new Set([...existing.values, ...clause.values])]
      : existing.values.filter((value) => clause.values.includes(value));
    byKey.set(key, { ...clause, values: merged });
  }

  const clauses = [...byKey.values()];
  const empty = clauses.some((clause) => !clause.negated && clause.values.length === 0);
  if (empty) {
    const dimension = clauses.find((clause) => clause.values.length === 0)!.dimension;
    return selector(schema, [{ dimension, values: allValuesOf(schema, dimension), negated: true }]);
  }
  return selector(schema, clauses);
}

function allValuesOf(schema: FeatureSchema, dimensionKey: string): string[] {
  return dimensionOf(schema, dimensionKey)?.values.map((value) => value.key) ?? [];
}

export function describeSelector(schema: FeatureSchema, selection: Selector): string {
  if (selection.clauses.length === 0) {
    return `every cell in ${schema.language}`;
  }
  return selection.clauses
    .map((clause) => {
      const joined = clause.values.join(' or ');
      return clause.negated
        ? `${clause.dimension} other than ${joined}`
        : `${clause.dimension} of ${joined}`;
    })
    .join(', ');
}
