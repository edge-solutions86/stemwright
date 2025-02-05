import type { Attempt } from '../domain/mastery/attempt.js';
import { attempt, isMatchVerdict } from '../domain/mastery/attempt.js';
import type { CellStrength } from '../domain/mastery/strength.js';
import type { MasteryLedger } from '../domain/mastery/ledger.js';
import { buildLedger } from '../domain/mastery/ledger.js';
import { ValidationError } from '../domain/errors.js';
import type { Json } from './codec.js';
import {
  readBoolean,
  readDate,
  readNumber,
  readOptionalDate,
  readOptionalNumber,
  readString,
  requireObject,
} from './codec.js';

/**
 * Record codecs for the two things written on every answer.
 *
 * An attempt is rebuilt through `attempt()` rather than cast into shape, so a
 * row that a bug wrote badly is refused on the way in rather than on the way
 * out. A strength is rebuilt by hand, because it has no constructor: it is
 * derived state and anything it could validate would already have been checked
 * when the attempt that produced it was written.
 */
export function encodeAttempt(entry: Attempt): Json {
  return {
    learnerId: entry.learnerId,
    sessionId: entry.sessionId,
    cellId: entry.cellId,
    deckKey: entry.deckKey,
    on: entry.on,
    sequence: entry.sequence,
    given: entry.given,
    expected: entry.expected,
    verdict: entry.verdict,
    toleranceLevel: entry.toleranceLevel,
    revisionId: entry.revisionId,
    ...(entry.elapsedMs === undefined ? {} : { elapsedMs: entry.elapsedMs }),
  };
}

export function decodeAttempt(value: unknown, path = 'attempt'): Attempt {
  const row = requireObject(path, value);
  const verdict = readString(row, path, 'verdict');
  if (!isMatchVerdict(verdict)) {
    throw ValidationError.field(`${path}.verdict`, `is not a verdict: ${verdict}`);
  }
  const elapsedMs = readOptionalNumber(row, path, 'elapsedMs');
  return attempt({
    learnerId: readString(row, path, 'learnerId'),
    sessionId: readString(row, path, 'sessionId'),
    cellId: readString(row, path, 'cellId'),
    deckKey: readString(row, path, 'deckKey'),
    on: readDate(row, path, 'on'),
    sequence: readNumber(row, path, 'sequence'),
    given: readString(row, path, 'given'),
    expected: readString(row, path, 'expected'),
    verdict,
    toleranceLevel: readString(row, path, 'toleranceLevel'),
    revisionId: readString(row, path, 'revisionId'),
    ...(elapsedMs === undefined ? {} : { elapsedMs }),
  });
}

export function encodeStrength(strength: CellStrength): Json {
  return {
    cellId: strength.cellId,
    rung: strength.rung,
    seen: strength.seen,
    correct: strength.correct,
    lapses: strength.lapses,
    streak: strength.streak,
    suspended: strength.suspended,
    ...(strength.dueOn === undefined ? {} : { dueOn: strength.dueOn }),
    ...(strength.lastSeenOn === undefined ? {} : { lastSeenOn: strength.lastSeenOn }),
  };
}

function checkCount(path: string, key: string, value: number): number {
  if (!Number.isInteger(value) || value < 0) {
    throw ValidationError.field(
      `${path}.${key}`,
      `must be a whole number at or above zero: ${String(value)}`,
    );
  }
  return value;
}

export function decodeStrength(value: unknown, path = 'strength'): CellStrength {
  const row = requireObject(path, value);
  const seen = checkCount(path, 'seen', readNumber(row, path, 'seen'));
  const correct = checkCount(path, 'correct', readNumber(row, path, 'correct'));
  if (correct > seen) {
    throw ValidationError.field(`${path}.correct`, 'is larger than the number of sightings');
  }
  return {
    cellId: readString(row, path, 'cellId'),
    rung: checkCount(path, 'rung', readNumber(row, path, 'rung')),
    dueOn: readOptionalDate(row, path, 'dueOn'),
    seen,
    correct,
    lapses: checkCount(path, 'lapses', readNumber(row, path, 'lapses')),
    streak: checkCount(path, 'streak', readNumber(row, path, 'streak')),
    lastSeenOn: readOptionalDate(row, path, 'lastSeenOn'),
    suspended: readBoolean(row, path, 'suspended'),
  };
}

export function encodeLedger(ledger: MasteryLedger): Json {
  return {
    learnerId: ledger.learnerId,
    cells: [...ledger.byCell.values()]
      .sort((a, b) => a.cellId.localeCompare(b.cellId))
      .map(encodeStrength),
  };
}

export function decodeLedger(value: unknown, path = 'ledger'): MasteryLedger {
  const row = requireObject(path, value);
  const cells = row['cells'];
  if (!Array.isArray(cells)) {
    throw ValidationError.field(`${path}.cells`, 'is not an array');
  }
  return buildLedger(
    readString(row, path, 'learnerId'),
    cells.map((entry, index) => decodeStrength(entry, `${path}.cells.${index}`)),
  );
}
