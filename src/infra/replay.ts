import { IntegrityError } from '../domain/errors.js';
import type { Clock, Stores } from '../app/ports.js';
import type { Journal, JournalEntry } from './journal.js';
import { payloadObject } from './journal.js';
import { decodeAttempt, decodeLedger, encodeAttempt, encodeLedger } from './records.js';
import { readString } from './codec.js';

/**
 * Two halves of the same idea. `journalled` wraps a set of stores so that every
 * write is written down before it is applied, and `replay` walks the journal
 * back into a fresh set of stores.
 *
 * Only the records with a codec are journalled: attempts and ledgers, which are
 * the two things a learner cannot afford to lose and the two that change on
 * every answer. Lexemes, decks and courses are editorial data that an import
 * can rebuild, and a codec for each of them is a cost that has not been earned
 * yet. The wrapper is explicit about that rather than silently dropping them.
 */
export interface JournalledStores extends Stores {
  readonly journal: Journal;
}

export function journalled(stores: Stores, journal: Journal, clock: Clock): JournalledStores {
  return {
    ...stores,
    journal,
    attempts: {
      ...stores.attempts,
      async append(entries) {
        if (entries.length === 0) {
          return;
        }
        await journal.append(
          entries.map((entry) => ({
            kind: 'attempt.appended' as const,
            payload: encodeAttempt(entry),
          })),
          clock.now(),
        );
        await stores.attempts.append(entries);
      },
    },
    ledgers: {
      ...stores.ledgers,
      async put(ledger) {
        await journal.append([{ kind: 'ledger.put', payload: encodeLedger(ledger) }], clock.now());
        await stores.ledgers.put(ledger);
      },
    },
  };
}

export interface ReplayReport {
  readonly applied: number;
  readonly skipped: number;
  readonly lastSeq: number;
}

/**
 * Applies entries in sequence order. An entry of a kind this build does not
 * handle is counted and skipped rather than refused, because a journal written
 * by a newer version has to be readable by an older one for long enough to roll
 * back. An entry that is malformed is a different thing and stops the replay.
 */
export async function replay(
  entries: readonly JournalEntry[],
  stores: Stores,
): Promise<ReplayReport> {
  let applied = 0;
  let skipped = 0;
  let lastSeq = 0;

  for (const entry of [...entries].sort((a, b) => a.seq - b.seq)) {
    lastSeq = entry.seq;
    switch (entry.kind) {
      case 'attempt.appended': {
        await stores.attempts.append([decodeAttempt(entry.payload, `journal.${entry.seq}`)]);
        applied += 1;
        break;
      }
      case 'ledger.put': {
        await stores.ledgers.put(decodeLedger(entry.payload, `journal.${entry.seq}`));
        applied += 1;
        break;
      }
      default:
        skipped += 1;
        break;
    }
  }

  return { applied, skipped, lastSeq };
}

export async function replayFrom(journal: Journal, stores: Stores): Promise<ReplayReport> {
  return replay(await journal.read(), stores);
}

/**
 * Reads a journal without applying it, which is what an operator wants when a
 * replay has failed and the question is which entry broke it.
 */
export function inspect(entries: readonly JournalEntry[]): string[] {
  return entries.map((entry) => {
    switch (entry.kind) {
      case 'attempt.appended': {
        const row = payloadObject(entry);
        return `${entry.seq} ${entry.at} attempt ${readString(row, 'payload', 'cellId')} by ${readString(row, 'payload', 'learnerId')}`;
      }
      case 'ledger.put': {
        const row = payloadObject(entry);
        return `${entry.seq} ${entry.at} ledger for ${readString(row, 'payload', 'learnerId')}`;
      }
      default:
        return `${entry.seq} ${entry.at} ${entry.kind}`;
    }
  });
}

export function assertContiguous(entries: readonly JournalEntry[]): void {
  for (const [index, entry] of entries.entries()) {
    if (entry.seq !== index + 1) {
      throw new IntegrityError(
        `journal is not contiguous at position ${index + 1}, found sequence ${entry.seq}`,
      );
    }
  }
}
