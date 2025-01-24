import { describe, expect, it } from 'vitest';
import {
  loadContext,
  requireBook,
  requireCourse,
  requireDeck,
  requireLanguage,
  requireScope,
} from '../../src/app/loaders.js';
import { enrol } from '../../src/domain/course/enrolment.js';
import { deck } from '../../src/domain/drill/deck.js';
import { NotFoundError } from '../../src/domain/errors.js';
import { memoryStores } from '../../src/infra/memory.js';
import { seededStores } from '../support/app.js';
import { michaelmas } from '../support/course.js';
import { latinNoun } from '../support/profiles.js';

describe('requireLanguage', () => {
  it('loads a bundle that is there', async () => {
    const stores = await seededStores();
    expect((await requireLanguage(stores, 'latin')).language).toBe('latin');
  });

  it('names the language it could not find', async () => {
    const stores = await seededStores();
    await expect(requireLanguage(stores, 'greek')).rejects.toThrow(/language greek was not found/);
  });
});

describe('requireBook', () => {
  it('loads a book that is there', async () => {
    const stores = await seededStores();
    expect((await requireBook(stores, 'latin')).language).toBe('latin');
  });

  it('names the book it could not find', async () => {
    const stores = memoryStores();
    await expect(requireBook(stores, 'latin')).rejects.toThrow(/profile book latin was not found/);
  });
});

describe('requireDeck', () => {
  it('loads a deck that is there', async () => {
    const stores = await seededStores();
    await stores.decks.put(deck(latinNoun, { key: 'all' }));
    expect((await requireDeck(stores, 'latin', 'all')).key).toBe('all');
  });

  it('names the language and the key it could not find', async () => {
    const stores = await seededStores();
    await expect(requireDeck(stores, 'latin', 'nowhere')).rejects.toThrow(
      /deck latin\/nowhere was not found/,
    );
  });
});

describe('requireCourse and requireScope', () => {
  it('loads a course and builds a scope with its roster', async () => {
    const stores = await seededStores();
    await stores.courses.put(michaelmas);
    await stores.courses.putEnrolment(enrol(michaelmas, { learnerId: 'lrn_one' }));
    expect((await requireCourse(stores, 'latin-1a')).key).toBe('latin-1a');
    const scope = await requireScope(stores, 'latin-1a');
    expect(scope.roster.enrolments).toHaveLength(1);
  });

  it('names the course it could not find', async () => {
    const stores = await seededStores();
    await expect(requireCourse(stores, 'nowhere')).rejects.toThrow(NotFoundError);
    await expect(requireScope(stores, 'nowhere')).rejects.toThrow(NotFoundError);
  });
});

describe('loadContext', () => {
  it('assembles the schema, classes, book and lexicon', async () => {
    const stores = await seededStores();
    const context = await loadContext(stores, 'latin');
    expect(context.schema.language).toBe('latin');
    expect(context.classes.language).toBe('latin');
    expect(context.book.histories.size).toBe(2);
    expect(context.lexicon.byId.size).toBe(4);
  });

  it('fails on the language before it reaches the book', async () => {
    const stores = memoryStores();
    await expect(loadContext(stores, 'latin')).rejects.toThrow(/language latin was not found/);
  });

  it('fails on the book when the language is configured without one', async () => {
    const stores = await seededStores();
    const bare = memoryStores();
    await bare.languages.put(await requireLanguage(stores, 'latin'));
    await expect(loadContext(bare, 'latin')).rejects.toThrow(/profile book/);
  });
});
