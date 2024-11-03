import { ProblemCollector } from '../errors.js';
import type { Id } from '../identifiers.js';
import { asSlug, derivedId } from '../identifiers.js';
import { requireText } from '../text/normalise.js';
import type { CivilDate } from '../time/civil.js';
import type { DayRange } from '../time/window.js';
import { dayRange, rangeContains, rangeLength } from '../time/window.js';

/**
 * A course is a term of teaching: a language, a window of dates, a set of decks
 * and the people in it. It is the layer where a department's decisions live, so
 * that the drill layer underneath can stay ignorant of terms and cohorts.
 *
 * The term window is closed at both ends and it is load bearing. An assignment
 * cannot fall outside it, a shielded day outside it means nothing, and a report
 * run after it ends is a historical report rather than a live one.
 */
export interface Course {
  readonly id: Id<'course'>;
  readonly key: string;
  readonly title: string;
  readonly language: string;
  readonly term: DayRange;
  readonly deckKeys: readonly string[];
  readonly instructorIds: readonly string[];
  readonly note: string | undefined;
}

export interface CourseInput {
  readonly key: string;
  readonly title?: string;
  readonly language: string;
  readonly from: CivilDate;
  readonly to: CivilDate;
  readonly deckKeys?: readonly string[];
  readonly instructorIds?: readonly string[];
  readonly note?: string;
}

export function courseIdFor(language: string, key: string): Id<'course'> {
  return derivedId('course', language, key);
}

export function course(input: CourseInput): Course {
  const problems = new ProblemCollector();
  const key = asSlug('course.key', input.key);
  const language = asSlug('course.language', input.language);

  const term = problems.absorbFlat('term', () => dayRange(input.from, input.to));

  const deckKeys = (input.deckKeys ?? []).map((value, index) => asSlug(`deckKeys.${index}`, value));
  if (new Set(deckKeys).size !== deckKeys.length) {
    problems.add('deckKeys', 'names the same deck twice');
  }

  const instructorIds = [...(input.instructorIds ?? [])];
  if (new Set(instructorIds).size !== instructorIds.length) {
    problems.add('instructorIds', 'names the same instructor twice');
  }
  if (instructorIds.length === 0) {
    problems.add('instructorIds', 'must name at least one instructor');
  }

  problems.throwIfAny(`course ${key} is not usable`);

  return {
    id: courseIdFor(language, key),
    key,
    title: input.title === undefined ? key : requireText('course.title', input.title, 160),
    language,
    term: term!,
    deckKeys,
    instructorIds,
    note: input.note === undefined ? undefined : requireText('course.note', input.note, 800),
  };
}

export function isRunning(entry: Course, on: CivilDate): boolean {
  return rangeContains(entry.term, on);
}

export function termLength(entry: Course): number {
  return rangeLength(entry.term);
}

export function teaches(entry: Course, deckKey: string): boolean {
  return entry.deckKeys.includes(deckKey);
}

export function isInstructor(entry: Course, actorId: string): boolean {
  return entry.instructorIds.includes(actorId);
}

export function withDeck(entry: Course, deckKey: string): Course {
  return course({
    key: entry.key,
    title: entry.title,
    language: entry.language,
    from: entry.term.from,
    to: entry.term.to,
    deckKeys: [...entry.deckKeys, deckKey],
    instructorIds: entry.instructorIds,
    ...(entry.note === undefined ? {} : { note: entry.note }),
  });
}

export function withoutDeck(entry: Course, deckKey: string): Course {
  return course({
    key: entry.key,
    title: entry.title,
    language: entry.language,
    from: entry.term.from,
    to: entry.term.to,
    deckKeys: entry.deckKeys.filter((candidate) => candidate !== deckKey),
    instructorIds: entry.instructorIds,
    ...(entry.note === undefined ? {} : { note: entry.note }),
  });
}

export function describeCourse(entry: Course): string {
  const decks = entry.deckKeys.length === 0 ? 'no decks yet' : `${entry.deckKeys.length} decks`;
  return `${entry.title} runs ${entry.term.from} to ${entry.term.to} with ${decks}`;
}
