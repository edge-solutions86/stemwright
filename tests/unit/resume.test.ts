import { describe, expect, it } from 'vitest';
import { NOTHING_BURIED } from '../../src/domain/drill/burial.js';
import {
  describeResume,
  needsClosing,
  pickResumable,
  planResume,
  shouldResume,
  staleSessions,
} from '../../src/domain/drill/resume.js';
import { closeSession, openSession, recordAttempt } from '../../src/domain/drill/session.js';
import type { Session } from '../../src/domain/drill/session.js';
import { attempt } from '../../src/domain/mastery/attempt.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';

const d = (value: string) => parseCivilDate(value);
const TODAY = d('2025-03-03');

const withAnswers = (session: Session, count: number, verdict: 'exact' | 'wrong' = 'exact') => {
  let grown = session;
  for (let index = 0; index < count; index += 1) {
    grown = recordAttempt(
      grown,
      attempt({
        learnerId: session.learnerId,
        sessionId: session.id,
        cellId: `cell_${index}`,
        deckKey: session.deckKey,
        on: session.on,
        sequence: index + 1,
        given: verdict === 'exact' ? 'x' : 'y',
        expected: 'x',
        verdict,
        toleranceLevel: 'standard',
        revisionId: 'rev_a',
      }),
      NOTHING_BURIED,
    );
  }
  return grown;
};

describe('planResume', () => {
  it('opens a new session when there is none', () => {
    const plan = planResume(undefined, TODAY);
    expect(plan.action).toBe('open');
    expect(plan.reason).toBe('there is no session open');
  });

  it('resumes a session from today', () => {
    const session = withAnswers(openSession('lrn_one', 'all', TODAY), 3);
    const plan = planResume(session, TODAY);
    expect(shouldResume(plan)).toBe(true);
    expect(plan.answered).toBe(3);
    expect(plan.staleFor).toBe(0);
  });

  it('closes a session from a previous day', () => {
    const session = withAnswers(openSession('lrn_one', 'all', d('2025-03-01')), 2);
    const plan = planResume(session, TODAY);
    expect(needsClosing(plan)).toBe(true);
    expect(plan.staleFor).toBe(2);
    expect(plan.reason).toContain('buried twins no longer apply');
  });

  it('closes a session dated after today', () => {
    const session = openSession('lrn_one', 'all', d('2025-03-05'));
    const plan = planResume(session, TODAY);
    expect(needsClosing(plan)).toBe(true);
    expect(plan.reason).toContain('after today');
  });

  it('opens a new one when the session it was given is already closed', () => {
    const session = closeSession(withAnswers(openSession('lrn_one', 'all', TODAY), 1));
    expect(planResume(session, TODAY).action).toBe('open');
  });

  it('reports the cells left unresolved', () => {
    const session = withAnswers(openSession('lrn_one', 'all', TODAY), 2, 'wrong');
    expect(planResume(session, TODAY).unresolved).toHaveLength(2);
  });

  it('reports nothing unresolved for a clean session', () => {
    const session = withAnswers(openSession('lrn_one', 'all', TODAY), 2);
    expect(planResume(session, TODAY).unresolved).toEqual([]);
  });
});

describe('pickResumable', () => {
  it('takes the session for today and this deck', () => {
    const wanted = openSession('lrn_one', 'all', TODAY);
    const other = openSession('lrn_one', 'core', TODAY);
    const yesterday = openSession('lrn_one', 'all', d('2025-03-02'));
    expect(pickResumable([other, yesterday, wanted], 'all', TODAY)?.id).toBe(wanted.id);
  });

  it('takes the one with the most answers when there are two', () => {
    const thin = withAnswers(openSession('lrn_one', 'all', TODAY, 1), 1);
    const thick = withAnswers(openSession('lrn_one', 'all', TODAY, 2), 4);
    expect(pickResumable([thin, thick], 'all', TODAY)?.id).toBe(thick.id);
  });

  it('ignores a closed session', () => {
    const closed = closeSession(openSession('lrn_one', 'all', TODAY));
    expect(pickResumable([closed], 'all', TODAY)).toBeUndefined();
  });

  it('gives nothing when there is nothing to resume', () => {
    expect(pickResumable([], 'all', TODAY)).toBeUndefined();
  });
});

describe('staleSessions', () => {
  it('names every open session not dated today', () => {
    const today = openSession('lrn_one', 'all', TODAY);
    const old = openSession('lrn_one', 'core', d('2025-02-28'));
    const closed = closeSession(openSession('lrn_one', 'all', d('2025-02-27')));
    const stale = staleSessions([today, old, closed], TODAY);
    expect(stale.map((session) => session.deckKey)).toEqual(['core']);
  });

  it('names none when everything is from today', () => {
    expect(staleSessions([openSession('lrn_one', 'all', TODAY)], TODAY)).toEqual([]);
  });
});

describe('describeResume', () => {
  it('reads the reason out', () => {
    const session = withAnswers(openSession('lrn_one', 'all', TODAY), 2);
    expect(describeResume(planResume(session, TODAY))).toBe(
      'resuming a session of 2 answers from today',
    );
  });

  it('counts the unresolved cells', () => {
    const session = withAnswers(openSession('lrn_one', 'all', TODAY), 2, 'wrong');
    expect(describeResume(planResume(session, TODAY))).toContain('2 cells left unresolved');
  });
});
