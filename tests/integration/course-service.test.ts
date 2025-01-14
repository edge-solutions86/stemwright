import { describe, expect, it } from 'vitest';
import { createCourseService } from '../../src/app/services/course-service.js';
import { createDrillService } from '../../src/app/services/drill-service.js';
import type { Actor } from '../../src/domain/course/authz.js';
import { deck } from '../../src/domain/drill/deck.js';
import { ForbiddenError, NotFoundError, ValidationError } from '../../src/domain/errors.js';
import { ladder } from '../../src/domain/mastery/ladder.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { seededStores, testHarness } from '../support/app.js';
import { michaelmas } from '../support/course.js';
import { latinNoun } from '../support/profiles.js';

const d = (value: string) => parseCivilDate(value);
const DAY = d('2024-10-21');
const flat = ladder({ key: 'flat', rungs: [1, 3], spread: 0 });

const teacher: Actor = { id: 'lrn_teacher', role: 'instructor' };
const student: Actor = { id: 'lrn_one', role: 'learner' };
const other: Actor = { id: 'lrn_two', role: 'learner' };

const build = async () => {
  const stores = await seededStores();
  await stores.decks.put(deck(latinNoun, { key: 'all' }));
  await stores.decks.put(deck(latinNoun, { key: 'core' }));
  await stores.courses.put(michaelmas);
  const { bus, clock } = testHarness();
  const drill = createDrillService(stores, bus, clock);
  return { stores, bus, drill, service: createCourseService(stores, bus, clock, drill) };
};

const weekInput = {
  key: 'week-3',
  deckKey: 'all',
  narrowing: 'number=sg',
  from: d('2024-10-21'),
  to: d('2024-10-27'),
  masteredCells: 5,
};

describe('get, list and scopeOf', () => {
  it('reads a course back', async () => {
    const { service } = await build();
    expect((await service.get('latin-1a')).title).toBe('Latin 1A');
    expect(await service.list()).toHaveLength(1);
  });

  it('refuses a course nobody stored', async () => {
    const { service } = await build();
    await expect(service.get('nowhere')).rejects.toThrow(NotFoundError);
  });

  it('builds a scope with the roster in it', async () => {
    const { service } = await build();
    await service.enrol(teacher, 'latin-1a', { learnerId: 'lrn_one' });
    const scope = await service.scopeOf('latin-1a');
    expect(scope.roster.enrolments).toHaveLength(1);
  });
});

describe('create', () => {
  it('stores a course the actor teaches', async () => {
    const { service } = await build();
    const created = await service.create(teacher, {
      key: 'latin-1b',
      language: 'latin',
      from: d('2025-01-06'),
      to: d('2025-03-14'),
      instructorIds: ['lrn_teacher'],
    });
    expect(created.key).toBe('latin-1b');
    expect(await service.list()).toHaveLength(2);
  });

  it('refuses a course the actor does not teach', async () => {
    const { service } = await build();
    await expect(
      service.create(teacher, {
        key: 'greek-1',
        language: 'greek',
        from: d('2025-01-06'),
        to: d('2025-03-14'),
        instructorIds: ['lrn_someone'],
      }),
    ).rejects.toThrow(ForbiddenError);
  });
});

describe('enrol and withdraw', () => {
  it('enrols a learner', async () => {
    const { service } = await build();
    const enrolment = await service.enrol(teacher, 'latin-1a', { learnerId: 'lrn_one' });
    expect(enrolment.window).toEqual(michaelmas.term);
  });

  it('refuses a learner enrolling themselves', async () => {
    const { service } = await build();
    await expect(service.enrol(student, 'latin-1a', { learnerId: 'lrn_one' })).rejects.toThrow(
      ForbiddenError,
    );
  });

  it('withdraws somebody who is enrolled', async () => {
    const { service } = await build();
    await service.enrol(teacher, 'latin-1a', { learnerId: 'lrn_one' });
    expect((await service.withdrawLearner(teacher, 'latin-1a', 'lrn_one')).withdrawn).toBe(true);
  });

  it('refuses to withdraw somebody who was never enrolled', async () => {
    const { service } = await build();
    await expect(service.withdrawLearner(teacher, 'latin-1a', 'lrn_nine')).rejects.toThrow(
      NotFoundError,
    );
  });
});

describe('assignments', () => {
  it('adds one and reads it back', async () => {
    const { service } = await build();
    const added = await service.addAssignment(teacher, 'latin-1a', weekInput);
    expect(added.key).toBe('week-3');
    expect(await service.assignments('latin-1a')).toHaveLength(1);
  });

  it('refuses a learner', async () => {
    const { service } = await build();
    await expect(service.addAssignment(student, 'latin-1a', weekInput)).rejects.toThrow(
      ForbiddenError,
    );
  });

  it('refuses a deck the course does not teach', async () => {
    const { service } = await build();
    await expect(
      service.addAssignment(teacher, 'latin-1a', { ...weekInput, deckKey: 'nowhere' }),
    ).rejects.toThrow(ValidationError);
  });
});

describe('progressFor', () => {
  it('reports a learner their own progress', async () => {
    const { service } = await build();
    await service.enrol(teacher, 'latin-1a', { learnerId: 'lrn_one' });
    await service.addAssignment(teacher, 'latin-1a', weekInput);
    const progress = await service.progressFor(student, 'latin-1a', 'week-3', 'lrn_one', DAY, flat);
    expect(progress.cells).toBe(15);
    expect(progress.mastered).toBe(0);
  });

  it('refuses a learner reading somebody else', async () => {
    const { service } = await build();
    await service.enrol(teacher, 'latin-1a', { learnerId: 'lrn_one' });
    await service.enrol(teacher, 'latin-1a', { learnerId: 'lrn_two' });
    await service.addAssignment(teacher, 'latin-1a', weekInput);
    await expect(
      service.progressFor(other, 'latin-1a', 'week-3', 'lrn_one', DAY, flat),
    ).rejects.toThrow(ForbiddenError);
  });

  it('refuses an assignment nobody set', async () => {
    const { service } = await build();
    await service.enrol(teacher, 'latin-1a', { learnerId: 'lrn_one' });
    await expect(
      service.progressFor(teacher, 'latin-1a', 'week-9', 'lrn_one', DAY, flat),
    ).rejects.toThrow(NotFoundError);
  });

  it('refuses a learner who is not on the register', async () => {
    const { service } = await build();
    await service.addAssignment(teacher, 'latin-1a', weekInput);
    await expect(
      service.progressFor(teacher, 'latin-1a', 'week-3', 'lrn_nine', DAY, flat),
    ).rejects.toThrow(NotFoundError);
  });
});

describe('cohortProgress', () => {
  it('reports a row for every graded learner', async () => {
    const { service } = await build();
    await service.enrol(teacher, 'latin-1a', { learnerId: 'lrn_one' });
    await service.enrol(teacher, 'latin-1a', { learnerId: 'lrn_two' });
    await service.enrol(teacher, 'latin-1a', { learnerId: 'lrn_three', role: 'auditor' });
    await service.addAssignment(teacher, 'latin-1a', weekInput);
    const rows = await service.cohortProgress(teacher, 'latin-1a', 'week-3', DAY, flat);
    expect(rows.map((row) => row.learnerId)).toEqual(['lrn_one', 'lrn_two']);
  });

  it('refuses a learner', async () => {
    const { service } = await build();
    await service.enrol(teacher, 'latin-1a', { learnerId: 'lrn_one' });
    await service.addAssignment(teacher, 'latin-1a', weekInput);
    await expect(service.cohortProgress(student, 'latin-1a', 'week-3', DAY, flat)).rejects.toThrow(
      ForbiddenError,
    );
  });

  it('reports nothing for a course with nobody on it', async () => {
    const { service } = await build();
    await service.addAssignment(teacher, 'latin-1a', weekInput);
    expect(await service.cohortProgress(teacher, 'latin-1a', 'week-3', DAY, flat)).toEqual([]);
  });
});
