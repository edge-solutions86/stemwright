import { readdir } from 'node:fs/promises';
import { join } from 'node:path';
import { IntegrityError } from '../domain/errors.js';
import type { Stores } from '../app/ports.js';
import type { Json } from './codec.js';
import { readNumber, readString, requireObject } from './codec.js';
import type { DataLayout } from './files.js';
import {
  atomicWrite,
  ensureDirectory,
  isMissing,
  readIfPresent,
  snapshotPath,
  snapshotSeq,
} from './files.js';
import type { Journal } from './journal.js';
import { decodeAttempt, decodeLedger, encodeAttempt, encodeLedger } from './records.js';
import type { ReplayReport } from './replay.js';
import { replay } from './replay.js';

/**
 * A snapshot is a shortcut, never a source of truth. It records the state at
 * one journal sequence, and a start up loads the newest one and replays only
 * what came after it.
 *
 * That makes the correctness argument short: if the snapshot is wrong or
 * missing, deleting it and replaying from the beginning gives the same answer.
 * Anything that could only be recovered from a snapshot would break that, so
 * nothing is written here that the journal does not already hold.
 */
export interface Snapshot {
  readonly seq: number;
  readonly at: string;
  readonly learners: readonly string[];
  readonly payload: Json;
}

export async function buildSnapshot(
  stores: Stores,
  learnerIds: readonly string[],
  seq: number,
  at: Date,
): Promise<Snapshot> {
  const ledgers: Json[] = [];
  const attempts: Json[] = [];

  for (const learnerId of [...learnerIds].sort()) {
    const ledger = await stores.ledgers.get(learnerId);
    if (ledger !== undefined) {
      ledgers.push(encodeLedger(ledger));
    }
    for (const entry of await stores.attempts.listByLearner(learnerId)) {
      attempts.push(encodeAttempt(entry));
    }
  }

  return {
    seq,
    at: at.toISOString(),
    learners: [...learnerIds].sort(),
    payload: { ledgers, attempts },
  };
}

export function encodeSnapshot(snapshot: Snapshot): string {
  return JSON.stringify(
    {
      seq: snapshot.seq,
      at: snapshot.at,
      learners: snapshot.learners,
      ...(snapshot.payload as object),
    },
    null,
    2,
  );
}

export function decodeSnapshot(text: string, path = 'snapshot'): Snapshot {
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new IntegrityError(`${path} is not valid json`);
  }
  const row = requireObject(path, parsed);
  const learners = row['learners'];
  if (!Array.isArray(learners)) {
    throw new IntegrityError(`${path}.learners is not an array`);
  }
  return {
    seq: readNumber(row, path, 'seq'),
    at: readString(row, path, 'at'),
    learners: learners.map(String),
    payload: { ledgers: row['ledgers'] ?? [], attempts: row['attempts'] ?? [] },
  };
}

export async function writeSnapshot(layout: DataLayout, snapshot: Snapshot): Promise<string> {
  const path = snapshotPath(layout, snapshot.seq);
  await ensureDirectory(layout.snapshotDir);
  await atomicWrite(path, encodeSnapshot(snapshot));
  return path;
}

export async function listSnapshots(layout: DataLayout): Promise<number[]> {
  try {
    const names = await readdir(layout.snapshotDir);
    return names
      .map(snapshotSeq)
      .filter((seq): seq is number => seq !== undefined)
      .sort((a, b) => a - b);
  } catch (error) {
    if (isMissing(error)) {
      return [];
    }
    throw error;
  }
}

export async function latestSnapshot(layout: DataLayout): Promise<Snapshot | undefined> {
  const seqs = await listSnapshots(layout);
  const newest = seqs[seqs.length - 1];
  if (newest === undefined) {
    return undefined;
  }
  const text = await readIfPresent(
    join(layout.snapshotDir, `${String(newest).padStart(12, '0')}.json`),
  );
  return text === undefined ? undefined : decodeSnapshot(text);
}

export async function restore(snapshot: Snapshot, stores: Stores): Promise<void> {
  const payload = requireObject('snapshot', snapshot.payload);
  const ledgers = payload['ledgers'];
  const attempts = payload['attempts'];

  if (Array.isArray(ledgers)) {
    for (const [index, entry] of ledgers.entries()) {
      await stores.ledgers.put(decodeLedger(entry, `snapshot.ledgers.${index}`));
    }
  }
  if (Array.isArray(attempts)) {
    const decoded = attempts.map((entry, index) =>
      decodeAttempt(entry, `snapshot.attempts.${index}`),
    );
    await stores.attempts.append(decoded);
  }
}

export interface BootstrapReport extends ReplayReport {
  readonly fromSnapshot: number | undefined;
}

/**
 * Start up. Load the newest snapshot, then replay the journal from the entry
 * after it. With no snapshot the replay starts at one, which is the same
 * answer taken the slow way.
 */
export async function bootstrap(
  layout: DataLayout,
  journal: Journal,
  stores: Stores,
): Promise<BootstrapReport> {
  const snapshot = await latestSnapshot(layout);
  if (snapshot !== undefined) {
    await restore(snapshot, stores);
  }
  const from = snapshot === undefined ? 1 : snapshot.seq + 1;
  const report = await replay(await journal.read(from), stores);
  return { ...report, fromSnapshot: snapshot?.seq };
}
