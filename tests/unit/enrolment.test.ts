import { describe, expect, it } from 'vitest';
import {
  activeOn,
  enrol,
  enrolmentOf,
  gradedLearners,
  isActiveOn,
  isEnrolmentRole,
  isGraded,
  reinstate,
  roster,
  withPolicyPatch,
  withShields,
  withdraw,
} from '../../src/domain/course/enrolment.js';
import { shieldSet } from '../../src/domain/mastery/shield.js';
import { ConflictError } from '../../src/domain/errors.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { dayRange } from '../../src/domain/time/window.js';
import { michaelmas } from '../support/course.js';
import { problemMessages } from '../support/problems.js';

const d = (value: string) => parseCivilDate(value);
const r = (from: string, to: string) => dayRange(d(from), d(to));

const student = enrol(michaelmas, { learnerId: 'lrn_one' });
const latecomer = enrol(michaelmas, { learnerId: 'lrn_two', from: d('2024-11-01') });
const auditor = enrol(michaelmas, { learnerId: 'lrn_three', role: 'auditor' });

describe('enrol', () => {
  it('defaults the window to the whole term', () => {
    expect(student.window).toEqual(michaelmas.term);
  });

  it('defaults the role to learner', () => {
    expect(student.role).toBe('learner');
  });

  it('takes a narrower window', () => {
    expect(latecomer.window).toEqual({ from: '2024-11-01', to: '2024-12-13' });
  });

  it('clips a window that overhangs the term rather than refusing it', () => {
    const early = enrol(michaelmas, {
      learnerId: 'lrn_four',
      from: d('2024-09-01'),
      to: d('2025-01-31'),
    });
    expect(early.window).toEqual(michaelmas.term);
  });

  it('refuses a window entirely outside the term', () => {
    expect(
      problemMessages(() =>
        enrol(michaelmas, { learnerId: 'lrn_five', from: d('2025-02-01'), to: d('2025-02-28') }),
      ),
    ).toEqual(['window lies entirely outside the term 2024-10-07 to 2024-12-13']);
  });

  it('refuses a window that ends before it starts', () => {
    expect(
      problemMessages(() =>
        enrol(michaelmas, { learnerId: 'lrn_six', from: d('2024-12-01'), to: d('2024-11-01') }),
      ),
    ).toHaveLength(1);
  });

  it('starts with no shields and no policy patch', () => {
    expect(student.shields.windows).toEqual([]);
    expect(student.policyPatch).toEqual({});
  });

  it('names the course it belongs to', () => {
    expect(student.courseKey).toBe('latin-1a');
  });
});

describe('isEnrolmentRole', () => {
  it('accepts the three roles and nothing else', () => {
    expect(isEnrolmentRole('learner')).toBe(true);
    expect(isEnrolmentRole('auditor')).toBe(true);
    expect(isEnrolmentRole('assistant')).toBe(true);
    expect(isEnrolmentRole('lecturer')).toBe(false);
  });
});

describe('isActiveOn', () => {
  it('is true inside the window', () => {
    expect(isActiveOn(student, d('2024-11-01'))).toBe(true);
  });

  it('is false before a late enrolment starts', () => {
    expect(isActiveOn(latecomer, d('2024-10-20'))).toBe(false);
    expect(isActiveOn(latecomer, d('2024-11-01'))).toBe(true);
  });

  it('is false once withdrawn, whatever the window says', () => {
    expect(isActiveOn(withdraw(student), d('2024-11-01'))).toBe(false);
  });
});

describe('withdraw and reinstate', () => {
  it('withdraws once and refuses a second time', () => {
    const gone = withdraw(student);
    expect(gone.withdrawn).toBe(true);
    expect(() => withdraw(gone)).toThrow(ConflictError);
  });

  it('reinstates and leaves an active enrolment alone', () => {
    expect(reinstate(withdraw(student)).withdrawn).toBe(false);
    expect(reinstate(student)).toBe(student);
  });
});

describe('withShields and withPolicyPatch', () => {
  it('attaches shields', () => {
    const shielded = withShields(student, shieldSet([{ window: r('2024-11-04', '2024-11-08') }]));
    expect(shielded.shields.windows).toHaveLength(1);
    expect(student.shields.windows).toHaveLength(0);
  });

  it('merges policy patches rather than replacing them', () => {
    const once = withPolicyPatch(student, { newPerDay: 4 });
    const twice = withPolicyPatch(once, { reviewsPerDay: 20 });
    expect(twice.policyPatch).toEqual({ newPerDay: 4, reviewsPerDay: 20 });
  });

  it('lets a later patch overwrite an earlier one', () => {
    const once = withPolicyPatch(student, { newPerDay: 4 });
    expect(withPolicyPatch(once, { newPerDay: 9 }).policyPatch.newPerDay).toBe(9);
  });
});

describe('isGraded', () => {
  it('grades a learner and nobody else', () => {
    expect(isGraded(student)).toBe(true);
    expect(isGraded(auditor)).toBe(false);
    expect(isGraded(withdraw(student))).toBe(false);
  });
});

describe('roster', () => {
  const list = roster(michaelmas, [student, latecomer, auditor]);

  it('holds every enrolment', () => {
    expect(list.enrolments).toHaveLength(3);
    expect(enrolmentOf(list, 'lrn_two')).toBe(latecomer);
    expect(enrolmentOf(list, 'lrn_nine')).toBeUndefined();
  });

  it('refuses an enrolment from another course', () => {
    const stray = { ...student, courseKey: 'greek-2' };
    expect(problemMessages(() => roster(michaelmas, [stray]))).toEqual([
      'lrn_one is enrolled on greek-2, not latin-1a',
    ]);
  });

  it('refuses the same learner twice', () => {
    expect(problemMessages(() => roster(michaelmas, [student, student]))).toEqual([
      'lrn_one is enrolled twice',
    ]);
  });

  it('reports who is active on a day', () => {
    expect(activeOn(list, d('2024-10-20')).map((e) => e.learnerId)).toEqual([
      'lrn_one',
      'lrn_three',
    ]);
    expect(activeOn(list, d('2024-11-05'))).toHaveLength(3);
  });

  it('names the graded learners in id order', () => {
    expect(gradedLearners(list)).toEqual(['lrn_one', 'lrn_two']);
  });
});
