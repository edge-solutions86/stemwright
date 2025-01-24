import { NotFoundError } from '../domain/errors.js';
import type { Course } from '../domain/course/course.js';
import type { CourseScope } from '../domain/course/authz.js';
import { roster } from '../domain/course/enrolment.js';
import type { Deck } from '../domain/drill/deck.js';
import type { DrillContext } from '../domain/drill/membership.js';
import { buildLexicon } from '../domain/lexicon/lexicon.js';
import type { ProfileBook } from '../domain/paradigm/book.js';
import type { LanguageBundle, Stores } from './ports.js';

/**
 * Loading. Five services had their own copy of "fetch it and throw a
 * NotFoundError if it is missing", and three of them named the missing thing
 * differently, so the same failure reached the http layer under three
 * different messages.
 *
 * Every loader here throws rather than returning undefined. A service that
 * wants the absence has the store underneath it and can ask directly.
 */
export async function requireLanguage(stores: Stores, language: string): Promise<LanguageBundle> {
  const found = await stores.languages.get(language);
  if (found === undefined) {
    throw new NotFoundError('language', language);
  }
  return found;
}

export async function requireBook(stores: Stores, language: string): Promise<ProfileBook> {
  const found = await stores.books.get(language);
  if (found === undefined) {
    throw new NotFoundError('profile book', language);
  }
  return found;
}

export async function requireDeck(stores: Stores, language: string, key: string): Promise<Deck> {
  const found = await stores.decks.get(language, key);
  if (found === undefined) {
    throw new NotFoundError('deck', `${language}/${key}`);
  }
  return found;
}

export async function requireCourse(stores: Stores, key: string): Promise<Course> {
  const found = await stores.courses.get(key);
  if (found === undefined) {
    throw new NotFoundError('course', key);
  }
  return found;
}

export async function requireScope(stores: Stores, courseKey: string): Promise<CourseScope> {
  const course = await requireCourse(stores, courseKey);
  return { course, roster: roster(course, await stores.courses.enrolments(courseKey)) };
}

export async function loadContext(stores: Stores, language: string): Promise<DrillContext> {
  const bundle = await requireLanguage(stores, language);
  const book = await requireBook(stores, language);
  return {
    schema: bundle.schema,
    classes: bundle.classes,
    book,
    lexicon: buildLexicon(language, await stores.lexemes.listByLanguage(language)),
  };
}
