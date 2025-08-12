import { describe, expect, it } from 'vitest';
import { ValidationError } from '../../src/domain/errors.js';
import {
  dayFrom,
  flagFrom,
  positiveFrom,
  rangeFrom,
  requireCourseKey,
  requireDay,
} from '../../src/http/query.js';

describe('dayFrom', () => {
  it('reads a day', () => {
    expect(dayFrom({ on: '2025-03-03' })).toBe('2025-03-03');
  });

  it('gives nothing for a missing or empty value', () => {
    expect(dayFrom({})).toBeUndefined();
    expect(dayFrom({ on: '' })).toBeUndefined();
  });

  it('refuses a day that is not real', () => {
    expect(() => dayFrom({ on: '2025-02-30' })).toThrow(ValidationError);
  });

  it('reads the name it was given', () => {
    expect(dayFrom({ since: '2025-03-03' }, 'since')).toBe('2025-03-03');
  });
});

describe('requireDay', () => {
  it('reads a day and refuses a missing one', () => {
    expect(requireDay({ on: '2025-03-03' })).toBe('2025-03-03');
    expect(() => requireDay({})).toThrow(/on must be given/);
  });
});

describe('requireCourseKey', () => {
  it('reads the course', () => {
    expect(requireCourseKey({ course: 'latin-1a' })).toBe('latin-1a');
  });

  it('refuses a missing or empty course with one message', () => {
    expect(() => requireCourseKey({})).toThrow(/course must be given so permissions/);
    expect(() => requireCourseKey({ course: '' })).toThrow(/course must be given so permissions/);
  });
});

describe('rangeFrom', () => {
  it('reads both ends', () => {
    expect(rangeFrom({ from: '2025-03-03', to: '2025-03-09' })).toEqual({
      from: '2025-03-03',
      to: '2025-03-09',
    });
  });

  it('refuses a window with only one end', () => {
    expect(() => rangeFrom({ from: '2025-03-03' })).toThrow(/needs both a from and a to day/);
    expect(() => rangeFrom({})).toThrow(ValidationError);
  });

  it('refuses a window that ends before it starts', () => {
    expect(() => rangeFrom({ from: '2025-03-09', to: '2025-03-03' })).toThrow(ValidationError);
  });
});

describe('flagFrom', () => {
  it('is true for a bare flag, true and one', () => {
    expect(flagFrom({ verbose: '' }, 'verbose')).toBe(true);
    expect(flagFrom({ verbose: 'true' }, 'verbose')).toBe(true);
    expect(flagFrom({ verbose: '1' }, 'verbose')).toBe(true);
  });

  it('is false when absent or set to anything else', () => {
    expect(flagFrom({}, 'verbose')).toBe(false);
    expect(flagFrom({ verbose: 'false' }, 'verbose')).toBe(false);
    expect(flagFrom({ verbose: 'no' }, 'verbose')).toBe(false);
  });
});

describe('positiveFrom', () => {
  it('reads a whole number above zero', () => {
    expect(positiveFrom({ limit: '5' }, 'limit')).toBe(5);
  });

  it('gives nothing when absent or empty', () => {
    expect(positiveFrom({}, 'limit')).toBeUndefined();
    expect(positiveFrom({ limit: '' }, 'limit')).toBeUndefined();
  });

  it('refuses zero, a negative and a fraction', () => {
    expect(() => positiveFrom({ limit: '0' }, 'limit')).toThrow(/above zero/);
    expect(() => positiveFrom({ limit: '-1' }, 'limit')).toThrow(ValidationError);
    expect(() => positiveFrom({ limit: '1.5' }, 'limit')).toThrow(ValidationError);
  });

  it('refuses something that is not a number at all', () => {
    expect(() => positiveFrom({ limit: 'lots' }, 'limit')).toThrow(ValidationError);
  });
});
