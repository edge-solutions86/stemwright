import { describe, expect, it } from 'vitest';
import { createDeckService } from '../../src/app/services/deck-service.js';
import { createDrillService } from '../../src/app/services/drill-service.js';
import type { Actor, CourseScope } from '../../src/domain/course/authz.js';
import { roster } from '../../src/domain/course/enrolment.js';
import { ConflictError, ForbiddenError, NotFoundError } from '../../src/domain/errors.js';
import { formatSelector } from '../../src/domain/features/selector.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { seededStores, testHarness } from '../support/app.js';
import { michaelmas } from '../support/course.js';

const d = (value: string) => parseCivilDate(value);
const teacher: Actor = { id: 'lrn_teacher', role: 'instructor' };
const student: Actor = { id: 'lrn_one', role: 'learner' };
const scope: CourseScope = { course: michaelmas, roster: roster(michaelmas, []) };

const build = async () => {
  const stores = await seededStores();
  const { bus, clock } = testHarness();
  const drill = createDrillService(stores, bus, clock);
  return { stores, service: createDeckService(stores, clock, drill) };
};

describe('create', () => {
  it('stores a deck that selects something', async () => {
    const { service } = await build();
    const created = await service.create(teacher, scope, 'latin', {
      key: 'singulars',
      scope: { cells: 'number=sg' },
    });
    expect(created.key).toBe('singulars');
    expect(await service.list('latin')).toHaveLength(1);
  });

  it('refuses a deck that selects nothing', async () => {
    const { service } = await build();
    await expect(
      service.create(teacher, scope, 'latin', {
        key: 'empty',
        scope: { cells: 'case=nom,case!=nom' },
      }),
    ).rejects.toThrow(ConflictError);
    expect(await service.list('latin')).toEqual([]);
  });

  it('refuses a key that is already taken', async () => {
    const { service } = await build();
    await service.create(teacher, scope, 'latin', { key: 'all' });
    await expect(service.create(teacher, scope, 'latin', { key: 'all' })).rejects.toThrow(
      ConflictError,
    );
  });

  it('refuses a learner', async () => {
    const { service } = await build();
    await expect(service.create(student, scope, 'latin', { key: 'all' })).rejects.toThrow(
      ForbiddenError,
    );
  });

  it('refuses a language nobody configured', async () => {
    const { service } = await build();
    await expect(service.create(teacher, scope, 'greek', { key: 'all' })).rejects.toThrow(
      NotFoundError,
    );
  });
});

describe('get and list', () => {
  it('reads a deck back and refuses one that is not there', async () => {
    const { service } = await build();
    await service.create(teacher, scope, 'latin', { key: 'all' });
    expect((await service.get('latin', 'all')).key).toBe('all');
    await expect(service.get('latin', 'nowhere')).rejects.toThrow(NotFoundError);
  });

  it('lists by language', async () => {
    const { service } = await build();
    await service.create(teacher, scope, 'latin', { key: 'all' });
    expect(await service.list('greek')).toEqual([]);
  });
});

describe('rescope', () => {
  it('narrows a deck and keeps it saved', async () => {
    const { service } = await build();
    await service.create(teacher, scope, 'latin', { key: 'all' });
    const narrowed = await service.rescope(teacher, scope, 'latin', 'all', {
      cells: 'number=pl',
    });
    expect(formatSelector(narrowed.scope.cells)).toBe('number=pl');
    expect(formatSelector((await service.get('latin', 'all')).scope.cells)).toBe('number=pl');
  });

  it('refuses a change that empties the deck', async () => {
    const { service } = await build();
    await service.create(teacher, scope, 'latin', { key: 'all' });
    await expect(
      service.rescope(teacher, scope, 'latin', 'all', { cells: 'case=nom,case!=nom' }),
    ).rejects.toThrow(ConflictError);
    expect(formatSelector((await service.get('latin', 'all')).scope.cells)).toBe('');
  });

  it('refuses a deck that is not there', async () => {
    const { service } = await build();
    await expect(
      service.rescope(teacher, scope, 'latin', 'nowhere', { cells: 'number=pl' }),
    ).rejects.toThrow(NotFoundError);
  });

  it('refuses a learner', async () => {
    const { service } = await build();
    await service.create(teacher, scope, 'latin', { key: 'all' });
    await expect(
      service.rescope(student, scope, 'latin', 'all', { cells: 'number=pl' }),
    ).rejects.toThrow(ForbiddenError);
  });
});

describe('remove', () => {
  it('removes a deck and refuses one that is not there', async () => {
    const { service } = await build();
    await service.create(teacher, scope, 'latin', { key: 'all' });
    await service.remove(teacher, scope, 'latin', 'all');
    expect(await service.list('latin')).toEqual([]);
    await expect(service.remove(teacher, scope, 'latin', 'all')).rejects.toThrow(NotFoundError);
  });

  it('refuses a learner', async () => {
    const { service } = await build();
    await service.create(teacher, scope, 'latin', { key: 'all' });
    await expect(service.remove(student, scope, 'latin', 'all')).rejects.toThrow(ForbiddenError);
  });
});

describe('preview', () => {
  it('reports what a deck would select without saving it', async () => {
    const { service } = await build();
    const preview = await service.preview('latin', { key: 'draft' }, d('2024-10-21'));
    expect(preview.membership.cells).toHaveLength(30);
    expect(await service.list('latin')).toEqual([]);
  });

  it('carries the warnings the membership produced', async () => {
    const { service } = await build();
    const preview = await service.preview('latin', { key: 'draft' }, d('2024-10-21'));
    expect(preview.warnings).toContain('1 lexemes have no paradigm bound to their class');
  });

  it('reports an empty selection as a warning rather than a failure', async () => {
    const { service } = await build();
    const preview = await service.preview(
      'latin',
      { key: 'draft', scope: { cells: 'case=nom,case!=nom' } },
      d('2024-10-21'),
    );
    expect(preview.membership.cells).toEqual([]);
    expect(preview.warnings).toContain('the deck selects nothing at all');
  });

  it('takes the day off the clock when none is given', async () => {
    const { service } = await build();
    expect((await service.preview('latin', { key: 'draft' })).membership.on).toBe('2024-10-21');
  });
});
