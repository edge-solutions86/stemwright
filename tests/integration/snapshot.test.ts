import { mkdtemp } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { fixedClock } from '../../src/app/ports.js';
import { IntegrityError } from '../../src/domain/errors.js';
import { attempt } from '../../src/domain/mastery/attempt.js';
import type { AttemptInput } from '../../src/domain/mastery/attempt.js';
import { ladder } from '../../src/domain/mastery/ladder.js';
import { buildLedger, strengthFor } from '../../src/domain/mastery/ledger.js';
import { newStrength, recordCorrect } from '../../src/domain/mastery/strength.js';
import { dataLayout, fileJournal, removeIfPresent } from '../../src/infra/files.js';
import { memoryStores } from '../../src/infra/memory.js';
import { journalled } from '../../src/infra/replay.js';
import {
  bootstrap,
  buildSnapshot,
  decodeSnapshot,
  encodeSnapshot,
  latestSnapshot,
  listSnapshots,
  restore,
  writeSnapshot,
} from '../../src/infra/snapshot.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';

const d = (value: string) => parseCivilDate(value);
const DAY = d('2025-03-03');
const AT = new Date('2025-03-03T09:00:00Z');
const clock = fixedClock(AT, DAY);
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

const roots: string[] = [];
const freshRoot = async () => {
  const root = await mkdtemp(join(tmpdir(), 'stemwright-snap-'));
  roots.push(root);
  return root;
};

afterEach(async () => {
  while (roots.length > 0) {
    await removeIfPresent(roots.pop()!);
  }
});

const seededStore = async () => {
  const stores = memoryStores();
  await stores.attempts.append([attempt(base)]);
  await stores.ledgers.put(
    buildLedger('lrn_one', [recordCorrect(newStrength('cell_aaaaaa'), flat, DAY)]),
  );
  return stores;
};

describe('buildSnapshot', () => {
  it('captures the ledgers and attempts of the learners it was given', async () => {
    const snapshot = await buildSnapshot(await seededStore(), ['lrn_one'], 2, AT);
    expect(snapshot.seq).toBe(2);
    expect(snapshot.learners).toEqual(['lrn_one']);
    expect(snapshot.at).toBe('2025-03-03T09:00:00.000Z');
  });

  it('skips a learner with no ledger', async () => {
    const snapshot = await buildSnapshot(memoryStores(), ['lrn_one'], 1, AT);
    const payload = snapshot.payload as { ledgers: unknown[]; attempts: unknown[] };
    expect(payload.ledgers).toEqual([]);
    expect(payload.attempts).toEqual([]);
  });

  it('sorts the learners so two snapshots of one state compare equal', async () => {
    const stores = await seededStore();
    const first = await buildSnapshot(stores, ['lrn_two', 'lrn_one'], 2, AT);
    const second = await buildSnapshot(stores, ['lrn_one', 'lrn_two'], 2, AT);
    expect(encodeSnapshot(first)).toBe(encodeSnapshot(second));
  });
});

describe('encode and decode', () => {
  it('round trips', async () => {
    const snapshot = await buildSnapshot(await seededStore(), ['lrn_one'], 2, AT);
    const decoded = decodeSnapshot(encodeSnapshot(snapshot));
    expect(decoded.seq).toBe(2);
    expect(decoded.learners).toEqual(['lrn_one']);
  });

  it('refuses text that is not json', () => {
    expect(() => decodeSnapshot('{')).toThrow(IntegrityError);
  });

  it('refuses a snapshot with no learners array', () => {
    expect(() => decodeSnapshot(JSON.stringify({ seq: 1, at: 'x' }))).toThrow(
      /learners is not an array/,
    );
  });
});

describe('writeSnapshot and listSnapshots', () => {
  it('writes a file named after the sequence', async () => {
    const layout = dataLayout(await freshRoot());
    const snapshot = await buildSnapshot(await seededStore(), ['lrn_one'], 2, AT);
    const path = await writeSnapshot(layout, snapshot);
    expect(path.endsWith('000000000002.json')).toBe(true);
    expect(await listSnapshots(layout)).toEqual([2]);
  });

  it('lists nothing before any snapshot is written', async () => {
    expect(await listSnapshots(dataLayout(await freshRoot()))).toEqual([]);
  });

  it('takes the newest of several', async () => {
    const layout = dataLayout(await freshRoot());
    const stores = await seededStore();
    await writeSnapshot(layout, await buildSnapshot(stores, ['lrn_one'], 2, AT));
    await writeSnapshot(layout, await buildSnapshot(stores, ['lrn_one'], 9, AT));
    expect(await listSnapshots(layout)).toEqual([2, 9]);
    expect((await latestSnapshot(layout))?.seq).toBe(9);
  });

  it('gives nothing when there is no snapshot at all', async () => {
    expect(await latestSnapshot(dataLayout(await freshRoot()))).toBeUndefined();
  });
});

describe('restore', () => {
  it('puts the ledgers and attempts into fresh stores', async () => {
    const snapshot = await buildSnapshot(await seededStore(), ['lrn_one'], 2, AT);
    const rebuilt = memoryStores();
    await restore(snapshot, rebuilt);
    expect(await rebuilt.attempts.listByLearner('lrn_one')).toHaveLength(1);
    expect(strengthFor((await rebuilt.ledgers.get('lrn_one'))!, 'cell_aaaaaa').seen).toBe(1);
  });

  it('tolerates a snapshot holding nothing', async () => {
    const snapshot = await buildSnapshot(memoryStores(), [], 1, AT);
    await expect(restore(snapshot, memoryStores())).resolves.toBeUndefined();
  });
});

describe('bootstrap', () => {
  it('replays the whole journal when there is no snapshot', async () => {
    const layout = dataLayout(await freshRoot());
    const journal = await fileJournal(layout);
    const stores = journalled(memoryStores(), journal, clock);
    await stores.attempts.append([attempt(base)]);

    const rebuilt = memoryStores();
    const report = await bootstrap(layout, journal, rebuilt);
    expect(report.fromSnapshot).toBeUndefined();
    expect(report.applied).toBe(1);
    expect(await rebuilt.attempts.listByLearner('lrn_one')).toHaveLength(1);
  });

  it('starts after the snapshot and replays only what followed', async () => {
    const layout = dataLayout(await freshRoot());
    const journal = await fileJournal(layout);
    const stores = journalled(memoryStores(), journal, clock);
    await stores.attempts.append([attempt(base)]);
    await writeSnapshot(layout, await buildSnapshot(stores, ['lrn_one'], 1, AT));
    await stores.ledgers.put(buildLedger('lrn_one', [newStrength('cell_b')]));

    const rebuilt = memoryStores();
    const report = await bootstrap(layout, journal, rebuilt);
    expect(report.fromSnapshot).toBe(1);
    expect(report.applied).toBe(1);
    expect(await rebuilt.attempts.listByLearner('lrn_one')).toHaveLength(1);
    expect((await rebuilt.ledgers.get('lrn_one'))?.byCell.size).toBe(1);
  });

  it('gives the same state as a replay from the beginning', async () => {
    const layout = dataLayout(await freshRoot());
    const journal = await fileJournal(layout);
    const stores = journalled(memoryStores(), journal, clock);
    await stores.attempts.append([attempt(base)]);
    await stores.ledgers.put(buildLedger('lrn_one', [newStrength('cell_b')]));
    await writeSnapshot(layout, await buildSnapshot(stores, ['lrn_one'], 2, AT));

    const withSnapshot = memoryStores();
    await bootstrap(layout, journal, withSnapshot);

    const withoutSnapshot = memoryStores();
    await bootstrap(dataLayout(await freshRoot()), journal, withoutSnapshot);

    expect((await withSnapshot.ledgers.get('lrn_one'))?.byCell.size).toBe(
      (await withoutSnapshot.ledgers.get('lrn_one'))?.byCell.size,
    );
  });
});
