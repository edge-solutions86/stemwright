import { describe, expect, it } from 'vitest';
import {
  DEFAULT_POLICY,
  applyPolicyPatch,
  dailyCeiling,
  describePolicy,
  drillPolicy,
  isAtLeastAsDemanding,
  policyAllowsDay,
} from '../../src/domain/drill/policy.js';
import { problemMessages } from '../support/problems.js';

describe('drillPolicy', () => {
  it('is the default when nothing is patched', () => {
    expect(drillPolicy()).toEqual(DEFAULT_POLICY);
  });

  it('takes only the fields the patch names', () => {
    const patched = drillPolicy({ newPerDay: 4 });
    expect(patched.newPerDay).toBe(4);
    expect(patched.reviewsPerDay).toBe(DEFAULT_POLICY.reviewsPerDay);
    expect(patched.leechThreshold).toBe(DEFAULT_POLICY.leechThreshold);
  });

  it('lower cases the ladder key', () => {
    expect(drillPolicy({ ladderKey: 'Slow-Start' }).ladderKey).toBe('slow-start');
  });

  it('sorts and deduplicates the rest days', () => {
    expect(drillPolicy({ restDays: [7, 6, 6] }).restDays).toEqual([6, 7]);
  });
});

describe('applyPolicyPatch', () => {
  it('layers a patch onto something that is not the default', () => {
    const deckPolicy = drillPolicy({ newPerDay: 5, buryTwins: false });
    const assignment = applyPolicyPatch(deckPolicy, { reviewsPerDay: 20 });
    expect(assignment.newPerDay).toBe(5);
    expect(assignment.buryTwins).toBe(false);
    expect(assignment.reviewsPerDay).toBe(20);
  });

  it('allows a false patch to override a true base', () => {
    expect(applyPolicyPatch(DEFAULT_POLICY, { buryTwins: false }).buryTwins).toBe(false);
  });

  it('allows a zero patch to override a positive base', () => {
    expect(applyPolicyPatch(DEFAULT_POLICY, { newPerDay: 0 }).newPerDay).toBe(0);
  });

  it('leaves the base untouched', () => {
    const base = drillPolicy();
    applyPolicyPatch(base, { newPerDay: 99 });
    expect(base.newPerDay).toBe(DEFAULT_POLICY.newPerDay);
  });
});

describe('validation', () => {
  it('refuses a negative count', () => {
    expect(problemMessages(() => drillPolicy({ newPerDay: -1 }))).toEqual([
      'newPerDay must not be negative: -1',
    ]);
  });

  it('refuses a fractional count', () => {
    expect(problemMessages(() => drillPolicy({ perLexemeCap: 2.5 }))).toEqual([
      'perLexemeCap must be a whole number: 2.5',
    ]);
  });

  it('refuses a count over the ceiling', () => {
    expect(problemMessages(() => drillPolicy({ reviewsPerDay: 5000 }))).toEqual([
      'reviewsPerDay must be at most 2000: 5000',
    ]);
  });

  it('refuses a weekday outside one to seven', () => {
    expect(problemMessages(() => drillPolicy({ restDays: [0] }))).toEqual([
      'restDays is not an iso weekday: 0',
    ]);
    expect(problemMessages(() => drillPolicy({ restDays: [8] }))).toEqual([
      'restDays is not an iso weekday: 8',
    ]);
  });

  it('refuses resting every day', () => {
    expect(problemMessages(() => drillPolicy({ restDays: [1, 2, 3, 4, 5, 6, 7] }))).toEqual([
      'restDays would rest every day of the week',
    ]);
  });

  it('refuses a policy that asks for no drilling at all', () => {
    expect(problemMessages(() => drillPolicy({ newPerDay: 0, reviewsPerDay: 0 }))).toEqual([
      'newPerDay is zero and so is reviewsPerDay, which asks for no drilling at all',
    ]);
  });

  it('validates the merged result rather than the patch', () => {
    const zeroNew = drillPolicy({ newPerDay: 0 });
    expect(problemMessages(() => applyPolicyPatch(zeroNew, { reviewsPerDay: 0 }))).toHaveLength(1);
  });

  it('reports several problems at once', () => {
    expect(problemMessages(() => drillPolicy({ newPerDay: -1, rowSpacing: -2 }))).toHaveLength(2);
  });
});

describe('policyAllowsDay', () => {
  it('allows every day when nothing rests', () => {
    const policy = drillPolicy();
    expect([1, 2, 3, 4, 5, 6, 7].every((day) => policyAllowsDay(policy, day))).toBe(true);
  });

  it('refuses the days it rests on', () => {
    const policy = drillPolicy({ restDays: [6, 7] });
    expect(policyAllowsDay(policy, 5)).toBe(true);
    expect(policyAllowsDay(policy, 6)).toBe(false);
  });
});

describe('dailyCeiling', () => {
  it('adds the two budgets', () => {
    expect(dailyCeiling(drillPolicy({ newPerDay: 4, reviewsPerDay: 20 }))).toBe(24);
  });
});

describe('describePolicy', () => {
  it('reads the whole policy out', () => {
    expect(describePolicy(drillPolicy({ newPerDay: 5, reviewsPerDay: 30, restDays: [7] }))).toBe(
      'up to 5 new and 30 review cells a day, at most 3 cells of one word in a session, 2 questions between cells of the same row, twins buried after an answer, resting on weekdays 7',
    );
  });

  it('leaves out the parts that are switched off', () => {
    const quiet = drillPolicy({ rowSpacing: 0, buryTwins: false });
    expect(describePolicy(quiet)).toBe(
      'up to 10 new and 60 review cells a day, at most 3 cells of one word in a session',
    );
  });
});

describe('isAtLeastAsDemanding', () => {
  it('is true when every budget is at least as large', () => {
    expect(isAtLeastAsDemanding(drillPolicy({ newPerDay: 5 }), drillPolicy({ newPerDay: 9 }))).toBe(
      true,
    );
  });

  it('is false when any budget shrinks', () => {
    expect(
      isAtLeastAsDemanding(drillPolicy({ reviewsPerDay: 40 }), drillPolicy({ reviewsPerDay: 20 })),
    ).toBe(false);
  });

  it('counts a new rest day as less demanding', () => {
    expect(isAtLeastAsDemanding(drillPolicy(), drillPolicy({ restDays: [7] }))).toBe(false);
  });

  it('is true of a policy against itself', () => {
    expect(isAtLeastAsDemanding(DEFAULT_POLICY, DEFAULT_POLICY)).toBe(true);
  });
});
