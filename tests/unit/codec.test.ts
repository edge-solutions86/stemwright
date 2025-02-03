import { describe, expect, it } from 'vitest';
import {
  decodeBundle,
  decodeRange,
  decodeSelectorClauses,
  encodeBundle,
  encodeRange,
  encodeSelector,
  encodeStems,
  isObject,
  readArray,
  readBoolean,
  readDate,
  readNumber,
  readOptionalDate,
  readOptionalNumber,
  readOptionalString,
  readString,
  readStringArray,
  requireObject,
} from '../../src/infra/codec.js';
import { bundleKey, featureBundle } from '../../src/domain/features/bundle.js';
import { formatSelector, parseSelector } from '../../src/domain/features/selector.js';
import { stemSet } from '../../src/domain/lexicon/stem.js';
import { ValidationError } from '../../src/domain/errors.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { dayRange } from '../../src/domain/time/window.js';
import { latinNoun } from '../support/profiles.js';

const d = (value: string) => parseCivilDate(value);
const row = {
  name: 'puella',
  count: 3,
  flag: true,
  when: '2025-03-03',
  tags: ['core', 'chapter-one'],
  nothing: null,
};

describe('isObject and requireObject', () => {
  it('recognises a plain object and nothing else', () => {
    expect(isObject({})).toBe(true);
    expect(isObject([])).toBe(false);
    expect(isObject(null)).toBe(false);
    expect(isObject('x')).toBe(false);
  });

  it('names the path when it refuses', () => {
    expect(() => requireObject('lexeme', [])).toThrow(/lexeme is not an object/);
    expect(requireObject('lexeme', row)).toBe(row);
  });
});

describe('readString', () => {
  it('reads a string', () => {
    expect(readString(row, 'lexeme', 'name')).toBe('puella');
  });

  it('refuses a missing or wrongly typed field', () => {
    expect(() => readString(row, 'lexeme', 'count')).toThrow(/lexeme.count is not a string/);
    expect(() => readString(row, 'lexeme', 'absent')).toThrow(ValidationError);
  });

  it('reads an optional string as undefined when absent or null', () => {
    expect(readOptionalString(row, 'lexeme', 'absent')).toBeUndefined();
    expect(readOptionalString(row, 'lexeme', 'nothing')).toBeUndefined();
    expect(readOptionalString(row, 'lexeme', 'name')).toBe('puella');
  });

  it('still refuses an optional field of the wrong type', () => {
    expect(() => readOptionalString(row, 'lexeme', 'count')).toThrow(ValidationError);
  });
});

describe('readNumber and readBoolean', () => {
  it('reads a finite number', () => {
    expect(readNumber(row, 'lexeme', 'count')).toBe(3);
  });

  it('refuses a string, a missing field and an infinity', () => {
    expect(() => readNumber(row, 'lexeme', 'name')).toThrow(/is not a finite number/);
    expect(() => readNumber({ x: Number.POSITIVE_INFINITY }, 'lexeme', 'x')).toThrow(
      ValidationError,
    );
  });

  it('reads an optional number', () => {
    expect(readOptionalNumber(row, 'lexeme', 'absent')).toBeUndefined();
    expect(readOptionalNumber(row, 'lexeme', 'count')).toBe(3);
  });

  it('reads a boolean and refuses anything else', () => {
    expect(readBoolean(row, 'lexeme', 'flag')).toBe(true);
    expect(() => readBoolean(row, 'lexeme', 'count')).toThrow(/is not a boolean/);
  });
});

describe('readArray and readStringArray', () => {
  it('reads an array', () => {
    expect(readArray(row, 'lexeme', 'tags')).toHaveLength(2);
    expect(() => readArray(row, 'lexeme', 'name')).toThrow(/is not an array/);
  });

  it('reads an array of strings and names the bad index', () => {
    expect(readStringArray(row, 'lexeme', 'tags')).toEqual(['core', 'chapter-one']);
    expect(() => readStringArray({ tags: ['a', 2] }, 'lexeme', 'tags')).toThrow(
      /lexeme.tags.1 is not a string/,
    );
  });
});

describe('readDate', () => {
  it('parses a stored day', () => {
    expect(readDate(row, 'lexeme', 'when')).toBe('2025-03-03');
  });

  it('refuses a day that is not real', () => {
    expect(() => readDate({ when: '2025-02-30' }, 'lexeme', 'when')).toThrow(ValidationError);
  });

  it('reads an optional day', () => {
    expect(readOptionalDate(row, 'lexeme', 'absent')).toBeUndefined();
    expect(readOptionalDate(row, 'lexeme', 'when')).toBe('2025-03-03');
  });
});

describe('ranges', () => {
  it('round trips', () => {
    const range = dayRange(d('2025-03-03'), d('2025-03-09'));
    expect(decodeRange(encodeRange(range), 'window')).toEqual(range);
  });

  it('refuses a range that ends before it starts', () => {
    expect(() => decodeRange({ from: '2025-03-09', to: '2025-03-03' }, 'window')).toThrow(
      ValidationError,
    );
  });

  it('refuses something that is not an object', () => {
    expect(() => decodeRange('2025-03-03', 'window')).toThrow(/window is not an object/);
  });
});

describe('bundles', () => {
  it('stores a bundle as its canonical key', () => {
    const bundle = featureBundle(latinNoun, { case: 'gen', number: 'pl' });
    expect(encodeBundle(bundle)).toBe('case=gen;number=pl');
  });

  it('round trips through the key', () => {
    const bundle = featureBundle(latinNoun, { case: 'gen', number: 'pl' });
    expect(bundleKey(decodeBundle(encodeBundle(bundle), 'bundle'))).toBe(bundleKey(bundle));
  });

  it('round trips the empty bundle', () => {
    expect(decodeBundle('', 'bundle').pairs).toEqual([]);
  });

  it('refuses a malformed key', () => {
    expect(() => decodeBundle('casegen', 'bundle')).toThrow(/dimension value pair/);
    expect(() => decodeBundle(42, 'bundle')).toThrow(/is not a bundle key/);
  });
});

describe('stems', () => {
  it('encodes every stem with its role and flag', () => {
    const stems = stemSet([
      { role: 'base', form: 'fer' },
      { role: 'perfect', form: 'tul', suppletive: true, note: 'unrelated root' },
    ]);
    expect(encodeStems(stems)).toEqual([
      { role: 'base', form: 'fer', suppletive: false },
      { role: 'perfect', form: 'tul', suppletive: true, note: 'unrelated root' },
    ]);
  });
});

describe('selectors', () => {
  it('round trips through clauses', () => {
    const selection = parseSelector(latinNoun, 'case=nom|gen,number!=pl');
    const decoded = decodeSelectorClauses(encodeSelector(selection), 'cells');
    expect(decoded).toHaveLength(2);
    expect(formatSelector(parseSelector(latinNoun, formatSelector(selection)))).toBe(
      formatSelector(selection),
    );
  });

  it('encodes the empty selector as an empty list', () => {
    expect(encodeSelector(parseSelector(latinNoun, ''))).toEqual([]);
  });

  it('refuses a clause list that is not an array', () => {
    expect(() => decodeSelectorClauses('nope', 'cells')).toThrow(/is not an array of clauses/);
  });

  it('names the bad clause', () => {
    expect(() => decodeSelectorClauses([{ dimension: 'case' }], 'cells')).toThrow(
      /cells.0.values is not an array/,
    );
  });
});
