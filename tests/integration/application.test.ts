import { describe, expect, it } from 'vitest';
import { attachLogging, createApplication } from '../../src/app/application.js';
import { recordingBus } from '../../src/app/events.js';
import { fixedClock } from '../../src/app/ports.js';
import type { Actor } from '../../src/domain/course/authz.js';
import { roster } from '../../src/domain/course/enrolment.js';
import { deck } from '../../src/domain/drill/deck.js';
import { ladder } from '../../src/domain/mastery/ladder.js';
import { leechPolicy } from '../../src/domain/mastery/leech.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { seededStores } from '../support/app.js';
import { michaelmas } from '../support/course.js';
import { latinNoun } from '../support/profiles.js';

const d = (value: string) => parseCivilDate(value);
const DAY = d('2024-10-21');
const teacher: Actor = { id: 'lrn_teacher', role: 'instructor' };
const flat = ladder({ key: 'flat', rungs: [1, 3], spread: 0 });

const build = async () => {
  const stores = await seededStores();
  await stores.decks.put(deck(latinNoun, { key: 'all' }));
  await stores.courses.put(michaelmas);
  const bus = recordingBus();
  const clock = fixedClock(new Date('2024-10-21T09:00:00Z'), DAY);
  return { stores, bus, app: createApplication(stores, { bus, clock }) };
};

type App = Awaited<ReturnType<typeof build>>['app'];

const answerFirst = async (app: App, given: string, settings: object = { ladder: flat }) => {
  const queue = await app.drill.queue('lrn_one', 'latin', 'all', DAY, { ladder: flat });
  return app.drill.answer(
    {
      learnerId: 'lrn_one',
      language: 'latin',
      deckKey: 'all',
      cellId: queue.entries[0]!.cell.id,
      given,
      on: DAY,
    },
    settings,
  );
};

describe('createApplication', () => {
  it('builds every service', async () => {
    const { app } = await build();
    expect(app.lexicon).toBeDefined();
    expect(app.profiles).toBeDefined();
    expect(app.decks).toBeDefined();
    expect(app.drill).toBeDefined();
    expect(app.courses).toBeDefined();
    expect(app.reports).toBeDefined();
  });

  it('gives every service the same clock', async () => {
    const { app } = await build();
    expect(app.clock.today()).toBe('2024-10-21');
    expect((await app.drill.queue('lrn_one', 'latin', 'all')).on).toBe('2024-10-21');
  });

  it('gives every service the same bus', async () => {
    const { app, bus } = await build();
    await answerFirst(app, 'x');
    expect(bus.ofKind('attempt.recorded')).toHaveLength(1);
  });

  it('takes a system clock when none is given', async () => {
    const stores = await seededStores();
    expect(createApplication(stores).clock.today()).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it('lets the services reach one another', async () => {
    const { app } = await build();
    const scope = { course: michaelmas, roster: roster(michaelmas, []) };
    const preview = await app.decks.preview('latin', { key: 'draft' }, DAY);
    expect(preview.membership.cells.length).toBeGreaterThan(0);
    await expect(app.decks.create(teacher, scope, 'latin', { key: 'new' })).resolves.toBeDefined();
  });
});

describe('attachLogging', () => {
  it('writes a line when a cell is suspended', async () => {
    const { app } = await build();
    const lines: string[] = [];
    attachLogging(app, (line) => lines.push(line));
    await answerFirst(app, 'nope', { ladder: flat, leech: leechPolicy({ threshold: 1 }) });
    expect(lines.some((line) => line.startsWith('suspended '))).toBe(true);
  });

  it('writes a line when a session closes', async () => {
    const { app } = await build();
    const lines: string[] = [];
    attachLogging(app, (line) => lines.push(line));
    const outcome = await answerFirst(app, 'x');
    await app.drill.finish('lrn_one', outcome.session.id);
    expect(lines.some((line) => line.includes('closed with 1 answers'))).toBe(true);
  });

  it('stops writing once detached', async () => {
    const { app } = await build();
    const lines: string[] = [];
    const detach = attachLogging(app, (line) => lines.push(line));
    detach();
    const outcome = await answerFirst(app, 'x');
    await app.drill.finish('lrn_one', outcome.session.id);
    expect(lines).toEqual([]);
  });
});
