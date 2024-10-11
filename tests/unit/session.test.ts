import { describe, expect, it } from 'vitest';
import { NOTHING_BURIED } from '../../src/domain/drill/burial.js';
import {
  attemptsForCell,
  cellsSeen,
  closeSession,
  describeSession,
  nextSequence,
  openSession,
  recordAttempt,
  sessionIdFor,
  sessionLength,
  summarise,
  unresolvedCells,
} from '../../src/domain/drill/session.js';
import { attempt } from '../../src/domain/mastery/attempt.js';
import type { AttemptInput } from '../../src/domain/mastery/attempt.js';
import { ConflictError, ValidationError } from '../../src/domain/errors.js';
import { isId } from '../../src/domain/identifiers.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';

const d = (value: string) => parseCivilDate(value);
const DAY = d('2025-03-03');
const session = openSession('lrn_one', 'first-year', DAY);

const makeAttempt = (patch: Partial<AttemptInput> = {}) =>
  attempt({
    learnerId: 'lrn_one',
    sessionId: session.id,
    cellId: 'cell_aaaaaa',
    deckKey: 'first-year',
    on: DAY,
    sequence: 1,
    given: 'puellae',
    expected: 'puellae',
    verdict: 'exact',
    toleranceLevel: 'standard',
    revisionId: 'rev_aaaaaa',
    ...patch,
  });

describe('openSession', () => {
  it('starts empty and open', () => {
    expect(sessionLength(session)).toBe(0);
    expect(session.closed).toBe(false);
    expect(session.buried).toBe(NOTHING_BURIED);
    expect(isId('session', session.id)).toBe(true);
  });

  it('derives the id from the learner, deck, day and ordinal', () => {
    expect(session.id).toBe(sessionIdFor('lrn_one', 'first-year', DAY, 1));
    expect(openSession('lrn_one', 'first-year', DAY, 2).id).not.toBe(session.id);
  });

  it('lower cases the deck key', () => {
    expect(openSession('lrn_one', 'First-Year', DAY).deckKey).toBe('first-year');
  });
});

describe('recordAttempt', () => {
  it('appends and leaves the original alone', () => {
    const grown = recordAttempt(session, makeAttempt(), NOTHING_BURIED);
    expect(sessionLength(grown)).toBe(1);
    expect(sessionLength(session)).toBe(0);
  });

  it('expects the sequence to follow on', () => {
    expect(nextSequence(session)).toBe(1);
    const grown = recordAttempt(session, makeAttempt(), NOTHING_BURIED);
    expect(nextSequence(grown)).toBe(2);
  });

  it('refuses an attempt out of order', () => {
    expect(() => recordAttempt(session, makeAttempt({ sequence: 3 }), NOTHING_BURIED)).toThrow(
      /expected 1 and got 3/,
    );
  });

  it('refuses an attempt from another session', () => {
    const other = openSession('lrn_two', 'first-year', DAY);
    const stray = attempt({
      learnerId: 'lrn_two',
      sessionId: other.id,
      cellId: 'cell_aaaaaa',
      deckKey: 'first-year',
      on: DAY,
      sequence: 1,
      given: 'x',
      expected: 'y',
      verdict: 'wrong',
      toleranceLevel: 'standard',
      revisionId: 'rev_aaaaaa',
    });
    expect(() => recordAttempt(session, stray, NOTHING_BURIED)).toThrow(ValidationError);
  });

  it('refuses to record into a closed session', () => {
    const closed = closeSession(session);
    expect(() => recordAttempt(closed, makeAttempt(), NOTHING_BURIED)).toThrow(ConflictError);
  });
});

describe('closeSession', () => {
  it('closes an open session and is idempotent', () => {
    const closed = closeSession(session);
    expect(closed.closed).toBe(true);
    expect(closeSession(closed)).toBe(closed);
  });
});

describe('cellsSeen and attemptsForCell', () => {
  const filled = recordAttempt(
    recordAttempt(
      recordAttempt(session, makeAttempt({ sequence: 1 }), NOTHING_BURIED),
      makeAttempt({ sequence: 2, cellId: 'cell_bbbbbb' }),
      NOTHING_BURIED,
    ),
    makeAttempt({ sequence: 3, verdict: 'wrong', given: 'x' }),
    NOTHING_BURIED,
  );

  it('counts each cell once', () => {
    expect(cellsSeen(filled)).toEqual(['cell_aaaaaa', 'cell_bbbbbb']);
  });

  it('finds every attempt on one cell', () => {
    expect(attemptsForCell(filled, 'cell_aaaaaa')).toHaveLength(2);
    expect(attemptsForCell(filled, 'cell_zzzzzz')).toEqual([]);
  });
});

describe('summarise', () => {
  const filled = recordAttempt(
    recordAttempt(session, makeAttempt({ sequence: 1, elapsedMs: 900 }), NOTHING_BURIED),
    makeAttempt({ sequence: 2, verdict: 'wrong', given: 'x', elapsedMs: 1100 }),
    NOTHING_BURIED,
  );

  it('counts the verdicts and the distinct cells', () => {
    const summary = summarise(filled);
    expect(summary.tally.total).toBe(2);
    expect(summary.tally.correct).toBe(1);
    expect(summary.distinctCells).toBe(1);
    expect(summary.accuracy).toBe(0.5);
    expect(summary.medianElapsedMs).toBe(1000);
  });

  it('summarises an empty session without dividing by zero', () => {
    const summary = summarise(session);
    expect(summary.accuracy).toBe(0);
    expect(summary.medianElapsedMs).toBeUndefined();
  });

  it('carries the learner, deck and day through', () => {
    const summary = summarise(filled);
    expect(summary.learnerId).toBe('lrn_one');
    expect(summary.deckKey).toBe('first-year');
    expect(summary.on).toBe('2025-03-03');
  });
});

describe('unresolvedCells', () => {
  it('names a cell whose last answer was wrong', () => {
    const filled = recordAttempt(
      session,
      makeAttempt({ verdict: 'wrong', given: 'x' }),
      NOTHING_BURIED,
    );
    expect(unresolvedCells(filled)).toEqual(['cell_aaaaaa']);
  });

  it('drops a cell that was got right after being got wrong', () => {
    const filled = recordAttempt(
      recordAttempt(session, makeAttempt({ verdict: 'wrong', given: 'x' }), NOTHING_BURIED),
      makeAttempt({ sequence: 2 }),
      NOTHING_BURIED,
    );
    expect(unresolvedCells(filled)).toEqual([]);
  });

  it('names a cell left at a near miss', () => {
    const filled = recordAttempt(session, makeAttempt({ verdict: 'near' }), NOTHING_BURIED);
    expect(unresolvedCells(filled)).toEqual(['cell_aaaaaa']);
  });
});

describe('describeSession', () => {
  it('says so when nothing was answered', () => {
    expect(describeSession(summarise(session))).toBe('no answers on 2025-03-03');
  });

  it('reads the counts out', () => {
    const filled = recordAttempt(session, makeAttempt(), NOTHING_BURIED);
    expect(describeSession(summarise(filled))).toBe(
      '1 answers across 1 cells on 2025-03-03, 100 percent right',
    );
  });
});
