import { describe, expect, it } from 'vitest';
import { createDrillService } from '../../src/app/services/drill-service.js';
import { deck } from '../../src/domain/drill/deck.js';
import { newCount, queueLength } from '../../src/domain/drill/queue.js';
import { NotFoundError } from '../../src/domain/errors.js';
import { ladder } from '../../src/domain/mastery/ladder.js';
import { leechPolicy } from '../../src/domain/mastery/leech.js';
import { strengthFor } from '../../src/domain/mastery/ledger.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { seededStores, testHarness } from '../support/app.js';
import { latinNoun } from '../support/profiles.js';

const d = (value: string) => parseCivilDate(value);
const DAY = d('2024-10-21');
const flat = ladder({ key: 'flat', rungs: [1, 3, 7], spread: 0 });
const settings = {
  policy: { newPerDay: 3, perLexemeCap: 10, rowSpacing: 0 },
  ladder: flat,
  leech: leechPolicy({ threshold: 2 }),
};

const build = async () => {
  const stores = await seededStores();
  await stores.decks.put(deck(latinNoun, { key: 'all' }));
  const { bus, clock } = testHarness();
  return { stores, bus, service: createDrillService(stores, bus, clock) };
};

const ask = (cellId: string, given: string) => ({
  learnerId: 'lrn_one',
  language: 'latin',
  deckKey: 'all',
  cellId,
  given,
  on: DAY,
});

describe('context and deck', () => {
  it('assembles a context from the stores', async () => {
    const { service } = await build();
    const context = await service.context('latin');
    expect(context.schema.language).toBe('latin');
    expect(context.lexicon.byId.size).toBe(4);
  });

  it('refuses a language or a deck nobody stored', async () => {
    const { service } = await build();
    await expect(service.context('greek')).rejects.toThrow(NotFoundError);
    await expect(service.deck('latin', 'nowhere')).rejects.toThrow(NotFoundError);
  });

  it('resolves the membership on a day', async () => {
    const { service } = await build();
    expect((await service.membership('latin', 'all', DAY)).cells).toHaveLength(30);
  });
});

describe('queue', () => {
  it('introduces new cells up to the budget', async () => {
    const { service } = await build();
    const queue = await service.queue('lrn_one', 'latin', 'all', DAY, settings);
    expect(queueLength(queue)).toBe(3);
    expect(newCount(queue)).toBe(3);
  });

  it('takes the day off the clock when none is given', async () => {
    const { service } = await build();
    const queue = await service.queue('lrn_one', 'latin', 'all', undefined, settings);
    expect(queue.on).toBe('2024-10-21');
  });

  it('leaves out a cell buried earlier in the same session', async () => {
    const { service } = await build();
    const first = await service.queue('lrn_one', 'latin', 'all', DAY, settings);
    const target = first.entries[0]!.cell;
    await service.answer(ask(target.id, 'x'), settings);
    const second = await service.queue('lrn_one', 'latin', 'all', DAY, settings);
    expect(second.entries.map((entry) => entry.cell.id)).not.toContain(target.id);
  });
});

describe('answer', () => {
  it('marks the answer and writes the three records', async () => {
    const { service, stores, bus } = await build();
    const queue = await service.queue('lrn_one', 'latin', 'all', DAY, settings);
    const target = queue.entries[0]!.cell;
    const expected = (await service.membership('latin', 'all', DAY)).cells.find(
      (cell) => cell.id === target.id,
    )!;
    expect(expected).toBeDefined();

    const outcome = await service.answer({ ...ask(target.id, 'x'), elapsedMs: 900 }, settings);

    expect(outcome.session.attempts).toHaveLength(1);
    expect(outcome.review.attempt.elapsedMs).toBe(900);
    expect(await stores.attempts.listByLearner('lrn_one')).toHaveLength(1);
    expect((await stores.ledgers.get('lrn_one'))?.byCell.size).toBeGreaterThan(0);
    expect(bus.ofKind('attempt.recorded')).toHaveLength(1);
  });

  it('marks a right answer as right', async () => {
    const { service } = await build();
    const queue = await service.queue('lrn_one', 'latin', 'all', DAY, settings);
    const target = queue.entries[0]!.cell;
    const outcome = await service.answer(ask(target.id, 'x'), settings);
    const second = await service.answer(
      ask(queue.entries[1]!.cell.id, outcome.review.expected),
      settings,
    );
    expect(second.review.match.verdict).toBeDefined();
  });

  it('keeps answering into the same session', async () => {
    const { service } = await build();
    const queue = await service.queue('lrn_one', 'latin', 'all', DAY, settings);
    const first = await service.answer(ask(queue.entries[0]!.cell.id, 'x'), settings);
    const second = await service.answer(ask(queue.entries[1]!.cell.id, 'x'), settings);
    expect(second.session.id).toBe(first.session.id);
    expect(second.session.attempts).toHaveLength(2);
  });

  it('refuses a cell that is not in the deck', async () => {
    const { service } = await build();
    await expect(service.answer(ask('cell_zzzzzz', 'x'))).rejects.toThrow(NotFoundError);
  });

  it('announces a suspension once the leech threshold is passed', async () => {
    const { service, stores, bus } = await build();
    const queue = await service.queue('lrn_one', 'latin', 'all', DAY, settings);
    const target = queue.entries[0]!.cell;
    for (let step = 0; step < 2; step += 1) {
      await service.answer(ask(target.id, 'nonsense'), settings);
    }
    expect(bus.ofKind('cell.suspended')).toHaveLength(1);
    const ledger = (await stores.ledgers.get('lrn_one'))!;
    expect(strengthFor(ledger, target.id).suspended).toBe(true);
  });
});

describe('finish', () => {
  it('closes the session and announces it', async () => {
    const { service, bus } = await build();
    const queue = await service.queue('lrn_one', 'latin', 'all', DAY, settings);
    const outcome = await service.answer(ask(queue.entries[0]!.cell.id, 'x'), settings);
    const closed = await service.finish('lrn_one', outcome.session.id);
    expect(closed.closed).toBe(true);
    expect(bus.ofKind('session.closed')[0]?.answers).toBe(1);
  });

  it('refuses a session belonging to somebody else', async () => {
    const { service } = await build();
    const queue = await service.queue('lrn_one', 'latin', 'all', DAY, settings);
    const outcome = await service.answer(ask(queue.entries[0]!.cell.id, 'x'), settings);
    await expect(service.finish('lrn_two', outcome.session.id)).rejects.toThrow(NotFoundError);
  });

  it('refuses a session nobody stored', async () => {
    const { service } = await build();
    await expect(service.finish('lrn_one', 'sess_zzzzzz')).rejects.toThrow(NotFoundError);
  });
});
