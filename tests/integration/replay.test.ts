import { describe, expect, it } from 'vitest';
import { fixedClock } from '../../src/app/ports.js';
import { attempt } from '../../src/domain/mastery/attempt.js';
import type { AttemptInput } from '../../src/domain/mastery/attempt.js';
import { ladder } from '../../src/domain/mastery/ladder.js';
import { buildLedger, strengthFor } from '../../src/domain/mastery/ledger.js';
import { newStrength, recordCorrect } from '../../src/domain/mastery/strength.js';
import { IntegrityError } from '../../src/domain/errors.js';
import { openSession } from '../../src/domain/drill/session.js';
import { memoryStores } from '../../src/infra/memory.js';
import { memoryJournal } from '../../src/infra/journal.js';
import {
  assertContiguous,
  inspect,
  journalled,
  replay,
  replayFrom,
} from '../../src/infra/replay.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';

const d = (value: string) => parseCivilDate(value);
const DAY = d('2025-03-03');
const clock = fixedClock(new Date('2025-03-03T09:00:00Z'), DAY);
const flat = ladder({ key: 'flat', rungs: [1, 3], spread: 0 });

const base: AttemptInput = {
  learnerId: 'lrn_one',
  sessionId: 'sess_aaaaaa',
  cellId: 'cell_aaaaaa',
  deckKey: 'first-year',
  on: DAY,
  sequence: 1,
  given: 'puellae',
  expected: 'puellae',
  verdict: 'exact',
  toleranceLevel: 'standard',
  revisionId: 'rev_aaaaaa',
};

const build = () => {
  const journal = memoryJournal();
  const stores = journalled(memoryStores(), journal, clock);
  return { journal, stores };
};

describe('journalled', () => {
  it('writes an attempt to the journal and to the store', async () => {
    const { journal, stores } = build();
    await stores.attempts.append([attempt(base)]);
    expect(await journal.size()).toBe(1);
    expect(await stores.attempts.listByLearner('lrn_one')).toHaveLength(1);
  });

  it('writes one journal entry per attempt', async () => {
    const { journal, stores } = build();
    await stores.attempts.append([attempt(base), attempt({ ...base, sequence: 2 })]);
    expect(await journal.size()).toBe(2);
  });

  it('writes nothing for an empty append', async () => {
    const { journal, stores } = build();
    await stores.attempts.append([]);
    expect(await journal.size()).toBe(0);
  });

  it('writes a ledger to the journal', async () => {
    const { journal, stores } = build();
    await stores.ledgers.put(buildLedger('lrn_one', [newStrength('cell_a')]));
    expect(await journal.size()).toBe(1);
    expect((await journal.read())[0]?.kind).toBe('ledger.put');
  });

  it('leaves the other stores unwrapped', async () => {
    const { journal, stores } = build();
    const session = openSession('lrn_one', 'first-year', DAY);
    await stores.sessions.put(session);
    expect(await journal.size()).toBe(0);
    expect(await stores.sessions.get(session.id)).toBeDefined();
  });
});

describe('replay', () => {
  it('rebuilds attempts and ledgers into fresh stores', async () => {
    const { journal, stores } = build();
    await stores.attempts.append([attempt(base)]);
    await stores.ledgers.put(
      buildLedger('lrn_one', [recordCorrect(newStrength('cell_aaaaaa'), flat, DAY)]),
    );

    const rebuilt = memoryStores();
    const report = await replayFrom(journal, rebuilt);

    expect(report.applied).toBe(2);
    expect(report.skipped).toBe(0);
    expect(report.lastSeq).toBe(2);
    expect(await rebuilt.attempts.listByLearner('lrn_one')).toHaveLength(1);
    expect(strengthFor((await rebuilt.ledgers.get('lrn_one'))!, 'cell_aaaaaa').seen).toBe(1);
  });

  it('applies entries in sequence order however they arrive', async () => {
    const entries = [
      {
        seq: 2,
        at: '2025-03-03T09:00:00.000Z',
        kind: 'ledger.put' as const,
        payload: { learnerId: 'lrn_one', cells: [] },
      },
      {
        seq: 1,
        at: '2025-03-03T09:00:00.000Z',
        kind: 'attempt.appended' as const,
        payload: JSON.parse(JSON.stringify({ ...base, on: '2025-03-03' })),
      },
    ];
    const rebuilt = memoryStores();
    expect((await replay(entries, rebuilt)).applied).toBe(2);
  });

  it('skips a kind this build does not handle', async () => {
    const entries = [
      { seq: 1, at: '2025-03-03T09:00:00.000Z', kind: 'deck.put' as const, payload: {} },
    ];
    const report = await replay(entries, memoryStores());
    expect(report.applied).toBe(0);
    expect(report.skipped).toBe(1);
  });

  it('stops on a malformed entry rather than skipping it', async () => {
    const entries = [
      {
        seq: 1,
        at: '2025-03-03T09:00:00.000Z',
        kind: 'attempt.appended' as const,
        payload: { learnerId: 'lrn_one' },
      },
    ];
    await expect(replay(entries, memoryStores())).rejects.toThrow(/journal.1/);
  });

  it('reports nothing for an empty journal', async () => {
    expect(await replay([], memoryStores())).toEqual({ applied: 0, skipped: 0, lastSeq: 0 });
  });
});

describe('inspect', () => {
  it('reads an attempt entry out', async () => {
    const { journal, stores } = build();
    await stores.attempts.append([attempt(base)]);
    expect(inspect(await journal.read())[0]).toBe(
      '1 2025-03-03T09:00:00.000Z attempt cell_aaaaaa by lrn_one',
    );
  });

  it('reads a ledger entry out', async () => {
    const { journal, stores } = build();
    await stores.ledgers.put(buildLedger('lrn_one', []));
    expect(inspect(await journal.read())[0]).toBe('1 2025-03-03T09:00:00.000Z ledger for lrn_one');
  });

  it('names the kind of anything else', () => {
    const entries = [
      { seq: 1, at: '2025-03-03T09:00:00.000Z', kind: 'deck.put' as const, payload: {} },
    ];
    expect(inspect(entries)[0]).toBe('1 2025-03-03T09:00:00.000Z deck.put');
  });
});

describe('assertContiguous', () => {
  it('passes on a journal that starts at one and does not skip', async () => {
    const { journal, stores } = build();
    await stores.attempts.append([attempt(base), attempt({ ...base, sequence: 2 })]);
    const entries = await journal.read();
    expect(() => assertContiguous(entries)).not.toThrow();
  });

  it('names the position of the gap', () => {
    const entries = [
      { seq: 1, at: 'x', kind: 'ledger.put' as const, payload: {} },
      { seq: 3, at: 'x', kind: 'ledger.put' as const, payload: {} },
    ];
    expect(() => assertContiguous(entries)).toThrow(IntegrityError);
    expect(() => assertContiguous(entries)).toThrow(/position 2, found sequence 3/);
  });

  it('passes on an empty journal', () => {
    expect(() => assertContiguous([])).not.toThrow();
  });
});
