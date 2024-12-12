import { describe, expect, it } from 'vitest';
import { fixedClock, steppableClock, systemClock } from '../../src/app/ports.js';
import { assignment } from '../../src/domain/course/assignment.js';
import { enrol } from '../../src/domain/course/enrolment.js';
import { deck } from '../../src/domain/drill/deck.js';
import { closeSession, openSession } from '../../src/domain/drill/session.js';
import { attempt } from '../../src/domain/mastery/attempt.js';
import { emptyLedger } from '../../src/domain/mastery/ledger.js';
import { memoryStores } from '../../src/infra/memory.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { dayRange } from '../../src/domain/time/window.js';
import { michaelmas } from '../support/course.js';
import { latinBook } from '../support/book.js';
import { bellum, puella } from '../support/drill.js';
import { latinNoun } from '../support/profiles.js';

const d = (value: string) => parseCivilDate(value);

describe('clocks', () => {
  it('a fixed clock never moves', () => {
    const clock = fixedClock(new Date('2025-03-03T09:00:00Z'), d('2025-03-03'));
    expect(clock.today()).toBe('2025-03-03');
    expect(clock.now().toISOString()).toBe('2025-03-03T09:00:00.000Z');
  });

  it('a fixed clock hands out a copy of its instant', () => {
    const clock = fixedClock(new Date('2025-03-03T09:00:00Z'), d('2025-03-03'));
    clock.now().setUTCFullYear(1999);
    expect(clock.now().getUTCFullYear()).toBe(2025);
  });

  it('a steppable clock moves only when told', () => {
    const clock = steppableClock(new Date('2025-03-03T09:00:00Z'), d('2025-03-03'));
    expect(clock.today()).toBe('2025-03-03');
    clock.set(new Date('2025-03-04T09:00:00Z'), d('2025-03-04'));
    expect(clock.today()).toBe('2025-03-04');
  });

  it('the system clock reports an iso day', () => {
    expect(systemClock().today()).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
});

describe('lexeme, book, deck and ledger stores', () => {
  it('round trips a lexeme and lists by language', async () => {
    const stores = memoryStores();
    await stores.lexemes.put(puella);
    await stores.lexemes.put(bellum);
    expect((await stores.lexemes.get(puella.id))?.lemma).toBe('puella');
    expect((await stores.lexemes.listByLanguage('latin')).map((e) => e.lemma)).toEqual([
      'bellum',
      'puella',
    ]);
    expect(await stores.lexemes.listByLanguage('greek')).toEqual([]);
  });

  it('removes a lexeme', async () => {
    const stores = memoryStores();
    await stores.lexemes.put(puella);
    await stores.lexemes.remove(puella.id);
    expect(await stores.lexemes.get(puella.id)).toBeUndefined();
  });

  it('round trips a profile book by language', async () => {
    const stores = memoryStores();
    await stores.books.put(latinBook);
    expect((await stores.books.get('latin'))?.histories.size).toBe(2);
    expect(await stores.books.get('greek')).toBeUndefined();
  });

  it('keys decks by language and key', async () => {
    const stores = memoryStores();
    await stores.decks.put(deck(latinNoun, { key: 'all' }));
    expect((await stores.decks.get('latin', 'all'))?.key).toBe('all');
    expect(await stores.decks.get('greek', 'all')).toBeUndefined();
    await stores.decks.remove('latin', 'all');
    expect(await stores.decks.listByLanguage('latin')).toEqual([]);
  });

  it('round trips a ledger by learner', async () => {
    const stores = memoryStores();
    await stores.ledgers.put(emptyLedger('lrn_one'));
    expect((await stores.ledgers.get('lrn_one'))?.learnerId).toBe('lrn_one');
    expect(await stores.ledgers.get('lrn_two')).toBeUndefined();
  });
});

describe('attempt, session and course stores', () => {
  const makeAttempt = (cellId: string, on: string, sequence: number, sessionId = 'sess_a') =>
    attempt({
      learnerId: 'lrn_one',
      sessionId,
      cellId,
      deckKey: 'all',
      on: d(on),
      sequence,
      given: 'x',
      expected: 'x',
      verdict: 'exact',
      toleranceLevel: 'standard',
      revisionId: 'rev_a',
    });

  it('appends attempts and reads them back in order', async () => {
    const stores = memoryStores();
    await stores.attempts.append([
      makeAttempt('cell_b', '2024-10-22', 2),
      makeAttempt('cell_a', '2024-10-21', 1),
    ]);
    const found = await stores.attempts.listByLearner('lrn_one');
    expect(found.map((entry) => entry.cellId)).toEqual(['cell_a', 'cell_b']);
  });

  it('filters attempts by range and by session', async () => {
    const stores = memoryStores();
    await stores.attempts.append([
      makeAttempt('cell_a', '2024-10-21', 1),
      makeAttempt('cell_b', '2024-10-25', 1, 'sess_b'),
    ]);
    const range = dayRange(d('2024-10-24'), d('2024-10-26'));
    expect(await stores.attempts.listByLearner('lrn_one', range)).toHaveLength(1);
    expect(await stores.attempts.listBySession('sess_b')).toHaveLength(1);
    expect(await stores.attempts.listByLearner('lrn_two')).toEqual([]);
  });

  it('lists only the open sessions of a learner', async () => {
    const stores = memoryStores();
    const open = openSession('lrn_one', 'all', d('2024-10-21'));
    const shut = closeSession(openSession('lrn_one', 'core', d('2024-10-21')));
    await stores.sessions.put(open);
    await stores.sessions.put(shut);
    expect(await stores.sessions.listOpen('lrn_one')).toHaveLength(1);
    expect(await stores.sessions.listOpen('lrn_two')).toEqual([]);
    expect((await stores.sessions.get(open.id))?.deckKey).toBe('all');
  });

  it('replaces an enrolment rather than adding a second one', async () => {
    const stores = memoryStores();
    await stores.courses.put(michaelmas);
    await stores.courses.putEnrolment(enrol(michaelmas, { learnerId: 'lrn_one' }));
    await stores.courses.putEnrolment(enrol(michaelmas, { learnerId: 'lrn_one', role: 'auditor' }));
    const found = await stores.courses.enrolments('latin-1a');
    expect(found).toHaveLength(1);
    expect(found[0]?.role).toBe('auditor');
  });

  it('replaces an assignment under the same key', async () => {
    const stores = memoryStores();
    const build = (practiceDays: number) =>
      assignment(latinNoun, michaelmas, {
        key: 'week-3',
        deckKey: 'all',
        from: d('2024-10-21'),
        to: d('2024-10-27'),
        practiceDays,
      });
    await stores.courses.putAssignment(build(3));
    await stores.courses.putAssignment(build(5));
    const found = await stores.courses.assignments('latin-1a');
    expect(found).toHaveLength(1);
    expect(found[0]?.target.practiceDays).toBe(5);
  });

  it('hands out a copy of the enrolment list', async () => {
    const stores = memoryStores();
    await stores.courses.putEnrolment(enrol(michaelmas, { learnerId: 'lrn_one' }));
    const found = await stores.courses.enrolments('latin-1a');
    found.pop();
    expect(await stores.courses.enrolments('latin-1a')).toHaveLength(1);
  });

  it('lists courses in key order', async () => {
    const stores = memoryStores();
    await stores.courses.put(michaelmas);
    expect((await stores.courses.list()).map((entry) => entry.key)).toEqual(['latin-1a']);
    expect(await stores.courses.get('nowhere')).toBeUndefined();
  });
});
