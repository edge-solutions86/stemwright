import { describe, expect, it } from 'vitest';
import {
  dimensionIndex,
  dimensionKeys,
  dimensionOf,
  featureSchema,
  hasValue,
  requireDimension,
  requiredDimensions,
  schemaCardinality,
  valueIndex,
} from '../../src/domain/features/schema.js';
import { ValidationError } from '../../src/domain/errors.js';

const latin = featureSchema({
  language: 'latin',
  dimensions: [
    { key: 'case', values: ['nom', 'gen', 'dat', 'acc', 'abl', 'voc'] },
    { key: 'number', values: ['sg', 'pl'] },
    { key: 'gender', values: ['masc', 'fem', 'neut'], optional: true },
  ],
});

describe('featureSchema', () => {
  it('keeps the dimensions in the order they were declared', () => {
    expect(dimensionKeys(latin)).toEqual(['case', 'number', 'gender']);
  });

  it('lower cases the language key', () => {
    const renamed = featureSchema({ language: 'Ancient-Greek', dimensions: latin.dimensions });
    expect(renamed.language).toBe('ancient-greek');
  });

  it('defaults a label from the key and an abbreviation from the first letters', () => {
    const dimension = requireDimension(latin, 'case');
    expect(dimension.label).toBe('Case');
    expect(dimension.values[0]).toEqual({ key: 'nom', label: 'Nom', abbreviation: 'nom' });
  });

  it('takes explicit labels and abbreviations when given', () => {
    const schema = featureSchema({
      language: 'greek',
      dimensions: [
        {
          key: 'voice',
          label: 'Diathesis',
          values: [{ key: 'mid-pass', label: 'Middle passive', abbreviation: 'mp' }],
        },
      ],
    });
    expect(schema.dimensions[0]!.label).toBe('Diathesis');
    expect(schema.dimensions[0]!.values[0]!.abbreviation).toBe('mp');
  });

  it('marks optional dimensions and leaves the rest required', () => {
    expect(requiredDimensions(latin).map((d) => d.key)).toEqual(['case', 'number']);
    expect(requireDimension(latin, 'gender').optional).toBe(true);
  });

  it('refuses a schema with no dimensions', () => {
    expect(() => featureSchema({ language: 'latin', dimensions: [] })).toThrow(ValidationError);
  });

  it('refuses a dimension with no values', () => {
    try {
      featureSchema({ language: 'latin', dimensions: [{ key: 'case', values: [] }] });
      expect.unreachable('the schema should not have built');
    } catch (error) {
      const problem = error as ValidationError;
      expect(problem.message).toBe('feature schema for latin is not usable');
      expect(problem.details).toEqual([
        { path: 'dimensions.case.values', message: 'must admit at least one value' },
      ]);
    }
  });

  it('collects every problem in one throw', () => {
    try {
      featureSchema({
        language: 'latin',
        dimensions: [
          { key: 'case', values: ['nom', 'nom'] },
          { key: 'case', values: ['gen'] },
        ],
      });
      expect.unreachable('the schema should not have built');
    } catch (error) {
      const problem = error as ValidationError;
      expect(problem.details.map((d) => d.path)).toEqual([
        'dimensions.case.values.nom',
        'dimensions.case',
      ]);
    }
  });

  it('refuses a key that is not a slug', () => {
    expect(() =>
      featureSchema({ language: 'latin', dimensions: [{ key: 'Case Marking', values: ['nom'] }] }),
    ).toThrow(ValidationError);
  });
});

describe('lookups', () => {
  it('finds a dimension and reports a missing one', () => {
    expect(dimensionOf(latin, 'number')?.values).toHaveLength(2);
    expect(dimensionOf(latin, 'tense')).toBeUndefined();
    expect(() => requireDimension(latin, 'tense')).toThrow(/not in the latin schema/);
  });

  it('answers whether a value belongs to a dimension', () => {
    expect(hasValue(latin, 'case', 'abl')).toBe(true);
    expect(hasValue(latin, 'case', 'inst')).toBe(false);
    expect(hasValue(latin, 'tense', 'pres')).toBe(false);
  });

  it('reports the declared position of a value', () => {
    expect(valueIndex(latin, 'case', 'nom')).toBe(0);
    expect(valueIndex(latin, 'case', 'voc')).toBe(5);
    expect(valueIndex(latin, 'case', 'inst')).toBe(-1);
  });

  it('reports the declared position of a dimension', () => {
    expect(dimensionIndex(latin, 'case')).toBe(0);
    expect(dimensionIndex(latin, 'gender')).toBe(2);
    expect(dimensionIndex(latin, 'mood')).toBe(-1);
  });
});

describe('schemaCardinality', () => {
  it('multiplies out every dimension', () => {
    expect(schemaCardinality(latin)).toBe(6 * 2 * 3);
  });

  it('is the value count for a single dimension schema', () => {
    const schema = featureSchema({
      language: 'x',
      dimensions: [{ key: 'number', values: ['sg', 'pl'] }],
    });
    expect(schemaCardinality(schema)).toBe(2);
  });
});
