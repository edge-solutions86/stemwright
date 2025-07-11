import { mkdtemp } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { fixedClock } from '../../src/app/ports.js';
import { IntegrityError } from '../../src/domain/errors.js';
import { attempt } from '../../src/domain/mastery/attempt.js';
import type { AttemptInput } from '../../src/domain/mastery/attempt.js';
import { buildLedger } from '../../src/domain/mastery/ledger.js';
import { newStrength } from '../../src/domain/mastery/strength.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { backup, compact, describeCompaction, pruneSnapshots } from '../../src/infra/backup.js';
import { dataLayout, fileJournal, readIfPresent, removeIfPresent } from '../../src/infra/files.js';
import { memoryStores } from '../../src/infra/memory.js';
import { journalled } from '../../src/infra/replay.js';
import {
  bootstrap,
  buildSnapshot,
  latestSnapshot,
  listSnapshots,
  writeSnapshot,
} from '../../src/infra/snapshot.js';

const DAY = parseCivilDate('2025-03-03');
const AT = new Date('2025-03-03T09:00:00Z');
const clock = fixedClock(AT, DAY);

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
  const root = await mkdtemp(join(tmpdir(), 'stemwright-backup-'));
  roots.push(root);
  return root;
};

afterEach(async () => {
  while (roots.length > 0) {
    await removeIfPresent(roots.pop()!);
  }
});

const seeded = async () => {
  const layout = dataLayout(await freshRoot());
  const journal = await fileJournal(layout);
  const stores = journalled(memoryStores(), journal, clock);
  await stores.attempts.append([attempt(base)]);
  await stores.ledgers.put(buildLedger('lrn_one', [newStrength('cell_aaaaaa')]));
  return { layout, journal, stores };
};

describe('backup', () => {
  it('copies the journal', async () => {
    const { layout } = await seeded();
    const destination = await freshRoot();
    const report = await backup(layout, destination);
    expect(report.journalCopied).toBe(true);
    expect(await readIfPresent(join(destination, 'journal.ndjson'))).toContain('attempt.appended');
  });

  it('copies every snapshot', async () => {
    const { layout, stores } = await seeded();
    await writeSnapshot(layout, await buildSnapshot(stores, ['lrn_one'], 1, AT));
    await writeSnapshot(layout, await buildSnapshot(stores, ['lrn_one'], 2, AT));
    const destination = await freshRoot();
    expect((await backup(layout, destination)).snapshotsCopied).toBe(2);
    expect(await listSnapshots(dataLayout(destination))).toEqual([1, 2]);
  });

  it('copies nothing from an empty store without failing', async () => {
    const layout = dataLayout(await freshRoot());
    const report = await backup(layout, await freshRoot());
    expect(report.journalCopied).toBe(false);
    expect(report.snapshotsCopied).toBe(0);
  });

  it('produces a directory a bootstrap can read', async () => {
    const { layout } = await seeded();
    const destination = await freshRoot();
    await backup(layout, destination);

    const copied = dataLayout(destination);
    const journal = await fileJournal(copied);
    const rebuilt = memoryStores();
    expect((await bootstrap(copied, journal, rebuilt)).applied).toBe(2);
  });
});

describe('compact', () => {
  it('writes a snapshot at the current position', async () => {
    const { layout, journal, stores } = await seeded();
    const report = await compact(layout, journal, stores, ['lrn_one'], AT);
    expect(report.wroteSnapshot).toBe(true);
    expect(report.atSeq).toBe(2);
    expect((await latestSnapshot(layout))?.seq).toBe(2);
  });

  it('does nothing when the journal has not moved', async () => {
    const { layout, journal, stores } = await seeded();
    await compact(layout, journal, stores, ['lrn_one'], AT);
    const second = await compact(layout, journal, stores, ['lrn_one'], AT);
    expect(second.wroteSnapshot).toBe(false);
    expect(await listSnapshots(layout)).toHaveLength(1);
  });

  it('never removes a journal entry', async () => {
    const { layout, journal, stores } = await seeded();
    await compact(layout, journal, stores, ['lrn_one'], AT);
    expect(await journal.size()).toBe(2);
    const text = await readIfPresent(layout.journalPath);
    expect(text?.trim().split('\n')).toHaveLength(2);
  });

  it('drops the snapshots it no longer needs', async () => {
    const { layout, journal, stores } = await seeded();
    for (const seq of [1, 2]) {
      await writeSnapshot(layout, await buildSnapshot(stores, ['lrn_one'], seq, AT));
    }
    await stores.ledgers.put(buildLedger('lrn_one', [newStrength('cell_b')]));
    const report = await compact(layout, journal, stores, ['lrn_one'], AT, 2);
    expect(report.wroteSnapshot).toBe(true);
    expect(report.atSeq).toBe(3);
    expect(report.removed).toEqual([1]);
    expect(await listSnapshots(layout)).toEqual([2, 3]);
  });

  it('refuses to keep fewer than one snapshot', async () => {
    const { layout, journal, stores } = await seeded();
    await expect(compact(layout, journal, stores, ['lrn_one'], AT, 0)).rejects.toThrow(
      IntegrityError,
    );
  });

  it('leaves the state readable after compaction', async () => {
    const { layout, journal, stores } = await seeded();
    await compact(layout, journal, stores, ['lrn_one'], AT);
    const rebuilt = memoryStores();
    const report = await bootstrap(layout, journal, rebuilt);
    expect(report.fromSnapshot).toBe(2);
    expect(await rebuilt.attempts.listByLearner('lrn_one')).toHaveLength(1);
  });
});

describe('pruneSnapshots', () => {
  it('keeps the newest few', async () => {
    const { layout, stores } = await seeded();
    for (const seq of [1, 2, 3]) {
      await writeSnapshot(layout, await buildSnapshot(stores, ['lrn_one'], seq, AT));
    }
    expect(await pruneSnapshots(layout, 2)).toEqual([1]);
    expect(await listSnapshots(layout)).toEqual([2, 3]);
  });

  it('drops nothing when there are already few enough', async () => {
    const { layout } = await seeded();
    expect(await pruneSnapshots(layout, 3)).toEqual([]);
  });

  it('refuses to keep fewer than one', async () => {
    const { layout } = await seeded();
    await expect(pruneSnapshots(layout, 0)).rejects.toThrow(IntegrityError);
  });
});

describe('describeCompaction', () => {
  it('says so when there was nothing to do', async () => {
    const { layout, journal, stores } = await seeded();
    await compact(layout, journal, stores, ['lrn_one'], AT);
    const second = await compact(layout, journal, stores, ['lrn_one'], AT);
    expect(describeCompaction(second)).toContain('nothing to compact');
  });

  it('names the position and the journal size', async () => {
    const { layout, journal, stores } = await seeded();
    const report = await compact(layout, journal, stores, ['lrn_one'], AT);
    expect(describeCompaction(report)).toBe('snapshot written at 2 over 2 journal entries');
  });
});
