import { describe, expect, it } from 'vitest';
import { createDrillService } from '../../src/app/services/drill-service.js';
import { createReportService } from '../../src/app/services/report-service.js';
import type { Actor, CourseScope } from '../../src/domain/course/authz.js';
import { enrol, roster } from '../../src/domain/course/enrolment.js';
import { deck } from '../../src/domain/drill/deck.js';
import { ForbiddenError } from '../../src/domain/errors.js';
import { ladder } from '../../src/domain/mastery/ladder.js';
import { totalDue } from '../../src/domain/report/forecast.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { dayRange } from '../../src/domain/time/window.js';
import { seededStores, testHarness } from '../support/app.js';
import { michaelmas } from '../support/course.js';
import { latinNoun } from '../support/profiles.js';

const d = (value: string) => parseCivilDate(value);
const r = (from: string, to: string) => dayRange(d(from), d(to));
const DAY = d('2024-10-21');
const flat = ladder({ key: 'flat', rungs: [1, 3], spread: 0 });

const teacher: Actor = { id: 'lrn_teacher', role: 'instructor' };
const student: Actor = { id: 'lrn_one', role: 'learner' };
const other: Actor = { id: 'lrn_two', role: 'learner' };

const scope: CourseScope = {
  course: michaelmas,
  roster: roster(michaelmas, [
    enrol(michaelmas, { learnerId: 'lrn_one' }),
    enrol(michaelmas, { learnerId: 'lrn_two' }),
  ]),
};

const build = async () => {
  const stores = await seededStores();
  await stores.decks.put(deck(latinNoun, { key: 'all' }));
  const { bus, clock } = testHarness();
  const drill = createDrillService(stores, bus, clock);
  return { stores, drill, service: createReportService(stores, clock, drill) };
};

const settings = { policy: { newPerDay: 3, perLexemeCap: 10, rowSpacing: 0 }, ladder: flat };

const drillOnce = async (drill: ReturnType<typeof createDrillService>) => {
  const queue = await drill.queue('lrn_one', 'latin', 'all', DAY, settings);
  const target = queue.entries[0]!.cell;
  await drill.answer(
    {
      learnerId: 'lrn_one',
      language: 'latin',
      deckKey: 'all',
      cellId: target.id,
      given: 'x',
      on: DAY,
    },
    settings,
  );
  return target;
};

describe('learner', () => {
  it('counts the whole deck as untouched before anything is answered', async () => {
    const { service } = await build();
    const report = await service.learner(
      student,
      scope,
      'lrn_one',
      'latin',
      'all',
      r('2024-10-21', '2024-10-27'),
      flat,
    );
    expect(report.counts.known).toBe(30);
    expect(report.counts.untouched).toBe(30);
    expect(report.counts.introduced).toBe(0);
  });

  it('breaks coverage down both ways', async () => {
    const { service } = await build();
    const report = await service.learner(
      student,
      scope,
      'lrn_one',
      'latin',
      'all',
      r('2024-10-21', '2024-10-27'),
      flat,
    );
    expect(report.byBundle).toHaveLength(10);
    expect(report.byLexeme).toHaveLength(3);
    expect(report.weakest.length).toBeLessThanOrEqual(5);
  });

  it('shows a review in the forecast once one is scheduled', async () => {
    const { service, drill } = await build();
    await drillOnce(drill);
    const report = await service.learner(
      student,
      scope,
      'lrn_one',
      'latin',
      'all',
      r('2024-10-21', '2024-10-27'),
      flat,
    );
    expect(totalDue(report.forecast)).toBe(1);
  });

  it('lets the instructor read a learner', async () => {
    const { service } = await build();
    const report = await service.learner(
      teacher,
      scope,
      'lrn_one',
      'latin',
      'all',
      r('2024-10-21', '2024-10-27'),
      flat,
    );
    expect(report.learnerId).toBe('lrn_one');
  });

  it('refuses a learner reading somebody else', async () => {
    const { service } = await build();
    await expect(
      service.learner(other, scope, 'lrn_one', 'latin', 'all', r('2024-10-21', '2024-10-27'), flat),
    ).rejects.toThrow(ForbiddenError);
  });
});

describe('activity', () => {
  it('reports nothing for a learner who has not drilled', async () => {
    const { service } = await build();
    const report = await service.activity(student, scope, 'lrn_one', r('2024-10-21', '2024-10-23'));
    expect(report.days).toHaveLength(3);
    expect(report.days.every((day) => day.tally.total === 0)).toBe(true);
    expect(report.hardest).toEqual([]);
  });

  it('counts an answer on the day it happened', async () => {
    const { service, drill } = await build();
    await drillOnce(drill);
    const report = await service.activity(student, scope, 'lrn_one', r('2024-10-21', '2024-10-23'));
    expect(report.days[0]?.tally.total).toBe(1);
    expect(report.days[1]?.tally.total).toBe(0);
  });

  it('names a cell that was got wrong', async () => {
    const { service, drill } = await build();
    const target = await drillOnce(drill);
    const report = await service.activity(student, scope, 'lrn_one', r('2024-10-21', '2024-10-23'));
    expect(report.hardest.map((row) => row.cellId)).toContain(target.id);
  });

  it('refuses a learner reading somebody else', async () => {
    const { service } = await build();
    await expect(
      service.activity(other, scope, 'lrn_one', r('2024-10-21', '2024-10-23')),
    ).rejects.toThrow(ForbiddenError);
  });
});

describe('cohort', () => {
  const rows = [
    {
      assignmentKey: 'week-3',
      learnerId: 'lrn_one',
      on: DAY,
      cells: 10,
      mastered: 4,
      practiceDays: 2,
      masteryMet: false,
      practiceMet: true,
      met: false,
      overdue: false,
      selectsNothing: false,
    },
    {
      assignmentKey: 'week-3',
      learnerId: 'lrn_two',
      on: DAY,
      cells: 10,
      mastered: 10,
      practiceDays: 4,
      masteryMet: true,
      practiceMet: true,
      met: true,
      overdue: false,
      selectsNothing: false,
    },
  ];

  it('shows the instructor every row', async () => {
    const { service } = await build();
    const report = await service.cohort(teacher, scope, rows, 'week-3', DAY);
    expect(report.rows).toHaveLength(2);
    expect(report.hidden).toBe(0);
  });

  it('shows a learner only their own', async () => {
    const { service } = await build();
    const report = await service.cohort(student, scope, rows, 'week-3', DAY);
    expect(report.rows).toHaveLength(1);
    expect(report.hidden).toBe(1);
  });

  it('takes the day off the clock when none is given', async () => {
    const { service } = await build();
    expect((await service.cohort(teacher, scope, rows, 'week-3')).on).toBe('2024-10-21');
  });
});
