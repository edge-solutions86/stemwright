import type { Assignment } from '../domain/course/assignment.js';
import type { Course } from '../domain/course/course.js';
import type { Enrolment } from '../domain/course/enrolment.js';
import type { Deck } from '../domain/drill/deck.js';
import type { Session } from '../domain/drill/session.js';
import type { FeatureSchema } from '../domain/features/schema.js';
import type { ClassRegistry } from '../domain/lexicon/inflection-class.js';
import type { Lexeme } from '../domain/lexicon/lexeme.js';
import type { Attempt } from '../domain/mastery/attempt.js';
import type { MasteryLedger } from '../domain/mastery/ledger.js';
import type { OrthoProfile } from '../domain/ortho/tolerance.js';
import type { ProfileBook } from '../domain/paradigm/book.js';
import type { RuleSet } from '../domain/paradigm/realise.js';
import type { CivilDate } from '../domain/time/civil.js';
import type { DayRange } from '../domain/time/window.js';

/**
 * The ports the services need and the infrastructure fills. Everything here is
 * an interface, and everything is asynchronous even where an in memory adapter
 * answers instantly, because the moment one of them is not the callers have to
 * be rewritten rather than the adapter.
 *
 * Nothing here mentions http, a file system or a journal. That is the point:
 * the services are testable against a map and the persistence layer is
 * replaceable without touching them.
 */
export interface Clock {
  now(): Date;
  today(): CivilDate;
}

export interface LanguageBundle {
  readonly language: string;
  readonly schema: FeatureSchema;
  readonly classes: ClassRegistry;
  readonly ortho: OrthoProfile;
  readonly rules: RuleSet;
}

export interface LanguageStore {
  get(language: string): Promise<LanguageBundle | undefined>;
  list(): Promise<string[]>;
  put(bundle: LanguageBundle): Promise<void>;
}

export interface LexemeStore {
  get(id: string): Promise<Lexeme | undefined>;
  listByLanguage(language: string): Promise<Lexeme[]>;
  put(entry: Lexeme): Promise<void>;
  remove(id: string): Promise<void>;
}

export interface BookStore {
  get(language: string): Promise<ProfileBook | undefined>;
  put(book: ProfileBook): Promise<void>;
}

export interface DeckStore {
  get(language: string, key: string): Promise<Deck | undefined>;
  listByLanguage(language: string): Promise<Deck[]>;
  put(entry: Deck): Promise<void>;
  remove(language: string, key: string): Promise<void>;
}

export interface LedgerStore {
  get(learnerId: string): Promise<MasteryLedger | undefined>;
  put(ledger: MasteryLedger): Promise<void>;
}

export interface AttemptStore {
  append(entries: readonly Attempt[]): Promise<void>;
  listByLearner(learnerId: string, range?: DayRange): Promise<Attempt[]>;
  listBySession(sessionId: string): Promise<Attempt[]>;
}

export interface SessionStore {
  get(id: string): Promise<Session | undefined>;
  put(session: Session): Promise<void>;
  listOpen(learnerId: string): Promise<Session[]>;
}

export interface CourseStore {
  get(key: string): Promise<Course | undefined>;
  list(): Promise<Course[]>;
  put(entry: Course): Promise<void>;
  enrolments(courseKey: string): Promise<Enrolment[]>;
  putEnrolment(enrolment: Enrolment): Promise<void>;
  assignments(courseKey: string): Promise<Assignment[]>;
  putAssignment(entry: Assignment): Promise<void>;
}

export interface Stores {
  readonly languages: LanguageStore;
  readonly lexemes: LexemeStore;
  readonly books: BookStore;
  readonly decks: DeckStore;
  readonly ledgers: LedgerStore;
  readonly attempts: AttemptStore;
  readonly sessions: SessionStore;
  readonly courses: CourseStore;
}

/** A fixed clock, which every test uses and no production wiring does. */
export function fixedClock(instant: Date, day: CivilDate): Clock {
  return {
    now: () => new Date(instant.getTime()),
    today: () => day,
  };
}

export function systemClock(): Clock {
  return {
    now: () => new Date(),
    today: () => {
      const now = new Date();
      const year = String(now.getUTCFullYear()).padStart(4, '0');
      const month = String(now.getUTCMonth() + 1).padStart(2, '0');
      const day = String(now.getUTCDate()).padStart(2, '0');
      return `${year}-${month}-${day}` as CivilDate;
    },
  };
}

/**
 * A clock that steps forward only when told to. Useful for a simulation and for
 * a test that wants several days to pass without waiting for them.
 */
export function steppableClock(
  start: Date,
  day: CivilDate,
): Clock & {
  set(next: Date, nextDay: CivilDate): void;
} {
  let instant = start;
  let current = day;
  return {
    now: () => new Date(instant.getTime()),
    today: () => current,
    set(next: Date, nextDay: CivilDate) {
      instant = next;
      current = nextDay;
    },
  };
}
