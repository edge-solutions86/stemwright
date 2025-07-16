import { describe, expect, it } from 'vitest';
import { createApplication } from '../../src/app/application.js';
import { recordingBus } from '../../src/app/events.js';
import { steppableClock } from '../../src/app/ports.js';
import { deck } from '../../src/domain/drill/deck.js';
import { ladder } from '../../src/domain/mastery/ladder.js';
import { leechPolicy } from '../../src/domain/mastery/leech.js';
import { strengthFor } from '../../src/domain/mastery/ledger.js';
import { isDue } from '../../src/domain/mastery/strength.js';
import { addDays, parseCivilDate } from '../../src/domain/time/civil.js';
import type { CivilDate } from '../../src/domain/time/civil.js';
import { seeded } from '../support/random.js';
import { seededStores } from '../support/app.js';
import { latinNoun } from '../support/profiles.js';

/**
 * A whole term, driven through the application the way a client would.
 *
 * This is the test that would catch a change nobody thought was risky. Every
 * unit test below it holds one module to its contract; this one holds the
 * arrangement of them to the facts that have to be true after real use, and
 * those facts are the ones an operator would notice were broken.
 */
const START = parseCivilDate('2024-10-07');
const DAYS = 40;
const flat = ladder({ key: 'flat', rungs: [1, 2, 4, 9, 20], spread: 0 });
const settings = {
  policy: { newPerDay: 6, reviewsPerDay: 40, perLexemeCap: 3, rowSpacing: 1 },
  ladder: flat,
  leech: leechPolicy({ threshold: 4 }),
};

async function runTerm() {
  const stores = await seededStores();
  await stores.decks.put(deck(latinNoun, { key: 'all' }));

  const clock = steppableClock(new Date('2024-10-07T09:00:00Z'), START);
  const bus = recordingBus();
  const app = createApplication(stores, { bus, clock });
  const random = seeded(4021);

  const sessionsSeen: string[] = [];
  let day: CivilDate = START;

  for (let index = 0; index < DAYS; index += 1) {
    clock.set(new Date(`${day}T09:00:00Z`), day);
    const queue = await app.drill.queue('lrn_one', 'latin', 'all', day, settings);

    let sessionId: string | undefined;
    for (const entry of queue.entries) {
      const expected = (
        await app.drill.answer(
          {
            learnerId: 'lrn_one',
            language: 'latin',
            deckKey: 'all',
            cellId: entry.cell.id,
            given: 'deliberately wrong',
            on: day,
          },
          settings,
        )
      ).review;
      sessionId = expected.attempt.sessionId;

      if (random.bool(0.7)) {
        await app.drill.answer(
          {
            learnerId: 'lrn_one',
            language: 'latin',
            deckKey: 'all',
            cellId: entry.cell.id,
            given: expected.expected,
            on: day,
          },
          settings,
        );
      }
    }

    if (sessionId !== undefined) {
      await app.drill.finish('lrn_one', sessionId);
      sessionsSeen.push(sessionId);
    }
    day = addDays(day, 1);
  }

  return { app, stores, bus, sessionsSeen, lastDay: day };
}

describe('a term of drilling', () => {
  it('records an attempt for every answer and no more', async () => {
    const { stores, bus } = await runTerm();
    const attempts = await stores.attempts.listByLearner('lrn_one');
    expect(attempts.length).toBeGreaterThan(100);
    expect(bus.ofKind('attempt.recorded')).toHaveLength(attempts.length);
  });

  it('numbers the attempts of each session from one with no gaps', async () => {
    const { stores, sessionsSeen } = await runTerm();
    for (const sessionId of sessionsSeen) {
      const attempts = await stores.attempts.listBySession(sessionId);
      expect(attempts.map((entry) => entry.sequence)).toEqual(
        attempts.map((_, index) => index + 1),
      );
    }
  });

  it('closes every session it opened', async () => {
    const { stores, sessionsSeen } = await runTerm();
    expect(await stores.sessions.listOpen('lrn_one')).toEqual([]);
    expect(sessionsSeen.length).toBeGreaterThan(10);
  });

  it('leaves every seen cell with a due date and a rung on the ladder', async () => {
    const { stores } = await runTerm();
    const ledger = (await stores.ledgers.get('lrn_one'))!;
    for (const strength of ledger.byCell.values()) {
      if (strength.seen === 0) {
        continue;
      }
      expect(strength.dueOn).toBeDefined();
      expect(strength.rung).toBeGreaterThanOrEqual(0);
      expect(strength.rung).toBeLessThan(flat.rungs.length);
    }
  });

  it('never records more correct answers than sightings on any cell', async () => {
    const { stores } = await runTerm();
    const ledger = (await stores.ledgers.get('lrn_one'))!;
    for (const strength of ledger.byCell.values()) {
      expect(strength.correct).toBeLessThanOrEqual(strength.seen);
    }
  });

  it('agrees with the attempt log about how many times each cell was seen', async () => {
    const { stores } = await runTerm();
    const ledger = (await stores.ledgers.get('lrn_one'))!;
    const attempts = await stores.attempts.listByLearner('lrn_one');

    const counted = new Map<string, number>();
    for (const entry of attempts) {
      counted.set(entry.cellId, (counted.get(entry.cellId) ?? 0) + 1);
    }
    for (const [cellId, count] of counted) {
      expect(strengthFor(ledger, cellId).seen).toBe(count);
    }
  });

  it('suspends a cell only after it has lapsed past the threshold', async () => {
    const { stores } = await runTerm();
    const ledger = (await stores.ledgers.get('lrn_one'))!;
    for (const strength of ledger.byCell.values()) {
      if (strength.suspended) {
        expect(strength.lapses).toBeGreaterThanOrEqual(4);
      }
    }
  });

  it('leaves no suspended cell in the due set on the day after the term', async () => {
    const { stores, lastDay } = await runTerm();
    const ledger = (await stores.ledgers.get('lrn_one'))!;
    for (const strength of ledger.byCell.values()) {
      if (strength.suspended) {
        expect(isDue(strength, lastDay)).toBe(false);
      }
    }
  });

  it('introduces new cells at the budget and no faster', async () => {
    const { stores } = await runTerm();
    const attempts = await stores.attempts.listByLearner('lrn_one');
    const firstSeen = new Map<string, string>();
    for (const entry of attempts) {
      if (!firstSeen.has(entry.cellId)) {
        firstSeen.set(entry.cellId, entry.on);
      }
    }
    const perDay = new Map<string, number>();
    for (const day of firstSeen.values()) {
      perDay.set(day, (perDay.get(day) ?? 0) + 1);
    }
    for (const count of perDay.values()) {
      expect(count).toBeLessThanOrEqual(settings.policy.newPerDay);
    }
  });
});
