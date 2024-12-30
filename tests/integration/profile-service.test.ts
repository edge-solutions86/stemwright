import { describe, expect, it } from 'vitest';
import { createProfileService } from '../../src/app/services/profile-service.js';
import type { Actor } from '../../src/domain/course/authz.js';
import {
  ConflictError,
  ForbiddenError,
  NotFoundError,
  ValidationError,
} from '../../src/domain/errors.js';
import { paradigmProfile, withSlot } from '../../src/domain/paradigm/profile.js';
import { syncretismMap } from '../../src/domain/paradigm/syncretism.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { dayRange } from '../../src/domain/time/window.js';
import { seededStores, testHarness } from '../support/app.js';
import { firstDeclension, latinNoun } from '../support/profiles.js';

const d = (value: string) => parseCivilDate(value);
const teacher: Actor = { id: 'lrn_teacher', role: 'instructor' };
const student: Actor = { id: 'lrn_one', role: 'learner' };

const build = async () => {
  const stores = await seededStores();
  const { bus, clock } = testHarness();
  return { stores, bus, service: createProfileService(stores, bus, clock) };
};

const revised = withSlot(latinNoun, firstDeclension, { bundle: 'gen.sg', affix: 'ai' });

describe('book and history', () => {
  it('loads the seeded book', async () => {
    const { service } = await build();
    expect((await service.book('latin')).histories.size).toBe(2);
  });

  it('refuses a language with no book', async () => {
    const { service } = await build();
    await expect(service.book('greek')).rejects.toThrow(NotFoundError);
  });

  it('loads one history and refuses an unknown key', async () => {
    const { service } = await build();
    expect((await service.history('latin', 'first-declension')).revisions).toHaveLength(1);
    await expect(service.history('latin', 'nowhere')).rejects.toThrow(NotFoundError);
  });
});

describe('resolve', () => {
  it('finds the revision in force through the class chain', async () => {
    const { service } = await build();
    const resolved = await service.resolve('latin', 'second-declension.neuter', d('2024-10-21'));
    expect(resolved.revision.ordinal).toBe(1);
    expect(resolved.boundThrough).toBe('second-declension');
  });

  it('refuses a class with no binding', async () => {
    const { service } = await build();
    await expect(service.resolve('latin', 'third-declension', d('2024-10-21'))).rejects.toThrow(
      NotFoundError,
    );
  });
});

describe('publish', () => {
  it('adds a revision and announces it', async () => {
    const { service, bus } = await build();
    const published = await service.publish(
      teacher,
      'latin',
      revised,
      d('2025-01-15'),
      undefined,
      'the archaic genitive was added',
    );
    expect(published.ordinal).toBe(2);
    expect((await service.history('latin', 'first-declension')).revisions).toHaveLength(2);
    expect(bus.ofKind('profile.published')[0]?.effectiveFrom).toBe('2025-01-15');
  });

  it('publishes a profile the book did not hold at all', async () => {
    const { service } = await build();
    const fresh = paradigmProfile(latinNoun, {
      key: 'third-declension',
      classKey: 'third-declension',
      slots: [{ bundle: 'nom.sg', affix: '' }],
    });
    await service.publish(teacher, 'latin', fresh, d('2025-01-15'));
    expect((await service.book('latin')).histories.size).toBe(3);
  });

  it('refuses a revision that would take effect in the past', async () => {
    const { service } = await build();
    await expect(service.publish(teacher, 'latin', revised, d('2024-10-01'))).rejects.toThrow(
      ConflictError,
    );
  });

  it('accepts a revision taking effect today', async () => {
    const { service } = await build();
    await expect(
      service.publish(teacher, 'latin', revised, d('2024-10-21')),
    ).resolves.toBeDefined();
  });

  it('refuses a learner', async () => {
    const { service, bus } = await build();
    await expect(service.publish(student, 'latin', revised, d('2025-01-15'))).rejects.toThrow(
      ForbiddenError,
    );
    expect(bus.events).toEqual([]);
  });

  it('refuses a start day another revision already holds', async () => {
    const { service } = await build();
    await service.publish(teacher, 'latin', revised, d('2025-01-15'));
    await expect(service.publish(teacher, 'latin', revised, d('2025-01-15'))).rejects.toThrow(
      ValidationError,
    );
  });

  it('carries a syncretism map onto the revision', async () => {
    const { service } = await build();
    const map = syncretismMap(latinNoun, [{ key: 'is', members: ['dat.pl', 'abl.pl'] }]);
    const published = await service.publish(teacher, 'latin', revised, d('2025-01-15'), map);
    expect(published.syncretism.groups).toHaveLength(1);
  });
});

describe('bind', () => {
  it('binds a class and closes the gap', async () => {
    const { service } = await build();
    expect(await service.gaps('latin')).toContain('third-declension');
    await service.bind(teacher, 'latin', 'third-declension', 'first-declension');
    expect(await service.gaps('latin')).not.toContain('third-declension');
  });

  it('refuses a learner', async () => {
    const { service } = await build();
    await expect(
      service.bind(student, 'latin', 'third-declension', 'first-declension'),
    ).rejects.toThrow(ForbiddenError);
  });
});

describe('disagreements', () => {
  it('reports a pair the endings produce that nobody declared', async () => {
    const { service } = await build();
    const found = await service.disagreements('latin', 'second-neuter');
    expect(found.length).toBeGreaterThan(0);
    expect(found[0]).toMatch(/produced but not declared/);
  });

  it('reports a pair the declaration missed even where most of it is right', async () => {
    const { service } = await build();
    expect(await service.disagreements('latin', 'first-declension')).toEqual([
      'case=nom;number=sg and case=abl;number=sg are produced but not declared',
    ]);
  });
});

describe('revisionsIn', () => {
  it('names one revision for a range inside it', async () => {
    const { service } = await build();
    const range = dayRange(d('2024-10-01'), d('2024-10-31'));
    expect(await service.revisionsIn('latin', 'second-neuter', range)).toHaveLength(1);
  });

  it('names both when the range crosses the change', async () => {
    const { service } = await build();
    const range = dayRange(d('2025-01-25'), d('2025-02-05'));
    expect(await service.revisionsIn('latin', 'second-neuter', range)).toHaveLength(2);
  });
});
