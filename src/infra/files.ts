import { appendFile, mkdir, readFile, rename, rm, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { IntegrityError } from '../domain/errors.js';
import type { Journal, JournalEntry } from './journal.js';
import { decodeJournal, encodeEntry } from './journal.js';

/**
 * The file layout. Everything the desk keeps lives under one directory, which
 * is what makes a backup a copy of a folder rather than a procedure.
 *
 *     <root>/journal.ndjson     every write, in order
 *     <root>/snapshots/<n>.json a point the replay can start from
 *
 * Writes that are not appends go through `atomicWrite`, which writes a
 * neighbouring temporary file and renames it over the target. A rename is
 * atomic on both of the platforms this runs on, so a reader never sees a half
 * written snapshot, and a crash leaves the old one intact.
 */
export interface DataLayout {
  readonly root: string;
  readonly journalPath: string;
  readonly snapshotDir: string;
}

export function dataLayout(root: string): DataLayout {
  return {
    root,
    journalPath: join(root, 'journal.ndjson'),
    snapshotDir: join(root, 'snapshots'),
  };
}

export async function ensureDirectory(path: string): Promise<void> {
  await mkdir(path, { recursive: true });
}

export async function atomicWrite(path: string, contents: string): Promise<void> {
  await ensureDirectory(dirname(path));
  const temporary = `${path}.tmp`;
  await writeFile(temporary, contents, 'utf8');
  await rename(temporary, path);
}

export async function readIfPresent(path: string): Promise<string | undefined> {
  try {
    return await readFile(path, 'utf8');
  } catch (error) {
    if (isMissing(error)) {
      return undefined;
    }
    throw error;
  }
}

export function isMissing(error: unknown): boolean {
  return (
    typeof error === 'object' &&
    error !== null &&
    'code' in error &&
    (error as { code?: unknown }).code === 'ENOENT'
  );
}

export async function removeIfPresent(path: string): Promise<void> {
  await rm(path, { force: true, recursive: true });
}

/**
 * A journal on the file system. It caches the entries it has read so a replay
 * does not reparse the whole log on every append, and it appends to the file
 * rather than rewriting it, which is the only reason the log is cheap enough to
 * keep forever.
 */
export async function fileJournal(layout: DataLayout): Promise<Journal> {
  await ensureDirectory(layout.root);
  const text = (await readIfPresent(layout.journalPath)) ?? '';
  const entries: JournalEntry[] = decodeJournal(text);

  return {
    async append(incoming, at) {
      if (incoming.length === 0) {
        return [];
      }
      const stamp = at.toISOString();
      const written = incoming.map((entry, index) => ({
        seq: entries.length + index + 1,
        at: stamp,
        kind: entry.kind,
        payload: entry.payload,
      }));
      await appendFile(layout.journalPath, `${written.map(encodeEntry).join('\n')}\n`, 'utf8');
      entries.push(...written);
      return written;
    },
    read(fromSeq = 1) {
      if (fromSeq < 1) {
        return Promise.reject(
          new IntegrityError(`a journal read starts at sequence one, not ${fromSeq}`),
        );
      }
      return Promise.resolve(entries.filter((entry) => entry.seq >= fromSeq));
    },
    size: () => Promise.resolve(entries.length),
    lastSeq: () => Promise.resolve(entries.length === 0 ? 0 : entries[entries.length - 1]!.seq),
  };
}

export function snapshotPath(layout: DataLayout, seq: number): string {
  return join(layout.snapshotDir, `${String(seq).padStart(12, '0')}.json`);
}

/** Reads the sequence a snapshot file name encodes, or nothing if it is not one. */
export function snapshotSeq(fileName: string): number | undefined {
  const match = /^(\d{12})\.json$/.exec(fileName);
  if (match === null) {
    return undefined;
  }
  return Number(match[1]);
}
