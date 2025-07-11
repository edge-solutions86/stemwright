import { copyFile, mkdir } from 'node:fs/promises';
import { join } from 'node:path';
import { IntegrityError } from '../domain/errors.js';
import type { Stores } from '../app/ports.js';
import type { DataLayout } from './files.js';
import {
  atomicWrite,
  ensureDirectory,
  readIfPresent,
  removeIfPresent,
  snapshotPath,
} from './files.js';
import type { Journal } from './journal.js';
import { buildSnapshot, latestSnapshot, listSnapshots, writeSnapshot } from './snapshot.js';

/**
 * Backing up and tidying, which are the two operational jobs this store needs
 * and the only two places that touch files nobody is currently reading.
 *
 * Compaction never removes a journal entry. Truncating an append only log is
 * the one way to make a replay produce a different answer to the one it
 * produced yesterday, and the space it would save is measured in megabytes
 * while the failure it would cause is measured in a term's marks. What it does
 * instead is write a fresh snapshot so the replay is short, and drop the
 * snapshots nobody needs any more.
 */
export interface BackupReport {
  readonly destination: string;
  readonly journalCopied: boolean;
  readonly snapshotsCopied: number;
}

export async function backup(layout: DataLayout, destination: string): Promise<BackupReport> {
  await ensureDirectory(destination);
  const journalText = await readIfPresent(layout.journalPath);
  if (journalText !== undefined) {
    await atomicWrite(join(destination, 'journal.ndjson'), journalText);
  }

  const seqs = await listSnapshots(layout);
  if (seqs.length > 0) {
    await mkdir(join(destination, 'snapshots'), { recursive: true });
  }
  for (const seq of seqs) {
    const name = `${String(seq).padStart(12, '0')}.json`;
    await copyFile(join(layout.snapshotDir, name), join(destination, 'snapshots', name));
  }

  return {
    destination,
    journalCopied: journalText !== undefined,
    snapshotsCopied: seqs.length,
  };
}

export interface CompactReport {
  readonly wroteSnapshot: boolean;
  readonly atSeq: number;
  readonly removed: readonly number[];
  readonly journalEntries: number;
}

/**
 * Writes a snapshot at the current journal position and prunes older ones.
 * Doing nothing is a legitimate outcome: a journal that has not moved since the
 * last snapshot has nothing to compact, and writing an identical snapshot every
 * night would only make the pruning harder to reason about.
 */
export async function compact(
  layout: DataLayout,
  journal: Journal,
  stores: Stores,
  learnerIds: readonly string[],
  at: Date,
  keep = 3,
): Promise<CompactReport> {
  if (keep < 1) {
    throw new IntegrityError(`a compaction must keep at least one snapshot, not ${keep}`);
  }

  const seq = await journal.lastSeq();
  const newest = await latestSnapshot(layout);
  const entries = await journal.size();

  if (newest !== undefined && newest.seq >= seq) {
    return { wroteSnapshot: false, atSeq: newest.seq, removed: [], journalEntries: entries };
  }

  await writeSnapshot(layout, await buildSnapshot(stores, learnerIds, seq, at));
  const removed = await pruneSnapshots(layout, keep);
  return { wroteSnapshot: true, atSeq: seq, removed, journalEntries: entries };
}

/** Removes all but the newest few snapshots and reports what it dropped. */
export async function pruneSnapshots(layout: DataLayout, keep: number): Promise<number[]> {
  if (keep < 1) {
    throw new IntegrityError(`a prune must keep at least one snapshot, not ${keep}`);
  }
  const seqs = await listSnapshots(layout);
  const doomed = seqs.slice(0, Math.max(0, seqs.length - keep));
  for (const seq of doomed) {
    await removeIfPresent(snapshotPath(layout, seq));
  }
  return doomed;
}

export function describeCompaction(report: CompactReport): string {
  if (!report.wroteSnapshot) {
    return `nothing to compact, the newest snapshot is already at ${report.atSeq}`;
  }
  const pruned =
    report.removed.length === 0 ? '' : `, dropped ${report.removed.length} older snapshots`;
  return `snapshot written at ${report.atSeq} over ${report.journalEntries} journal entries${pruned}`;
}
