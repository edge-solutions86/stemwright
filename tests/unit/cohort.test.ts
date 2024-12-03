import { describe, expect, it } from 'vitest';
import type { Actor, CourseScope } from '../../src/domain/course/authz.js';
import { enrol, roster } from '../../src/domain/course/enrolment.js';
import type { AssignmentProgress } from '../../src/domain/course/progress.js';
import {
  cohortReport,
  describeCohort,
  furthestBehind,
  isRedacted,
  median,
  metShare,
} from '../../src/domain/report/cohort.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { michaelmas } from '../support/course.js';

const d = (value: string) => parseCivilDate(value);
const ON = d('2024-10-27');

const student: Actor = { id: 'lrn_one', role: 'learner' };
const teacher: Actor = { id: 'lrn_teacher', role: 'instructor' };
const stranger: Actor = { id: 'lrn_other', role: 'instructor' };

const scope: CourseScope = {
  course: michaelmas,
  roster: roster(michaelmas, [
    enrol(michaelmas, { learnerId: 'lrn_one' }),
    enrol(michaelmas, { learnerId: 'lrn_two' }),
    enrol(michaelmas, { learnerId: 'lrn_three' }),
  ]),
};

const progress = (
  learnerId: string,
  mastered: number,
  practiceDays: number,
  met: boolean,
): AssignmentProgress => ({
  assignmentKey: 'week-3',
  learnerId,
  on: ON,
  cells: 10,
  mastered,
  practiceDays,
  masteryMet: met,
  practiceMet: met,
  met,
  overdue: false,
  selectsNothing: false,
});

const rows = [
  progress('lrn_one', 3, 2, false),
  progress('lrn_two', 9, 5, true),
  progress('lrn_three', 6, 3, false),
];

describe('median', () => {
  it('takes the middle of an odd list', () => {
    expect(median([1, 3, 2])).toBe(2);
  });

  it('averages the middle two of an even list', () => {
    expect(median([1, 2, 3, 4])).toBe(2.5);
  });

  it('is zero for an empty list', () => {
    expect(median([])).toBe(0);
  });
});

describe('cohortReport', () => {
  it('shows every row to the instructor', () => {
    const report = cohortReport(teacher, scope, rows, 'week-3', ON);
    expect(report.rows).toHaveLength(3);
    expect(report.hidden).toBe(0);
    expect(isRedacted(report)).toBe(false);
  });

  it('sorts by mastery share, highest first', () => {
    const report = cohortReport(teacher, scope, rows, 'week-3', ON);
    expect(report.rows.map((row) => row.learnerId)).toEqual(['lrn_two', 'lrn_three', 'lrn_one']);
  });

  it('shows a learner only their own row and counts what it hid', () => {
    const report = cohortReport(student, scope, rows, 'week-3', ON);
    expect(report.rows.map((row) => row.learnerId)).toEqual(['lrn_one']);
    expect(report.hidden).toBe(2);
    expect(isRedacted(report)).toBe(true);
  });

  it('hides everything from an instructor of another course', () => {
    const report = cohortReport(stranger, scope, rows, 'week-3', ON);
    expect(report.rows).toEqual([]);
    expect(report.hidden).toBe(3);
  });

  it('counts how many met the target among the rows it shows', () => {
    expect(cohortReport(teacher, scope, rows, 'week-3', ON).metCount).toBe(1);
    expect(cohortReport(student, scope, rows, 'week-3', ON).metCount).toBe(0);
  });

  it('reads the median share off the visible rows', () => {
    expect(cohortReport(teacher, scope, rows, 'week-3', ON).medianShare).toBeCloseTo(0.6);
  });

  it('reports zeros for an empty cohort', () => {
    const report = cohortReport(teacher, scope, [], 'week-3', ON);
    expect(report.rows).toEqual([]);
    expect(report.medianShare).toBe(0);
    expect(metShare(report)).toBe(0);
  });

  it('carries the assignment key and the day', () => {
    const report = cohortReport(teacher, scope, rows, 'week-3', ON);
    expect(report.assignmentKey).toBe('week-3');
    expect(report.on).toBe('2024-10-27');
  });
});

describe('metShare', () => {
  it('is the share of visible rows that met the target', () => {
    expect(metShare(cohortReport(teacher, scope, rows, 'week-3', ON))).toBeCloseTo(1 / 3);
  });
});

describe('furthestBehind', () => {
  it('ranks the unmet rows by how far they have to go', () => {
    const report = cohortReport(teacher, scope, rows, 'week-3', ON);
    expect(furthestBehind(report).map((row) => row.learnerId)).toEqual(['lrn_one', 'lrn_three']);
  });

  it('leaves out rows that met the target', () => {
    const report = cohortReport(teacher, scope, rows, 'week-3', ON);
    expect(furthestBehind(report).map((row) => row.learnerId)).not.toContain('lrn_two');
  });

  it('honours the limit', () => {
    expect(furthestBehind(cohortReport(teacher, scope, rows, 'week-3', ON), 1)).toHaveLength(1);
  });
});

describe('describeCohort', () => {
  it('reads the counts out', () => {
    expect(describeCohort(cohortReport(teacher, scope, rows, 'week-3', ON))).toBe(
      '1 of 3 have met week-3, 33 percent',
    );
  });

  it('says how many rows were hidden', () => {
    expect(describeCohort(cohortReport(student, scope, rows, 'week-3', ON))).toBe(
      '0 of 1 have met week-3, 0 percent, 2 rows hidden from you',
    );
  });

  it('says so when everything is hidden', () => {
    expect(describeCohort(cohortReport(stranger, scope, rows, 'week-3', ON))).toBe(
      'every row of week-3 is hidden from you',
    );
  });

  it('says so when nobody is working on it', () => {
    expect(describeCohort(cohortReport(teacher, scope, [], 'week-3', ON))).toBe(
      'nobody is working on week-3',
    );
  });
});
