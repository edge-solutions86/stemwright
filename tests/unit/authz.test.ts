import { describe, expect, it } from 'vitest';
import {
  can,
  canReadLearner,
  describePermissions,
  isAccountRole,
  isEnrolled,
  require as requirePermission,
  requireReadLearner,
  teachesCourse,
  visibleLearners,
} from '../../src/domain/course/authz.js';
import type { Actor, CourseScope } from '../../src/domain/course/authz.js';
import { course } from '../../src/domain/course/course.js';
import { enrol, roster, withdraw } from '../../src/domain/course/enrolment.js';
import { ForbiddenError } from '../../src/domain/errors.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { michaelmas } from '../support/course.js';

const d = (value: string) => parseCivilDate(value);

const student: Actor = { id: 'lrn_one', role: 'learner' };
const teacher: Actor = { id: 'lrn_teacher', role: 'instructor' };
const stranger: Actor = { id: 'lrn_other', role: 'instructor' };
const admin: Actor = { id: 'lrn_admin', role: 'admin' };

const scope: CourseScope = {
  course: michaelmas,
  roster: roster(michaelmas, [
    enrol(michaelmas, { learnerId: 'lrn_one' }),
    enrol(michaelmas, { learnerId: 'lrn_two' }),
    withdraw(enrol(michaelmas, { learnerId: 'lrn_three' })),
  ]),
};

const otherCourse = course({
  key: 'greek-2',
  language: 'greek',
  from: d('2025-01-06'),
  to: d('2025-03-14'),
  instructorIds: ['lrn_other'],
});
const otherScope: CourseScope = { course: otherCourse, roster: roster(otherCourse, []) };

describe('isAccountRole', () => {
  it('accepts the three roles and nothing else', () => {
    expect(isAccountRole('learner')).toBe(true);
    expect(isAccountRole('instructor')).toBe(true);
    expect(isAccountRole('admin')).toBe(true);
    expect(isAccountRole('tutor')).toBe(false);
  });
});

describe('teachesCourse and isEnrolled', () => {
  it('recognises the instructor of this course', () => {
    expect(teachesCourse(teacher, scope)).toBe(true);
    expect(teachesCourse(stranger, scope)).toBe(false);
  });

  it('does not let a learner account teach even if it is listed', () => {
    const listed: Actor = { id: 'lrn_teacher', role: 'learner' };
    expect(teachesCourse(listed, scope)).toBe(false);
  });

  it('recognises an active enrolment and not a withdrawn one', () => {
    expect(isEnrolled(student, scope)).toBe(true);
    expect(isEnrolled({ id: 'lrn_three', role: 'learner' }, scope)).toBe(false);
    expect(isEnrolled({ id: 'lrn_nine', role: 'learner' }, scope)).toBe(false);
  });
});

describe('can', () => {
  it('lets an enrolled learner drill and read their own progress', () => {
    expect(can(student, 'drill', scope)).toBe(true);
    expect(can(student, 'read-own-progress', scope)).toBe(true);
  });

  it('does not let a learner read the cohort or edit anything', () => {
    expect(can(student, 'read-cohort', scope)).toBe(false);
    expect(can(student, 'edit-deck', scope)).toBe(false);
    expect(can(student, 'edit-lexicon')).toBe(false);
  });

  it('lets the instructor of the course read the cohort and edit it', () => {
    expect(can(teacher, 'read-cohort', scope)).toBe(true);
    expect(can(teacher, 'edit-course', scope)).toBe(true);
  });

  it('does not let an instructor of another course touch this one', () => {
    expect(can(stranger, 'read-cohort', scope)).toBe(false);
    expect(can(stranger, 'edit-course', scope)).toBe(false);
    expect(can(stranger, 'read-cohort', otherScope)).toBe(true);
  });

  it('lets any instructor edit the lexicon and publish a profile', () => {
    expect(can(stranger, 'edit-lexicon')).toBe(true);
    expect(can(stranger, 'publish-profile')).toBe(true);
  });

  it('fails closed when a course permission is asked without a scope', () => {
    expect(can(teacher, 'read-cohort')).toBe(false);
    expect(can(student, 'drill')).toBe(false);
  });

  it('gives an admin everything', () => {
    expect(can(admin, 'manage-accounts')).toBe(true);
    expect(can(admin, 'edit-course', scope)).toBe(true);
    expect(can(admin, 'read-cohort')).toBe(true);
  });

  it('gives nobody but an admin account management', () => {
    expect(can(teacher, 'manage-accounts', scope)).toBe(false);
  });
});

describe('require', () => {
  it('passes silently when the actor may act', () => {
    expect(() => requirePermission(teacher, 'read-cohort', scope)).not.toThrow();
  });

  it('names the actor and the permission when it refuses', () => {
    expect(() => requirePermission(student, 'read-cohort', scope)).toThrow(ForbiddenError);
    expect(() => requirePermission(student, 'read-cohort', scope)).toThrow(
      /lrn_one may not read-cohort/,
    );
  });
});

describe('canReadLearner', () => {
  it('lets a learner read their own work', () => {
    expect(canReadLearner(student, 'lrn_one', scope)).toBe(true);
  });

  it('does not let a learner read somebody else', () => {
    expect(canReadLearner(student, 'lrn_two', scope)).toBe(false);
  });

  it('lets the instructor read anybody on the course', () => {
    expect(canReadLearner(teacher, 'lrn_two', scope)).toBe(true);
  });

  it('does not let an instructor of another course read them', () => {
    expect(canReadLearner(stranger, 'lrn_two', scope)).toBe(false);
  });

  it('lets an admin read anybody with or without a scope', () => {
    expect(canReadLearner(admin, 'lrn_two')).toBe(true);
    expect(canReadLearner(admin, 'lrn_two', scope)).toBe(true);
  });

  it('lets a learner read their own work with no scope at all', () => {
    expect(canReadLearner(student, 'lrn_one')).toBe(true);
  });

  it('refuses a learner who has withdrawn from the course', () => {
    const gone: Actor = { id: 'lrn_three', role: 'learner' };
    expect(canReadLearner(gone, 'lrn_three', scope)).toBe(false);
  });

  it('throws with both names when a caller demands access', () => {
    expect(() => requireReadLearner(student, 'lrn_two', scope)).toThrow(
      /lrn_one may not read the work of lrn_two/,
    );
  });
});

describe('visibleLearners', () => {
  it('narrows a list to what the actor may see', () => {
    expect(visibleLearners(student, ['lrn_one', 'lrn_two'], scope)).toEqual(['lrn_one']);
    expect(visibleLearners(teacher, ['lrn_one', 'lrn_two'], scope)).toEqual(['lrn_one', 'lrn_two']);
  });

  it('returns nothing for an instructor of another course', () => {
    expect(visibleLearners(stranger, ['lrn_one'], scope)).toEqual([]);
  });
});

describe('describePermissions', () => {
  it('lists what a learner may do on their course', () => {
    expect(describePermissions(student, scope)).toEqual(['drill', 'read-own-progress']);
  });

  it('lists what an instructor may do', () => {
    expect(describePermissions(teacher, scope)).toEqual([
      'drill',
      'read-own-progress',
      'read-cohort',
      'edit-deck',
      'edit-course',
      'edit-lexicon',
      'publish-profile',
    ]);
  });

  it('lists everything for an admin', () => {
    expect(describePermissions(admin, scope)).toHaveLength(8);
  });
});
