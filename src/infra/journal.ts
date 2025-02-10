import { ConflictError, IntegrityError, ValidationError } from '../domain/errors.js';
import type { Json } from './codec.js';
import { isObject, readNumber, readString, requireObject } from './codec.js';

/**
 * An append only log of everything that happened, in the order it happened.
 * It is the only durable thing in the system: every store, every ledger and
 * every report can be rebuilt from it, and nothing else can be rebuilt from
 * them.
 *
 * Entries carry a sequence number that the journal assigns, never the caller.
 * A caller supplied sequence is a caller supplied ordering, and two writers
 * would then disagree about what happened first while both believing they were
 * right.
 */
export type JournalKind =
  | 'lexeme.put'
  | 'lexeme.removed'
  | 'book.put'
  | 'deck.put'
  | 'deck.removed'
  | 'attempt.appended'
  | 'ledger.put'
  | 'session.put'
  | 'course.put'
  | 'enrolment.put'
  | 'assignment.put';

export interface JournalEntry {
  readonly seq: number;
  readonly at: string;
  readonly kind: JournalKind;
  readonly payload: Json;
}

export interface JournalAppend {
  readonly kind: JournalKind;
  readonly payload: Json;
}

export interface Journal {
  append(entries: readonly JournalAppend[], at: Date): Promise<JournalEntry[]>;
  read(fromSeq?: number): Promise<JournalEntry[]>;
  size(): Promise<number>;
  lastSeq(): Promise<number>;
}

const KINDS: readonly JournalKind[] = [
  'lexeme.put',
  'lexeme.removed',
  'book.put',
  'deck.put',
  'deck.removed',
  'attempt.appended',
  'ledger.put',
  'session.put',
  'course.put',
  'enrolment.put',
  'assignment.put',
];

export function isJournalKind(value: string): value is JournalKind {
  return (KINDS as readonly string[]).includes(value);
}

export function encodeEntry(entry: JournalEntry): string {
  return JSON.stringify({
    seq: entry.seq,
    at: entry.at,
    kind: entry.kind,
    payload: entry.payload,
  });
}

/**
 * Decoding a line is where a torn write shows up. A half written last line is
 * the normal way an append only file ends after a crash, so the failure has to
 * be distinguishable from a line that parsed but held nonsense.
 */
export function decodeEntry(line: string, lineNumber: number): JournalEntry {
  let parsed: unknown;
  try {
    parsed = JSON.parse(line);
  } catch {
    throw new IntegrityError(`journal line ${lineNumber} is not valid json`);
  }
  if (!isObject(parsed)) {
    throw new IntegrityError(`journal line ${lineNumber} is not an object`);
  }

  const path = `journal.${lineNumber}`;
  const kind = readString(parsed, path, 'kind');
  if (!isJournalKind(kind)) {
    throw new IntegrityError(`journal line ${lineNumber} has an unknown kind: ${kind}`);
  }
  const payload = parsed['payload'];
  if (payload === undefined) {
    throw ValidationError.field(`${path}.payload`, 'is missing');
  }

  return {
    seq: readNumber(parsed, path, 'seq'),
    at: readString(parsed, path, 'at'),
    kind,
    payload,
  };
}

export function decodeJournal(text: string): JournalEntry[] {
  const lines = text.split('\n').filter((line) => line.trim() !== '');
  const entries = lines.map((line, index) => decodeEntry(line, index + 1));
  for (const [index, entry] of entries.entries()) {
    const expected = index + 1;
    if (entry.seq !== expected) {
      throw new IntegrityError(
        `journal is out of order at line ${expected}, expected sequence ${expected} and found ${entry.seq}`,
      );
    }
  }
  return entries;
}

export function encodeJournal(entries: readonly JournalEntry[]): string {
  return entries.length === 0 ? '' : `${entries.map(encodeEntry).join('\n')}\n`;
}

export function memoryJournal(seed: readonly JournalEntry[] = []): Journal {
  const entries: JournalEntry[] = [...seed];

  return {
    append(incoming, at) {
      if (incoming.length === 0) {
        return Promise.resolve([]);
      }
      const stamp = at.toISOString();
      const written = incoming.map((entry, index) => ({
        seq: entries.length + index + 1,
        at: stamp,
        kind: entry.kind,
        payload: entry.payload,
      }));
      entries.push(...written);
      return Promise.resolve(written);
    },
    read(fromSeq = 1) {
      // Rejects rather than throwing, so a caller that only awaits sees the
      // failure. A synchronous throw from an async port escapes an await and
      // lands somewhere nobody is catching.
      if (fromSeq < 1) {
        return Promise.reject(
          new ConflictError(`a journal read starts at sequence one, not ${fromSeq}`),
        );
      }
      return Promise.resolve(entries.filter((entry) => entry.seq >= fromSeq));
    },
    size: () => Promise.resolve(entries.length),
    lastSeq: () => Promise.resolve(entries.length === 0 ? 0 : entries[entries.length - 1]!.seq),
  };
}

/** Entries of one kind, which is how a replay picks out what it cares about. */
export function entriesOfKind(entries: readonly JournalEntry[], kind: JournalKind): JournalEntry[] {
  return entries.filter((entry) => entry.kind === kind);
}

export function payloadObject(entry: JournalEntry): Record<string, Json> {
  return requireObject(`journal.${entry.seq}.payload`, entry.payload);
}
