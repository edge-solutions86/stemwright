import { mkdtemp } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { fixedClock } from '../../src/app/ports.js';
import { attempt } from '../../src/domain/mastery/attempt.js';
import type { AttemptInput } from '../../src/domain/mastery/attempt.js';
import { ladder } from '../../src/domain/mastery/ladder.js';
import { buildLedger, strengthFor } from '../../src/domain/mastery/ledger.js';
import { newStrength, recordCorrect, recordLapse } from '../../src/domain/mastery/strength.js';
import { addDays, parseCivilDate } from '../../src/domain/time/civil.js';
import { dataLayout, fileJournal, removeIfPresent } from '../../src/infra/files.js';
import { decodeJournal, encodeJournal } from '../../src/infra/journal.js';
import { memoryStores } from '../../src/infra/memory.js';
import { assertContiguous, journalled, replay } from '../../src/infra/replay.js';
import { bootstrap, buildSnapshot, writeSnapshot } from '../../src/infra/snapshot.js';
import { seeded } from '../support/random.js';

/**
 * The journal has one job and one property: whatever is replayed out of it is
 * what was put into it. Everything the store does above it rests on that, and
 * a single ordering or encoding mistake would be invisible until somebody
 * restarted a process that had been up for a term.
 */
const DAY = parseCivilDate('2025-03-03');
const AT = new Date('2025-03-03T09:00:00Z');
const clock = fixedClock(AT, DAY);
const flat = ladder({ key: 'flat', rungs: [1, 3, 7], spread: 0 });

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

const roots: string[] = [];
const freshRoot = async () => {
  const root = await mkdtemp(join(tmpdir(), 'stemwright-journal-'));
  roots.push(root);
  return root;
};

afterEach(async () => {
  while (roots.length > 0) {
    await removeIfPresent(roots.pop()!);
  }
});

async function busyStore(writes: number) {
  const layout = dataLayout(await freshRoot());
  const journal = await fileJournal(layout);
  const stores = journalled(memoryStores(), journal, clock);
  const random = seeded(7331);

  let strength = newStrength('cell_aaaaaa');
  let day = DAY;

  for (let index = 0; index < writes; index += 1) {
    await stores.attempts.append([
      attempt({ ...base, sequence: index + 1, on: day, given: random.word() }),
    ]);
    strength = random.bool(0.6)
      ? recordCorrect(strength, flat, day)
      : recordLapse(strength, flat, day);
    await stores.ledgers.put(buildLedger('lrn_one', [strength]));
    day = addDays(day, random.int(0, 3));
  }

  return { layout, journal, stores, strength };
}

describe('the journal after real use', () => {
  it('numbers every entry from one with no gaps', async () => {
    const { journal } = await busyStore(30);
    const entries = await journal.read();
    expect(entries).toHaveLength(60);
    expect(() => assertContiguous(entries)).not.toThrow();
  });

  it('round trips through its own encoding byte for byte', async () => {
    const { journal } = await busyStore(20);
    const entries = await journal.read();
    const text = encodeJournal(entries);
    expect(encodeJournal(decodeJournal(text))).toBe(text);
  });

  it('replays into a state that matches the live one', async () => {
    const { journal, stores, strength } = await busyStore(25);
    const rebuilt = memoryStores();
    await replay(await journal.read(), rebuilt);

    const live = await stores.attempts.listByLearner('lrn_one');
    const copied = await rebuilt.attempts.listByLearner('lrn_one');
    expect(copied).toHaveLength(live.length);
    expect(strengthFor((await rebuilt.ledgers.get('lrn_one'))!, 'cell_aaaaaa')).toEqual(strength);
  });

  /**
   * The snapshot is taken at the journal position the store is actually at.
   * Labelling a snapshot with a sequence lower than the state it captured makes
   * the replay apply those entries a second time, which is why `compact` reads
   * `lastSeq` itself rather than taking a number from its caller.
   */
  it('gives the same state whether or not a snapshot was taken', async () => {
    const { layout, journal, stores } = await busyStore(20);
    const at = await journal.lastSeq();
    await writeSnapshot(layout, await buildSnapshot(stores, ['lrn_one'], at, AT));

    const withSnapshot = memoryStores();
    await bootstrap(layout, journal, withSnapshot);

    const plain = memoryStores();
    await replay(await journal.read(), plain);

    const left = await withSnapshot.attempts.listByLearner('lrn_one');
    const right = await plain.attempts.listByLearner('lrn_one');
    expect(left.map((entry) => entry.id)).toEqual(right.map((entry) => entry.id));
    expect(await withSnapshot.ledgers.get('lrn_one')).toEqual(await plain.ledgers.get('lrn_one'));
  });

  it('survives being reopened from disk', async () => {
    const { layout, journal } = await busyStore(15);
    const reopened = await fileJournal(layout);
    expect(await reopened.size()).toBe(await journal.size());
    expect(await reopened.read()).toEqual(await journal.read());
  });

  it('carries on numbering after a reopen', async () => {
    const { layout, journal } = await busyStore(10);
    const before = await journal.lastSeq();
    const reopened = await fileJournal(layout);
    const written = await reopened.append([{ kind: 'ledger.put', payload: {} }], AT);
    expect(written[0]?.seq).toBe(before + 1);
  });

  it('replays a reopened journal to the same place', async () => {
    const { layout, stores } = await busyStore(18);
    const reopened = await fileJournal(dataLayout(layout.root));
    const rebuilt = memoryStores();
    await replay(await reopened.read(), rebuilt);
    expect(await rebuilt.ledgers.get('lrn_one')).toEqual(await stores.ledgers.get('lrn_one'));
  });

  it('applies every entry it holds and skips nothing', async () => {
    const { journal } = await busyStore(12);
    const report = await replay(await journal.read(), memoryStores());
    expect(report.skipped).toBe(0);
    expect(report.applied).toBe(await journal.size());
  });
});
