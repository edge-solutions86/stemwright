import { describe, expect, it } from 'vitest';
import { enrol } from '../../src/domain/course/enrolment.js';
import {
  clampToCeiling,
  describeRefusals,
  learnerPreferences,
  noPreferences,
  resolvePolicyFor,
  withPatch,
} from '../../src/domain/course/preferences.js';
import { DEFAULT_POLICY, drillPolicy } from '../../src/domain/drill/policy.js';
import { michaelmas } from '../support/course.js';
import { problemMessages } from '../support/problems.js';

const base = drillPolicy({ newPerDay: 10, reviewsPerDay: 60, perLexemeCap: 3 });

describe('learnerPreferences', () => {
  it('keeps what it was given', () => {
    const preferences = learnerPreferences({
      learnerId: 'lrn_one',
      patch: { newPerDay: 4 },
      toleranceLevel: 'Lenient',
      ladderKey: 'Slow-Start',
    });
    expect(preferences.patch).toEqual({ newPerDay: 4 });
    expect(preferences.toleranceLevel).toBe('lenient');
    expect(preferences.ladderKey).toBe('slow-start');
  });

  it('starts empty when nothing is asked for', () => {
    const preferences = noPreferences('lrn_one');
    expect(preferences.patch).toEqual({});
    expect(preferences.toleranceLevel).toBeUndefined();
  });

  it('refuses resting every day', () => {
    expect(
      problemMessages(() =>
        learnerPreferences({ learnerId: 'lrn_one', patch: { restDays: [1, 2, 3, 4, 5, 6, 7] } }),
      ),
    ).toEqual(['patch.restDays would rest every day of the week']);
  });
});

describe('clampToCeiling', () => {
  it('lets a smaller number through', () => {
    const clamped = clampToCeiling(base, { newPerDay: 4 });
    expect(clamped.patch).toEqual({ newPerDay: 4 });
    expect(clamped.refused).toEqual([]);
  });

  it('refuses a larger one and says which', () => {
    const clamped = clampToCeiling(base, { newPerDay: 40 });
    expect(clamped.patch).toEqual({});
    expect(clamped.refused).toEqual(['newPerDay']);
  });

  it('lets an equal number through', () => {
    expect(clampToCeiling(base, { newPerDay: 10 }).refused).toEqual([]);
  });

  it('checks each budget separately', () => {
    const clamped = clampToCeiling(base, { newPerDay: 4, reviewsPerDay: 900, perLexemeCap: 9 });
    expect(clamped.patch).toEqual({ newPerDay: 4 });
    expect(clamped.refused).toEqual(['reviewsPerDay', 'perLexemeCap']);
  });

  it('leaves a patch it does not police alone', () => {
    expect(clampToCeiling(base, { buryTwins: false }).patch).toEqual({ buryTwins: false });
  });

  it('leaves the original patch untouched', () => {
    const patch = { newPerDay: 40 };
    clampToCeiling(base, patch);
    expect(patch.newPerDay).toBe(40);
  });
});

describe('resolvePolicyFor', () => {
  const enrolment = enrol(michaelmas, { learnerId: 'lrn_one' });

  it('takes the base when nothing else is set', () => {
    const resolution = resolvePolicyFor(base, {}, undefined, noPreferences('lrn_one'));
    expect(resolution.policy).toEqual(base);
    expect(resolution.refused).toEqual([]);
  });

  it('lets the course tighten the base', () => {
    const resolution = resolvePolicyFor(
      base,
      { newPerDay: 5 },
      undefined,
      noPreferences('lrn_one'),
    );
    expect(resolution.policy.newPerDay).toBe(5);
  });

  it('lets an enrolment raise what the course set', () => {
    const raised = { ...enrolment, policyPatch: { newPerDay: 20 } };
    const resolution = resolvePolicyFor(base, { newPerDay: 5 }, raised, noPreferences('lrn_one'));
    expect(resolution.policy.newPerDay).toBe(20);
  });

  it('lets a learner ask for less', () => {
    const preferences = learnerPreferences({ learnerId: 'lrn_one', patch: { newPerDay: 2 } });
    const resolution = resolvePolicyFor(base, { newPerDay: 5 }, enrolment, preferences);
    expect(resolution.policy.newPerDay).toBe(2);
  });

  it('does not let a learner ask for more and says so', () => {
    const preferences = learnerPreferences({ learnerId: 'lrn_one', patch: { newPerDay: 50 } });
    const resolution = resolvePolicyFor(base, { newPerDay: 5 }, enrolment, preferences);
    expect(resolution.policy.newPerDay).toBe(5);
    expect(resolution.refused).toEqual(['newPerDay']);
  });

  it('clamps against the enrolment rather than the base', () => {
    const raised = { ...enrolment, policyPatch: { newPerDay: 20 } };
    const preferences = learnerPreferences({ learnerId: 'lrn_one', patch: { newPerDay: 15 } });
    const resolution = resolvePolicyFor(base, {}, raised, preferences);
    expect(resolution.policy.newPerDay).toBe(15);
    expect(resolution.refused).toEqual([]);
  });

  it('lets a learner set something that is not a budget', () => {
    const preferences = learnerPreferences({ learnerId: 'lrn_one', patch: { restDays: [7] } });
    const resolution = resolvePolicyFor(DEFAULT_POLICY, {}, enrolment, preferences);
    expect(resolution.policy.restDays).toEqual([7]);
  });
});

describe('describeRefusals', () => {
  it('says so when everything was applied', () => {
    const resolution = resolvePolicyFor(base, {}, undefined, noPreferences('lrn_one'));
    expect(describeRefusals(resolution)).toBe('every preference was applied');
  });

  it('names what was refused', () => {
    const preferences = learnerPreferences({
      learnerId: 'lrn_one',
      patch: { newPerDay: 90, reviewsPerDay: 900 },
    });
    const resolution = resolvePolicyFor(base, {}, undefined, preferences);
    expect(describeRefusals(resolution)).toBe(
      'newPerDay, reviewsPerDay were above the ceiling and were left alone',
    );
  });
});

describe('withPatch', () => {
  it('merges rather than replacing', () => {
    const once = withPatch(noPreferences('lrn_one'), { newPerDay: 4 });
    const twice = withPatch(once, { reviewsPerDay: 20 });
    expect(twice.patch).toEqual({ newPerDay: 4, reviewsPerDay: 20 });
  });

  it('keeps the tolerance level through an edit', () => {
    const preferences = learnerPreferences({ learnerId: 'lrn_one', toleranceLevel: 'exact' });
    expect(withPatch(preferences, { newPerDay: 4 }).toleranceLevel).toBe('exact');
  });
});
