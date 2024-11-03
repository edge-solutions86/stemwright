import { describe, expect, it } from 'vitest';
import {
  course,
  courseIdFor,
  describeCourse,
  isInstructor,
  isRunning,
  teaches,
  termLength,
  withDeck,
  withoutDeck,
} from '../../src/domain/course/course.js';
import { isId } from '../../src/domain/identifiers.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { problemMessages } from '../support/problems.js';

const d = (value: string) => parseCivilDate(value);

const michaelmas = course({
  key: 'latin-1a',
  title: 'Latin 1A',
  language: 'latin',
  from: d('2024-10-07'),
  to: d('2024-12-13'),
  deckKeys: ['first-year', 'core-vocabulary'],
  instructorIds: ['lrn_teacher'],
  note: 'first term, two contact hours a week',
});

describe('course', () => {
  it('keeps the key, title, language and term', () => {
    expect(michaelmas.key).toBe('latin-1a');
    expect(michaelmas.title).toBe('Latin 1A');
    expect(michaelmas.language).toBe('latin');
    expect(michaelmas.term).toEqual({ from: '2024-10-07', to: '2024-12-13' });
    expect(isId('course', michaelmas.id)).toBe(true);
  });

  it('derives the id from the language and the key', () => {
    expect(michaelmas.id).toBe(courseIdFor('latin', 'latin-1a'));
  });

  it('defaults the title to the key', () => {
    const bare = course({
      key: 'greek-2',
      language: 'greek',
      from: d('2025-01-06'),
      to: d('2025-03-14'),
      instructorIds: ['lrn_teacher'],
    });
    expect(bare.title).toBe('greek-2');
    expect(bare.deckKeys).toEqual([]);
  });

  it('refuses a term that ends before it starts', () => {
    expect(
      problemMessages(() =>
        course({
          key: 'x',
          language: 'latin',
          from: d('2024-12-13'),
          to: d('2024-10-07'),
          instructorIds: ['lrn_teacher'],
        }),
      ),
    ).toEqual(['term range starts after it ends: 2024-12-13 to 2024-10-07']);
  });

  it('refuses a course with no instructor', () => {
    expect(
      problemMessages(() =>
        course({ key: 'x', language: 'latin', from: d('2024-10-07'), to: d('2024-12-13') }),
      ),
    ).toEqual(['instructorIds must name at least one instructor']);
  });

  it('refuses a repeated deck or instructor', () => {
    expect(
      problemMessages(() =>
        course({
          key: 'x',
          language: 'latin',
          from: d('2024-10-07'),
          to: d('2024-12-13'),
          deckKeys: ['a', 'a'],
          instructorIds: ['lrn_teacher'],
        }),
      ),
    ).toEqual(['deckKeys names the same deck twice']);
    expect(
      problemMessages(() =>
        course({
          key: 'x',
          language: 'latin',
          from: d('2024-10-07'),
          to: d('2024-12-13'),
          instructorIds: ['lrn_teacher', 'lrn_teacher'],
        }),
      ),
    ).toEqual(['instructorIds names the same instructor twice']);
  });
});

describe('isRunning', () => {
  it('is true on both endpoints', () => {
    expect(isRunning(michaelmas, d('2024-10-07'))).toBe(true);
    expect(isRunning(michaelmas, d('2024-12-13'))).toBe(true);
  });

  it('is false either side', () => {
    expect(isRunning(michaelmas, d('2024-10-06'))).toBe(false);
    expect(isRunning(michaelmas, d('2024-12-14'))).toBe(false);
  });
});

describe('termLength', () => {
  it('counts both ends in', () => {
    expect(termLength(michaelmas)).toBe(68);
  });
});

describe('teaches and isInstructor', () => {
  it('answers whether a deck belongs to the course', () => {
    expect(teaches(michaelmas, 'first-year')).toBe(true);
    expect(teaches(michaelmas, 'third-year')).toBe(false);
  });

  it('answers whether somebody teaches it', () => {
    expect(isInstructor(michaelmas, 'lrn_teacher')).toBe(true);
    expect(isInstructor(michaelmas, 'lrn_student')).toBe(false);
  });
});

describe('withDeck and withoutDeck', () => {
  it('adds a deck and leaves the original alone', () => {
    const grown = withDeck(michaelmas, 'inscriptions');
    expect(teaches(grown, 'inscriptions')).toBe(true);
    expect(teaches(michaelmas, 'inscriptions')).toBe(false);
  });

  it('refuses to add a deck that is already there', () => {
    expect(problemMessages(() => withDeck(michaelmas, 'first-year'))).toEqual([
      'deckKeys names the same deck twice',
    ]);
  });

  it('removes a deck and tolerates one that was never there', () => {
    expect(withoutDeck(michaelmas, 'first-year').deckKeys).toEqual(['core-vocabulary']);
    expect(withoutDeck(michaelmas, 'nowhere').deckKeys).toHaveLength(2);
  });

  it('keeps the note through an edit', () => {
    expect(withDeck(michaelmas, 'inscriptions').note).toBe('first term, two contact hours a week');
  });
});

describe('describeCourse', () => {
  it('reads the term and the deck count out', () => {
    expect(describeCourse(michaelmas)).toBe('Latin 1A runs 2024-10-07 to 2024-12-13 with 2 decks');
  });

  it('says so when there are no decks', () => {
    const bare = course({
      key: 'greek-2',
      language: 'greek',
      from: d('2025-01-06'),
      to: d('2025-03-14'),
      instructorIds: ['lrn_teacher'],
    });
    expect(describeCourse(bare)).toBe('greek-2 runs 2025-01-06 to 2025-03-14 with no decks yet');
  });
});
