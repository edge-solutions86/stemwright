import { ProblemCollector, ValidationError } from '../errors.js';
import { asSlug } from '../identifiers.js';
import { requireText } from '../text/normalise.js';

/**
 * A feature schema is the grammar of one language as this desk models it. It
 * fixes which dimensions exist, which values each admits and in what order they
 * are conventionally printed, because a paradigm table read out of order is a
 * different table to a learner even though it holds the same cells.
 *
 * Schemas are per language and never global. Latin has five cases and no dual,
 * Greek has five cases and a dual, and no code outside a schema is allowed to
 * assume either.
 */
export interface FeatureValue {
  readonly key: string;
  readonly label: string;
  /** The short form printed in a table header, such as gen for genitive. */
  readonly abbreviation: string;
}

export interface FeatureDimension {
  readonly key: string;
  readonly label: string;
  readonly values: readonly FeatureValue[];
  /**
   * A dimension a lexeme may leave out. Gender is inherent to a Latin noun and
   * chosen for a Latin adjective, so a noun paradigm omits it entirely.
   */
  readonly optional: boolean;
}

export interface FeatureSchema {
  readonly language: string;
  readonly dimensions: readonly FeatureDimension[];
}

export interface FeatureValueInput {
  readonly key: string;
  readonly label?: string;
  readonly abbreviation?: string;
}

export interface FeatureDimensionInput {
  readonly key: string;
  readonly label?: string;
  readonly values: readonly (string | FeatureValueInput)[];
  readonly optional?: boolean;
}

export interface FeatureSchemaInput {
  readonly language: string;
  readonly dimensions: readonly FeatureDimensionInput[];
}

function titleCase(key: string): string {
  return key
    .split(/[-.]/)
    .map((part) => (part.length === 0 ? part : part[0]!.toUpperCase() + part.slice(1)))
    .join(' ');
}

function buildValue(dimensionKey: string, input: string | FeatureValueInput): FeatureValue {
  const raw = typeof input === 'string' ? { key: input } : input;
  const key = asSlug(`${dimensionKey}.value`, raw.key);
  const label =
    raw.label === undefined ? titleCase(key) : requireText(`${key}.label`, raw.label, 64);
  const abbreviation =
    raw.abbreviation === undefined
      ? key.slice(0, 3)
      : requireText(`${key}.abbreviation`, raw.abbreviation, 8);
  return { key, label, abbreviation };
}

export function featureSchema(input: FeatureSchemaInput): FeatureSchema {
  const language = asSlug('language', input.language);
  const problems = new ProblemCollector();
  if (input.dimensions.length === 0) {
    problems.add('dimensions', 'must name at least one dimension');
  }

  const dimensions: FeatureDimension[] = [];
  const seenDimensions = new Set<string>();
  for (const raw of input.dimensions) {
    const key = asSlug('dimension', raw.key);
    if (seenDimensions.has(key)) {
      problems.add(`dimensions.${key}`, 'is declared twice');
      continue;
    }
    seenDimensions.add(key);

    const values = raw.values.map((value) => buildValue(key, value));
    if (values.length === 0) {
      problems.add(`dimensions.${key}.values`, 'must admit at least one value');
    }
    const seenValues = new Set<string>();
    for (const value of values) {
      if (seenValues.has(value.key)) {
        problems.add(`dimensions.${key}.values.${value.key}`, 'is declared twice');
      }
      seenValues.add(value.key);
    }

    dimensions.push({
      key,
      label: raw.label === undefined ? titleCase(key) : requireText(`${key}.label`, raw.label, 64),
      values,
      optional: raw.optional ?? false,
    });
  }

  problems.throwIfAny(`feature schema for ${language} is not usable`);
  return { language, dimensions };
}

export function dimensionKeys(schema: FeatureSchema): string[] {
  return schema.dimensions.map((dimension) => dimension.key);
}

export function dimensionOf(schema: FeatureSchema, key: string): FeatureDimension | undefined {
  return schema.dimensions.find((dimension) => dimension.key === key);
}

export function requireDimension(schema: FeatureSchema, key: string): FeatureDimension {
  const dimension = dimensionOf(schema, key);
  if (dimension === undefined) {
    throw ValidationError.field('dimension', `is not in the ${schema.language} schema: ${key}`);
  }
  return dimension;
}

export function hasValue(schema: FeatureSchema, dimensionKey: string, valueKey: string): boolean {
  const dimension = dimensionOf(schema, dimensionKey);
  return dimension !== undefined && dimension.values.some((value) => value.key === valueKey);
}

/** Where a value sits in its dimension, which is how a table orders its rows. */
export function valueIndex(schema: FeatureSchema, dimensionKey: string, valueKey: string): number {
  const dimension = requireDimension(schema, dimensionKey);
  return dimension.values.findIndex((value) => value.key === valueKey);
}

export function dimensionIndex(schema: FeatureSchema, dimensionKey: string): number {
  return schema.dimensions.findIndex((dimension) => dimension.key === dimensionKey);
}

/** How many bundles the schema can express if every dimension is filled in. */
export function schemaCardinality(schema: FeatureSchema): number {
  return schema.dimensions.reduce((total, dimension) => total * dimension.values.length, 1);
}

export function requiredDimensions(schema: FeatureSchema): FeatureDimension[] {
  return schema.dimensions.filter((dimension) => !dimension.optional);
}
