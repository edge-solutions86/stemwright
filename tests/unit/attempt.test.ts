import { describe, expect, it } from 'vitest';
import {
  accuracyOf,
  attempt,
  attemptIdFor,
  compareAttempts,
  isMatchVerdict,
  medianElapsed,
  tally,
  wasBlank,
  wasCorrect,
} from '../../src/domain/mastery/attempt.js';
import type { AttemptInput } from '../../src/domain/mastery/attempt.js';
import { isId } from '../../src/domain/identifiers.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { problemMessages } from '../support/problems.js';

const d = (value: string) => parseCivilDate(value);

const base: AttemptInput = {
  learnerId: 'lrn_one',
  sessionId: 'sess_aaaaaa',
  cellId: 'cell_aaaaaa',
  deckKey: 'first-year',
  on: d('2025-03-03'),
  sequence: 1,
  given: 'puellae',
  expected: 'puellae',
  verdict: 'exact',
  toleranceLevel: 'standard',
  revisionId: 'rev_aaaaaa',
};

const make = (patch: Partial<AttemptInput> = {}) => attempt({ ...base, ...patch });

describe('attempt', () => {
  it('keeps every field it was given', () => {
    const entry = make();
    expect(entry.learnerId).toBe('lrn_one');
    expect(entry.cellId).toBe('cell_aaaaaa');
    expect(entry.revisionId).toBe('rev_aaaaaa');
    expect(isId('attempt', entry.id)).toBe(true);
  });

  it('derives the id from the session and the sequence', () => {
    expect(make().id).toBe(attemptIdFor('sess_aaaaaa', 1));
    expect(make({ sequence: 2 }).id).not.toBe(make().id);
  });

  it('lets the same cell be answered twice in one session', () => {
    const first = make({ sequence: 1 });
    const second = make({ sequence: 2, verdict: 'wrong', given: 'puella' });
    expect(first.id).not.toBe(second.id);
    expect(first.cellId).toBe(second.cellId);
  });

  it('normalises the typed answer', () => {
    expect(make({ given: '  puellae  ' }).given).toBe('puellae');
  });

  it('keeps a blank answer rather than refusing it', () => {
    const skipped = make({ given: '   ', verdict: 'wrong' });
    expect(skipped.given).toBe('');
    expect(wasBlank(skipped)).toBe(true);
  });

  it('refuses a blank expected form', () => {
    expect(problemMessages(() => make({ expected: '   ' }))).toEqual([
      'expected must not be blank',
    ]);
  });

  it('refuses a sequence below one', () => {
    expect(problemMessages(() => make({ sequence: 0 }))).toEqual([
      'sequence must be a positive whole number: 0',
    ]);
  });

  it('refuses a verdict that is not one of the four', () => {
    expect(problemMessages(() => make({ verdict: 'partial' as 'exact' }))).toEqual([
      'verdict is not a verdict: partial',
    ]);
  });

  it('refuses a negative elapsed time and accepts zero', () => {
    expect(problemMessages(() => make({ elapsedMs: -1 }))).toEqual([
      'elapsedMs must not be negative: -1',
    ]);
    expect(make({ elapsedMs: 0 }).elapsedMs).toBe(0);
  });

  it('leaves the elapsed time absent when none is given', () => {
    expect(make().elapsedMs).toBeUndefined();
  });
});

describe('isMatchVerdict', () => {
  it('accepts the four verdicts and nothing else', () => {
    for (const verdict of ['exact', 'tolerated', 'near', 'wrong']) {
      expect(isMatchVerdict(verdict)).toBe(true);
    }
    expect(isMatchVerdict('right')).toBe(false);
  });
});

describe('wasCorrect', () => {
  it('counts exact and tolerated as correct', () => {
    expect(wasCorrect(make({ verdict: 'exact' }))).toBe(true);
    expect(wasCorrect(make({ verdict: 'tolerated' }))).toBe(true);
  });

  it('counts near and wrong as not correct', () => {
    expect(wasCorrect(make({ verdict: 'near' }))).toBe(false);
    expect(wasCorrect(make({ verdict: 'wrong' }))).toBe(false);
  });
});

describe('compareAttempts', () => {
  it('sorts by day, then session, then sequence', () => {
    const sorted = [
      make({ sequence: 2 }),
      make({ on: d('2025-03-01'), sequence: 9 }),
      make({ sequence: 1 }),
    ].sort(compareAttempts);
    expect(sorted.map((entry) => entry.sequence)).toEqual([9, 1, 2]);
  });

  it('calls an attempt equal to itself', () => {
    const entry = make();
    expect(compareAttempts(entry, entry)).toBe(0);
  });
});

describe('tally', () => {
  const attempts = [
    make({ sequence: 1, verdict: 'exact' }),
    make({ sequence: 2, verdict: 'tolerated' }),
    make({ sequence: 3, verdict: 'near' }),
    make({ sequence: 4, verdict: 'wrong' }),
    make({ sequence: 5, verdict: 'wrong', given: '' }),
  ];

  it('counts every verdict', () => {
    expect(tally(attempts)).toEqual({
      total: 5,
      correct: 2,
      exact: 1,
      near: 1,
      wrong: 2,
      blank: 1,
    });
  });

  it('counts nothing for an empty list', () => {
    expect(tally([])).toEqual({ total: 0, correct: 0, exact: 0, near: 0, wrong: 0, blank: 0 });
  });

  it('reads accuracy off the tally', () => {
    expect(accuracyOf(tally(attempts))).toBeCloseTo(0.4);
    expect(accuracyOf(tally([]))).toBe(0);
  });
});

describe('medianElapsed', () => {
  it('takes the middle of an odd list', () => {
    const attempts = [100, 300, 200].map((elapsedMs, index) =>
      make({ sequence: index + 1, elapsedMs }),
    );
    expect(medianElapsed(attempts)).toBe(200);
  });

  it('averages the middle two of an even list', () => {
    const attempts = [100, 200, 300, 400].map((elapsedMs, index) =>
      make({ sequence: index + 1, elapsedMs }),
    );
    expect(medianElapsed(attempts)).toBe(250);
  });

  it('ignores attempts that were not timed', () => {
    const attempts = [make({ sequence: 1, elapsedMs: 500 }), make({ sequence: 2 })];
    expect(medianElapsed(attempts)).toBe(500);
  });

  it('gives nothing when nothing was timed', () => {
    expect(medianElapsed([make()])).toBeUndefined();
    expect(medianElapsed([])).toBeUndefined();
  });
});
