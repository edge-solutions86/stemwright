import type {
  AttemptStore,
  BookStore,
  CourseStore,
  DeckStore,
  LanguageBundle,
  LanguageStore,
  LedgerStore,
  LexemeStore,
  SessionStore,
  Stores,
} from '../app/ports.js';
import type { Assignment } from '../domain/course/assignment.js';
import type { Course } from '../domain/course/course.js';
import type { Enrolment } from '../domain/course/enrolment.js';
import type { Deck } from '../domain/drill/deck.js';
import type { Session } from '../domain/drill/session.js';
import type { Attempt } from '../domain/mastery/attempt.js';
import { compareAttempts } from '../domain/mastery/attempt.js';
import type { Lexeme } from '../domain/lexicon/lexeme.js';
import type { MasteryLedger } from '../domain/mastery/ledger.js';
import type { ProfileBook } from '../domain/paradigm/book.js';
import type { DayRange } from '../domain/time/window.js';
import { rangeContains } from '../domain/time/window.js';

/**
 * Stores backed by plain maps. Every service test runs against these, and a
 * single process deployment can run against them too by loading a snapshot at
 * start up and writing one at shutdown.
 *
 * Everything is asynchronous even though nothing here waits, so that swapping a
 * real store in later is a wiring change rather than a rewrite of every caller.
 */
export function memoryLanguageStore(): LanguageStore {
  const byLanguage = new Map<string, LanguageBundle>();
  return {
    get: (language) => Promise.resolve(byLanguage.get(language)),
    list: () => Promise.resolve([...byLanguage.keys()].sort()),
    put: (bundle) => {
      byLanguage.set(bundle.language, bundle);
      return Promise.resolve();
    },
  };
}

export function memoryLexemeStore(): LexemeStore {
  const byId = new Map<string, Lexeme>();
  return {
    get: (id) => Promise.resolve(byId.get(id)),
    listByLanguage: (language) =>
      Promise.resolve(
        [...byId.values()]
          .filter((entry) => entry.language === language)
          .sort((a, b) => a.lemma.localeCompare(b.lemma) || a.homograph - b.homograph),
      ),
    put: (entry) => {
      byId.set(entry.id, entry);
      return Promise.resolve();
    },
    remove: (id) => {
      byId.delete(id);
      return Promise.resolve();
    },
  };
}

export function memoryBookStore(): BookStore {
  const byLanguage = new Map<string, ProfileBook>();
  return {
    get: (language) => Promise.resolve(byLanguage.get(language)),
    put: (book) => {
      byLanguage.set(book.language, book);
      return Promise.resolve();
    },
  };
}

export function memoryDeckStore(): DeckStore {
  const byKey = new Map<string, Deck>();
  const compose = (language: string, key: string) => `${language}/${key}`;
  return {
    get: (language, key) => Promise.resolve(byKey.get(compose(language, key))),
    listByLanguage: (language) =>
      Promise.resolve(
        [...byKey.values()]
          .filter((entry) => entry.language === language)
          .sort((a, b) => a.key.localeCompare(b.key)),
      ),
    put: (entry) => {
      byKey.set(compose(entry.language, entry.key), entry);
      return Promise.resolve();
    },
    remove: (language, key) => {
      byKey.delete(compose(language, key));
      return Promise.resolve();
    },
  };
}

export function memoryLedgerStore(): LedgerStore {
  const byLearner = new Map<string, MasteryLedger>();
  return {
    get: (learnerId) => Promise.resolve(byLearner.get(learnerId)),
    put: (ledger) => {
      byLearner.set(ledger.learnerId, ledger);
      return Promise.resolve();
    },
  };
}

export function memoryAttemptStore(): AttemptStore {
  const entries: Attempt[] = [];
  return {
    append: (incoming) => {
      entries.push(...incoming);
      return Promise.resolve();
    },
    listByLearner: (learnerId: string, range?: DayRange) =>
      Promise.resolve(
        entries
          .filter((entry) => entry.learnerId === learnerId)
          .filter((entry) => range === undefined || rangeContains(range, entry.on))
          .sort(compareAttempts),
      ),
    listBySession: (sessionId) =>
      Promise.resolve(
        entries.filter((entry) => entry.sessionId === sessionId).sort(compareAttempts),
      ),
  };
}

export function memorySessionStore(): SessionStore {
  const byId = new Map<string, Session>();
  return {
    get: (id) => Promise.resolve(byId.get(id)),
    put: (session) => {
      byId.set(session.id, session);
      return Promise.resolve();
    },
    listOpen: (learnerId) =>
      Promise.resolve(
        [...byId.values()].filter((session) => session.learnerId === learnerId && !session.closed),
      ),
  };
}

export function memoryCourseStore(): CourseStore {
  const byKey = new Map<string, Course>();
  const enrolled = new Map<string, Enrolment[]>();
  const assigned = new Map<string, Assignment[]>();

  return {
    get: (key) => Promise.resolve(byKey.get(key)),
    list: () => Promise.resolve([...byKey.values()].sort((a, b) => a.key.localeCompare(b.key))),
    put: (entry) => {
      byKey.set(entry.key, entry);
      return Promise.resolve();
    },
    enrolments: (courseKey) => Promise.resolve([...(enrolled.get(courseKey) ?? [])]),
    putEnrolment: (enrolment) => {
      const existing = enrolled.get(enrolment.courseKey) ?? [];
      const kept = existing.filter((row) => row.learnerId !== enrolment.learnerId);
      enrolled.set(enrolment.courseKey, [...kept, enrolment]);
      return Promise.resolve();
    },
    assignments: (courseKey) => Promise.resolve([...(assigned.get(courseKey) ?? [])]),
    putAssignment: (entry) => {
      const existing = assigned.get(entry.courseKey) ?? [];
      const kept = existing.filter((row) => row.key !== entry.key);
      assigned.set(entry.courseKey, [...kept, entry]);
      return Promise.resolve();
    },
  };
}

export function memoryStores(): Stores {
  return {
    languages: memoryLanguageStore(),
    lexemes: memoryLexemeStore(),
    books: memoryBookStore(),
    decks: memoryDeckStore(),
    ledgers: memoryLedgerStore(),
    attempts: memoryAttemptStore(),
    sessions: memorySessionStore(),
    courses: memoryCourseStore(),
  };
}
