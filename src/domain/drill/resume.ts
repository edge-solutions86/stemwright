import type { CivilDate } from '../time/civil.js';
import { daysBetween } from '../time/civil.js';
import type { Session } from './session.js';
import { sessionLength, unresolvedCells } from './session.js';

/**
 * What to do with a session that is already open when a learner comes back.
 *
 * The answer depends on the day, because burial is a session scoped fact. A
 * session left open from yesterday is still carrying yesterday's buried twins,
 * and resuming into it would hide cells that are due today for a reason that
 * stopped applying at midnight.
 *
 * A session on the same day is resumed even if hours have passed, because the
 * learner closing a laptop for lunch has not stopped being in the middle of the
 * same sitting, and the burial is still doing the job it was meant to.
 */
export type ResumeAction = 'resume' | 'close-and-open' | 'open';

export interface ResumePlan {
  readonly action: ResumeAction;
  readonly reason: string;
  readonly staleFor: number;
  readonly answered: number;
  readonly unresolved: readonly string[];
}

export function planResume(session: Session | undefined, on: CivilDate): ResumePlan {
  if (session === undefined) {
    return {
      action: 'open',
      reason: 'there is no session open',
      staleFor: 0,
      answered: 0,
      unresolved: [],
    };
  }

  const answered = sessionLength(session);
  const unresolved = unresolvedCells(session);

  if (session.closed) {
    return {
      action: 'open',
      reason: 'the open session was already closed',
      staleFor: 0,
      answered,
      unresolved,
    };
  }

  const staleFor = daysBetween(session.on, on);
  if (staleFor === 0) {
    return {
      action: 'resume',
      reason: `resuming a session of ${answered} answers from today`,
      staleFor,
      answered,
      unresolved,
    };
  }
  if (staleFor < 0) {
    return {
      action: 'close-and-open',
      reason: `the open session is dated ${session.on}, which is after today`,
      staleFor,
      answered,
      unresolved,
    };
  }
  return {
    action: 'close-and-open',
    reason: `the open session is ${staleFor} days old and its buried twins no longer apply`,
    staleFor,
    answered,
    unresolved,
  };
}

export function shouldResume(plan: ResumePlan): boolean {
  return plan.action === 'resume';
}

export function needsClosing(plan: ResumePlan): boolean {
  return plan.action === 'close-and-open';
}

/**
 * Picks the session to resume out of several open ones. The one for today wins,
 * and among several for today the one with the most answers, because that is
 * the one the learner was actually working in.
 */
export function pickResumable(
  sessions: readonly Session[],
  deckKey: string,
  on: CivilDate,
): Session | undefined {
  const candidates = sessions.filter(
    (session) => !session.closed && session.deckKey === deckKey && session.on === on,
  );
  return [...candidates].sort(
    (a, b) => sessionLength(b) - sessionLength(a) || a.id.localeCompare(b.id),
  )[0];
}

export function staleSessions(sessions: readonly Session[], on: CivilDate): Session[] {
  return sessions.filter((session) => !session.closed && daysBetween(session.on, on) !== 0);
}

export function describeResume(plan: ResumePlan): string {
  if (plan.unresolved.length === 0) {
    return plan.reason;
  }
  return `${plan.reason}, ${plan.unresolved.length} cells left unresolved`;
}
