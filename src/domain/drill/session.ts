import { ConflictError, ValidationError } from '../errors.js';
import type { Id } from '../identifiers.js';
import { asSlug, derivedId } from '../identifiers.js';
import type { Attempt, AttemptTally } from '../mastery/attempt.js';
import { accuracyOf, medianElapsed, tally } from '../mastery/attempt.js';
import type { CivilDate } from '../time/civil.js';
import type { BurialSet } from './burial.js';
import { NOTHING_BURIED } from './burial.js';

/**
 * A session is one sitting. It exists because burial is a session scoped fact:
 * the twins of an answered cell stay out of the way until the learner closes
 * the tab, and nothing longer lived should carry that.
 *
 * A session is append only while it is open and frozen once closed. Reopening
 * would let an answer be recorded against a burial set that has moved on, which
 * is the one way the twin rule can be made to contradict itself.
 */
export interface Session {
  readonly id: Id<'session'>;
  readonly learnerId: string;
  readonly deckKey: string;
  readonly on: CivilDate;
  readonly attempts: readonly Attempt[];
  readonly buried: BurialSet;
  readonly closed: boolean;
}

export function sessionIdFor(
  learnerId: string,
  deckKey: string,
  on: CivilDate,
  ordinal = 1,
): Id<'session'> {
  return derivedId('session', learnerId, deckKey, on, String(ordinal));
}

export function openSession(
  learnerId: string,
  deckKey: string,
  on: CivilDate,
  ordinal = 1,
): Session {
  return {
    id: sessionIdFor(learnerId, asSlug('deckKey', deckKey), on, ordinal),
    learnerId,
    deckKey: asSlug('deckKey', deckKey),
    on,
    attempts: [],
    buried: NOTHING_BURIED,
    closed: false,
  };
}

export function nextSequence(session: Session): number {
  return session.attempts.length + 1;
}

export function recordAttempt(session: Session, entry: Attempt, buried: BurialSet): Session {
  if (session.closed) {
    throw new ConflictError(`session ${session.id} is closed`);
  }
  if (entry.sessionId !== session.id) {
    throw ValidationError.field(
      'attempt.sessionId',
      `belongs to another session: ${entry.sessionId}`,
    );
  }
  if (entry.sequence !== nextSequence(session)) {
    throw ValidationError.field(
      'attempt.sequence',
      `is out of order, expected ${nextSequence(session)} and got ${entry.sequence}`,
    );
  }
  return { ...session, attempts: [...session.attempts, entry], buried };
}

export function closeSession(session: Session): Session {
  return session.closed ? session : { ...session, closed: true };
}

export function sessionLength(session: Session): number {
  return session.attempts.length;
}

export function cellsSeen(session: Session): string[] {
  return [...new Set(session.attempts.map((entry) => entry.cellId))];
}

export function attemptsForCell(session: Session, cellId: string): Attempt[] {
  return session.attempts.filter((entry) => entry.cellId === cellId);
}

export interface SessionSummary {
  readonly sessionId: string;
  readonly learnerId: string;
  readonly deckKey: string;
  readonly on: CivilDate;
  readonly tally: AttemptTally;
  readonly accuracy: number;
  readonly distinctCells: number;
  readonly medianElapsedMs: number | undefined;
  readonly closed: boolean;
}

export function summarise(session: Session): SessionSummary {
  const tallied = tally(session.attempts);
  return {
    sessionId: session.id,
    learnerId: session.learnerId,
    deckKey: session.deckKey,
    on: session.on,
    tally: tallied,
    accuracy: accuracyOf(tallied),
    distinctCells: cellsSeen(session).length,
    medianElapsedMs: medianElapsed(session.attempts),
    closed: session.closed,
  };
}

/** Cells the learner got wrong and did not later get right in the same sitting. */
export function unresolvedCells(session: Session): string[] {
  const lastVerdict = new Map<string, Attempt>();
  for (const entry of session.attempts) {
    lastVerdict.set(entry.cellId, entry);
  }
  return [...lastVerdict.entries()]
    .filter(([, entry]) => entry.verdict === 'wrong' || entry.verdict === 'near')
    .map(([cellId]) => cellId)
    .sort();
}

export function describeSession(summary: SessionSummary): string {
  if (summary.tally.total === 0) {
    return `no answers on ${summary.on}`;
  }
  const percent = Math.round(summary.accuracy * 100);
  return `${summary.tally.total} answers across ${summary.distinctCells} cells on ${summary.on}, ${percent} percent right`;
}
