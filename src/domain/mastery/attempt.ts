import { ProblemCollector } from '../errors.js';
import type { Id } from '../identifiers.js';
import { asSlug, derivedId } from '../identifiers.js';
import type { MatchVerdict } from '../ortho/compare.js';
import { normaliseInput } from '../text/normalise.js';
import type { CivilDate } from '../time/civil.js';

/**
 * One typed answer, recorded as it was marked at the time. This record is the
 * only thing in the system that is never recomputed: the tolerance level, the
 * profile revision and the expected form are all copied in rather than looked
 * up later, so a transcript printed next year says what the learner was
 * actually shown.
 *
 * That redundancy is the point. Every other table can be rebuilt from the
 * journal, and this one cannot be rebuilt from anything, because the inputs
 * that produced it have moved on.
 */
export interface Attempt {
  readonly id: Id<'attempt'>;
  readonly learnerId: string;
  readonly sessionId: string;
  readonly cellId: string;
  readonly deckKey: string;
  readonly on: CivilDate;
  readonly sequence: number;
  readonly given: string;
  readonly expected: string;
  readonly verdict: MatchVerdict;
  readonly toleranceLevel: string;
  readonly revisionId: string;
  readonly elapsedMs: number | undefined;
}

export interface AttemptInput {
  readonly learnerId: string;
  readonly sessionId: string;
  readonly cellId: string;
  readonly deckKey: string;
  readonly on: CivilDate;
  readonly sequence: number;
  readonly given: string;
  readonly expected: string;
  readonly verdict: MatchVerdict;
  readonly toleranceLevel: string;
  readonly revisionId: string;
  readonly elapsedMs?: number;
}

const VERDICTS: readonly MatchVerdict[] = ['exact', 'tolerated', 'near', 'wrong'];

export function isMatchVerdict(value: string): value is MatchVerdict {
  return (VERDICTS as readonly string[]).includes(value);
}

/**
 * The id is derived from the session and the sequence number rather than the
 * cell, because a learner may meet the same cell twice in one session after a
 * lapse and both attempts are real.
 */
export function attemptIdFor(sessionId: string, sequence: number): Id<'attempt'> {
  return derivedId('attempt', sessionId, String(sequence));
}

export function attempt(input: AttemptInput): Attempt {
  const problems = new ProblemCollector();

  if (!Number.isInteger(input.sequence) || input.sequence < 1) {
    problems.add('sequence', `must be a positive whole number: ${String(input.sequence)}`);
  }
  const verdict: string = input.verdict;
  if (!isMatchVerdict(verdict)) {
    problems.add('verdict', `is not a verdict: ${verdict}`);
  }
  if (input.elapsedMs !== undefined) {
    if (!Number.isFinite(input.elapsedMs) || input.elapsedMs < 0) {
      problems.add('elapsedMs', `must not be negative: ${String(input.elapsedMs)}`);
    }
  }
  const expected = normaliseInput(input.expected);
  if (expected === '') {
    problems.add('expected', 'must not be blank');
  }

  problems.throwIfAny('attempt is not usable');

  return {
    id: attemptIdFor(input.sessionId, input.sequence),
    learnerId: input.learnerId,
    sessionId: input.sessionId,
    cellId: input.cellId,
    deckKey: asSlug('deckKey', input.deckKey),
    on: input.on,
    sequence: input.sequence,
    given: normaliseInput(input.given),
    expected,
    verdict: input.verdict,
    toleranceLevel: asSlug('toleranceLevel', input.toleranceLevel),
    revisionId: input.revisionId,
    elapsedMs: input.elapsedMs,
  };
}

export function wasCorrect(entry: Attempt): boolean {
  return entry.verdict === 'exact' || entry.verdict === 'tolerated';
}

export function wasBlank(entry: Attempt): boolean {
  return entry.given === '';
}

export function compareAttempts(a: Attempt, b: Attempt): number {
  const byDay = a.on.localeCompare(b.on);
  if (byDay !== 0) {
    return byDay;
  }
  const bySession = a.sessionId.localeCompare(b.sessionId);
  return bySession !== 0 ? bySession : a.sequence - b.sequence;
}

export interface AttemptTally {
  readonly total: number;
  readonly correct: number;
  readonly exact: number;
  readonly near: number;
  readonly wrong: number;
  readonly blank: number;
}

export function tally(attempts: readonly Attempt[]): AttemptTally {
  let correct = 0;
  let exact = 0;
  let near = 0;
  let wrong = 0;
  let blank = 0;

  for (const entry of attempts) {
    if (wasBlank(entry)) {
      blank += 1;
    }
    switch (entry.verdict) {
      case 'exact':
        exact += 1;
        correct += 1;
        break;
      case 'tolerated':
        correct += 1;
        break;
      case 'near':
        near += 1;
        break;
      case 'wrong':
        wrong += 1;
        break;
    }
  }

  return { total: attempts.length, correct, exact, near, wrong, blank };
}

export function accuracyOf(tallied: AttemptTally): number {
  return tallied.total === 0 ? 0 : tallied.correct / tallied.total;
}

export function medianElapsed(attempts: readonly Attempt[]): number | undefined {
  const timed = attempts
    .map((entry) => entry.elapsedMs)
    .filter((value): value is number => value !== undefined)
    .sort((a, b) => a - b);
  if (timed.length === 0) {
    return undefined;
  }
  const middle = Math.floor(timed.length / 2);
  return timed.length % 2 === 1 ? timed[middle]! : (timed[middle - 1]! + timed[middle]!) / 2;
}
