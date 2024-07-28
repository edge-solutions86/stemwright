import { describe, expect, it } from 'vitest';
import {
  ConflictError,
  ForbiddenError,
  IntegrityError,
  NotFoundError,
  PolicyError,
  ProblemCollector,
  StemwrightError,
  UnsupportedError,
  ValidationError,
  errorCodeOf,
  isStemwrightError,
} from '../../src/domain/errors.js';

describe('StemwrightError', () => {
  it('keeps the code it was built with', () => {
    const error = new StemwrightError('VALIDATION', 'bad input');
    expect(error.code).toBe('VALIDATION');
    expect(error.message).toBe('bad input');
  });

  it('names itself after the concrete subclass', () => {
    expect(new ValidationError('x').name).toBe('ValidationError');
    expect(new ConflictError('x').name).toBe('ConflictError');
    expect(new StemwrightError('POLICY', 'x').name).toBe('StemwrightError');
  });

  it('defaults details to an empty list rather than undefined', () => {
    expect(new ValidationError('x').details).toEqual([]);
  });

  it('carries a cause when one is given', () => {
    const cause = new Error('underlying');
    const error = new IntegrityError('journal is torn', { cause });
    expect(error.cause).toBe(cause);
  });

  it('leaves cause absent when none is given', () => {
    expect('cause' in new IntegrityError('torn')).toBe(false);
  });

  it('serialises to a wire safe shape', () => {
    const error = new ValidationError('two problems', {
      details: [
        { path: 'lemma', message: 'is empty' },
        { path: 'class', message: 'is unknown' },
      ],
    });
    expect(error.toJSON()).toEqual({
      code: 'VALIDATION',
      message: 'two problems',
      details: [
        { path: 'lemma', message: 'is empty' },
        { path: 'class', message: 'is unknown' },
      ],
    });
  });
});

describe('ValidationError.field', () => {
  it('builds a single detail from the path and message', () => {
    const error = ValidationError.field('bundle.case', 'is not in the schema');
    expect(error.message).toBe('bundle.case is not in the schema');
    expect(error.details).toEqual([{ path: 'bundle.case', message: 'is not in the schema' }]);
  });
});

describe('NotFoundError', () => {
  it('reads the kind and the id back', () => {
    const error = new NotFoundError('lexeme', 'lex_7');
    expect(error.kind).toBe('lexeme');
    expect(error.id).toBe('lex_7');
    expect(error.message).toBe('lexeme lex_7 was not found');
    expect(error.code).toBe('NOT_FOUND');
  });
});

describe('PolicyError', () => {
  it('names the rule that refused', () => {
    const error = new PolicyError('daily.new.budget', 'no new cells left today');
    expect(error.rule).toBe('daily.new.budget');
    expect(error.code).toBe('POLICY');
  });
});

describe('error predicates', () => {
  it('recognises its own errors', () => {
    expect(isStemwrightError(new ForbiddenError('no'))).toBe(true);
    expect(isStemwrightError(new Error('no'))).toBe(false);
    expect(isStemwrightError(null)).toBe(false);
    expect(isStemwrightError('FORBIDDEN')).toBe(false);
  });

  it('reads a code off anything that has one', () => {
    expect(errorCodeOf(new UnsupportedError('later'))).toBe('UNSUPPORTED');
    expect(errorCodeOf(new TypeError('no'))).toBeUndefined();
  });
});

describe('ProblemCollector', () => {
  it('stays quiet when nothing was added', () => {
    const collector = new ProblemCollector();
    expect(collector.size).toBe(0);
    expect(() => collector.throwIfAny('bad row')).not.toThrow();
  });

  it('throws once with every problem it gathered', () => {
    const collector = new ProblemCollector();
    collector.add('lemma', 'is empty');
    collector.addIf(true, 'pos', 'is unknown');
    collector.addIf(false, 'gloss', 'never fires');

    expect(collector.size).toBe(2);
    try {
      collector.throwIfAny('row 4 is not usable');
      expect.unreachable('throwIfAny should have thrown');
    } catch (error) {
      expect(error).toBeInstanceOf(ValidationError);
      const problem = error as ValidationError;
      expect(problem.message).toBe('row 4 is not usable');
      expect(problem.details.map((d) => d.path)).toEqual(['lemma', 'pos']);
    }
  });

  it('hands back a copy so callers cannot grow the list', () => {
    const collector = new ProblemCollector();
    collector.add('lemma', 'is empty');
    const first = collector.problems() as ErrorDetailArray;
    first.push({ path: 'sneaky', message: 'added from outside' });
    expect(collector.size).toBe(1);
  });
});

type ErrorDetailArray = { path: string; message: string }[];

describe('ProblemCollector.absorb', () => {
  it('returns the value when nothing was thrown', () => {
    const collector = new ProblemCollector();
    expect(collector.absorb('inherent', () => 42)).toBe(42);
    expect(collector.size).toBe(0);
  });

  it('nests the details of a validation failure under the path', () => {
    const collector = new ProblemCollector();
    const result = collector.absorb('inherent', () => {
      throw new ValidationError('two problems', {
        details: [
          { path: 'case', message: 'is unknown' },
          { path: 'number', message: 'is unknown' },
        ],
      });
    });
    expect(result).toBeUndefined();
    expect(collector.problems()).toEqual([
      { path: 'inherent.case', message: 'is unknown' },
      { path: 'inherent.number', message: 'is unknown' },
    ]);
  });

  it('records one problem for a failure that is not a validation error', () => {
    const collector = new ProblemCollector();
    collector.absorb('inherent', () => {
      throw new TypeError('cannot read the schema');
    });
    expect(collector.problems()).toEqual([{ path: 'inherent', message: 'cannot read the schema' }]);
  });

  it('records a validation error carrying no details as one problem', () => {
    const collector = new ProblemCollector();
    collector.absorb('inherent', () => {
      throw new ValidationError('nothing more to say');
    });
    expect(collector.problems()).toEqual([{ path: 'inherent', message: 'nothing more to say' }]);
  });
});

describe('ProblemCollector.absorbFlat', () => {
  it('keeps the caller path and folds the detail into the message', () => {
    const collector = new ProblemCollector();
    collector.absorbFlat('slots.0', () => {
      throw ValidationError.field('bundle', 'is not a feature value');
    });
    expect(collector.problems()).toEqual([
      { path: 'slots.0', message: 'bundle is not a feature value' },
    ]);
  });

  it('returns the value when nothing was thrown', () => {
    const collector = new ProblemCollector();
    expect(collector.absorbFlat('slots.0', () => 'kept')).toBe('kept');
  });

  it('records one problem for anything else that was thrown', () => {
    const collector = new ProblemCollector();
    collector.absorbFlat('slots.0', () => {
      throw new RangeError('out of range');
    });
    expect(collector.problems()).toEqual([{ path: 'slots.0', message: 'out of range' }]);
  });
});
