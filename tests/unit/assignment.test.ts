import { describe, expect, it } from 'vitest';
import {
  assignment,
  assignmentIdFor,
  describeAssignment,
  describeTarget,
  hasStartedOn,
  isOpenOn,
  isOverdueOn,
} from '../../src/domain/course/assignment.js';
import { formatSelector } from '../../src/domain/features/selector.js';
import { isId } from '../../src/domain/identifiers.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { michaelmas } from '../support/course.js';
import { problemMessages } from '../support/problems.js';
import { latinNoun } from '../support/profiles.js';

const d = (value: string) => parseCivilDate(value);
const make = (input: Parameters<typeof assignment>[2]) => assignment(latinNoun, michaelmas, input);

const week3 = make({
  key: 'week-3',
  title: 'Singular cases',
  deckKey: 'all',
  narrowing: 'number=sg',
  from: d('2024-10-21'),
  to: d('2024-10-27'),
  masteredCells: 12,
});

describe('assignment', () => {
  it('keeps the key, deck and window', () => {
    expect(week3.key).toBe('week-3');
    expect(week3.deckKey).toBe('all');
    expect(week3.window).toEqual({ from: '2024-10-21', to: '2024-10-27' });
    expect(isId('assignment', week3.id)).toBe(true);
  });

  it('derives the id from the course and the key', () => {
    expect(week3.id).toBe(assignmentIdFor('latin-1a', 'week-3'));
  });

  it('parses the narrowing selector', () => {
    expect(formatSelector(week3.narrowing)).toBe('number=sg');
  });

  it('defaults the title to the key and the narrowing to nothing', () => {
    const bare = make({
      key: 'week-4',
      deckKey: 'core',
      from: d('2024-10-28'),
      to: d('2024-11-03'),
      practiceDays: 4,
    });
    expect(bare.title).toBe('week-4');
    expect(bare.narrowing.clauses).toEqual([]);
  });

  it('refuses a deck the course does not teach', () => {
    expect(
      problemMessages(() =>
        make({
          key: 'x',
          deckKey: 'third-year',
          from: d('2024-10-21'),
          to: d('2024-10-27'),
          practiceDays: 1,
        }),
      ),
    ).toEqual(['deckKey is not a deck of latin-1a: third-year']);
  });

  it('refuses a window that misses the term entirely', () => {
    expect(
      problemMessages(() =>
        make({
          key: 'x',
          deckKey: 'all',
          from: d('2025-02-01'),
          to: d('2025-02-07'),
          practiceDays: 1,
        }),
      ),
    ).toEqual(['window falls outside the term 2024-10-07 to 2024-12-13']);
  });

  it('accepts a window that overlaps the term at one end', () => {
    const straddle = make({
      key: 'revision',
      deckKey: 'all',
      from: d('2024-12-09'),
      to: d('2024-12-20'),
      practiceDays: 5,
    });
    expect(straddle.window.to).toBe('2024-12-20');
  });

  it('refuses a window that ends before it starts', () => {
    expect(
      problemMessages(() =>
        make({
          key: 'x',
          deckKey: 'all',
          from: d('2024-10-27'),
          to: d('2024-10-21'),
          practiceDays: 1,
        }),
      ),
    ).toHaveLength(1);
  });

  it('refuses a narrowing the schema rejects', () => {
    expect(
      problemMessages(() =>
        make({
          key: 'x',
          deckKey: 'all',
          narrowing: 'tense=pres',
          from: d('2024-10-21'),
          to: d('2024-10-27'),
          practiceDays: 1,
        }),
      ),
    ).toEqual(['narrowing tense is not a dimension of latin']);
  });

  it('refuses an assignment with no target at all', () => {
    expect(
      problemMessages(() =>
        make({ key: 'x', deckKey: 'all', from: d('2024-10-21'), to: d('2024-10-27') }),
      ),
    ).toEqual(['target must set at least one of masteredCells and practiceDays']);
  });

  it('refuses a target below one', () => {
    expect(
      problemMessages(() =>
        make({
          key: 'x',
          deckKey: 'all',
          from: d('2024-10-21'),
          to: d('2024-10-27'),
          masteredCells: 0,
        }),
      ).some((message) => message.includes('masteredCells')),
    ).toBe(true);
  });

  it('takes both targets at once', () => {
    const both = make({
      key: 'week-5',
      deckKey: 'all',
      from: d('2024-11-04'),
      to: d('2024-11-10'),
      masteredCells: 8,
      practiceDays: 5,
    });
    expect(both.target).toEqual({ masteredCells: 8, practiceDays: 5 });
  });

  it('carries a tolerance override and a policy patch', () => {
    const strict = make({
      key: 'exam',
      deckKey: 'all',
      from: d('2024-12-09'),
      to: d('2024-12-13'),
      masteredCells: 20,
      toleranceLevel: 'exact',
      policyPatch: { newPerDay: 0 },
    });
    expect(strict.toleranceLevel).toBe('exact');
    expect(strict.policyPatch).toEqual({ newPerDay: 0 });
  });
});

describe('the window predicates', () => {
  it('is open on both endpoints', () => {
    expect(isOpenOn(week3, d('2024-10-21'))).toBe(true);
    expect(isOpenOn(week3, d('2024-10-27'))).toBe(true);
    expect(isOpenOn(week3, d('2024-10-28'))).toBe(false);
  });

  it('is overdue only after the last day', () => {
    expect(isOverdueOn(week3, d('2024-10-27'))).toBe(false);
    expect(isOverdueOn(week3, d('2024-10-28'))).toBe(true);
  });

  it('has started from the first day on', () => {
    expect(hasStartedOn(week3, d('2024-10-20'))).toBe(false);
    expect(hasStartedOn(week3, d('2024-10-21'))).toBe(true);
    expect(hasStartedOn(week3, d('2024-11-01'))).toBe(true);
  });
});

describe('describeTarget', () => {
  it('reads one target out', () => {
    expect(describeTarget({ masteredCells: 12, practiceDays: undefined })).toBe(
      '12 cells to the top of the ladder',
    );
    expect(describeTarget({ masteredCells: undefined, practiceDays: 4 })).toBe(
      '4 days of practice',
    );
  });

  it('joins two targets', () => {
    expect(describeTarget({ masteredCells: 8, practiceDays: 5 })).toBe(
      '8 cells to the top of the ladder and 5 days of practice',
    );
  });
});

describe('describeAssignment', () => {
  it('names the narrowing when there is one', () => {
    expect(describeAssignment(week3)).toBe(
      'Singular cases: all narrowed to number=sg, 12 cells to the top of the ladder, due 2024-10-27',
    );
  });

  it('names the bare deck when there is not', () => {
    const bare = make({
      key: 'week-4',
      deckKey: 'core',
      from: d('2024-10-28'),
      to: d('2024-11-03'),
      practiceDays: 4,
    });
    expect(describeAssignment(bare)).toBe('week-4: core, 4 days of practice, due 2024-11-03');
  });
});
