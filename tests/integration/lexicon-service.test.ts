import { describe, expect, it } from 'vitest';
import { createLexiconService } from '../../src/app/services/lexicon-service.js';
import type { Actor } from '../../src/domain/course/authz.js';
import { ForbiddenError, NotFoundError, ValidationError } from '../../src/domain/errors.js';
import { stemSet } from '../../src/domain/lexicon/stem.js';
import { lexiconSize } from '../../src/domain/lexicon/lexicon.js';
import { seededStores, testHarness } from '../support/app.js';
import { puella } from '../support/drill.js';

const teacher: Actor = { id: 'lrn_teacher', role: 'instructor' };
const student: Actor = { id: 'lrn_one', role: 'learner' };

const build = async () => {
  const stores = await seededStores();
  const { bus, clock } = testHarness();
  return { stores, bus, service: createLexiconService(stores, bus, clock) };
};

const nauta = {
  lemma: 'nauta',
  classKey: 'first-declension',
  stems: stemSet([{ role: 'base', form: 'naut' }]),
  glosses: ['sailor'],
  tags: ['core'],
};

describe('language', () => {
  it('loads the bundle it was seeded with', async () => {
    const { service } = await build();
    expect((await service.language('latin')).schema.language).toBe('latin');
  });

  it('refuses a language nobody has configured', async () => {
    const { service } = await build();
    await expect(service.language('greek')).rejects.toThrow(NotFoundError);
  });
});

describe('load and get', () => {
  it('builds a lexicon from what is stored', async () => {
    const { service } = await build();
    expect(lexiconSize(await service.load('latin'))).toBe(4);
  });

  it('reads one lexeme back', async () => {
    const { service } = await build();
    expect((await service.get(puella.id)).lemma).toBe('puella');
  });

  it('refuses an id nobody stored', async () => {
    const { service } = await build();
    await expect(service.get('lex_zzzzzz')).rejects.toThrow(NotFoundError);
  });
});

describe('add', () => {
  it('stores the lexeme and announces it', async () => {
    const { service, bus } = await build();
    const added = await service.add(teacher, 'latin', nauta);
    expect(added.lemma).toBe('nauta');
    expect(lexiconSize(await service.load('latin'))).toBe(5);
    expect(bus.ofKind('lexeme.added')).toHaveLength(1);
    expect(bus.ofKind('lexeme.added')[0]?.lexemeId).toBe(added.id);
  });

  it('refuses a learner', async () => {
    const { service, bus } = await build();
    await expect(service.add(student, 'latin', nauta)).rejects.toThrow(ForbiddenError);
    expect(bus.events).toEqual([]);
  });

  it('refuses a lexeme the domain rejects and stores nothing', async () => {
    const { service } = await build();
    await expect(
      service.add(teacher, 'latin', { ...nauta, classKey: 'fourth-declension' }),
    ).rejects.toThrow(ValidationError);
    expect(lexiconSize(await service.load('latin'))).toBe(4);
  });

  it('refuses a language nobody has configured', async () => {
    const { service } = await build();
    await expect(service.add(teacher, 'greek', nauta)).rejects.toThrow(NotFoundError);
  });
});

describe('replace and remove', () => {
  it('replaces an entry that is there', async () => {
    const { service } = await build();
    const changed = await service.replace(teacher, 'latin', {
      id: puella.id,
      lemma: 'puella',
      classKey: 'first-declension',
      stems: stemSet([{ role: 'base', form: 'puell' }]),
      glosses: ['girl', 'maiden'],
    });
    expect(changed.glosses).toEqual(['girl', 'maiden']);
    expect(lexiconSize(await service.load('latin'))).toBe(4);
  });

  it('refuses to replace something that is not stored', async () => {
    const { service } = await build();
    await expect(service.replace(teacher, 'latin', { ...nauta, id: 'lex_zzzzzz' })).rejects.toThrow(
      NotFoundError,
    );
  });

  it('removes an entry and refuses one that is not there', async () => {
    const { service } = await build();
    await service.remove(teacher, puella.id);
    expect(lexiconSize(await service.load('latin'))).toBe(3);
    await expect(service.remove(teacher, puella.id)).rejects.toThrow(NotFoundError);
  });

  it('refuses a learner on both', async () => {
    const { service } = await build();
    await expect(service.remove(student, puella.id)).rejects.toThrow(ForbiddenError);
  });
});

describe('search', () => {
  it('matches a lemma', async () => {
    const { service } = await build();
    expect((await service.search('latin', 'puel')).map((e) => e.lemma)).toEqual(['puella']);
  });

  it('matches a gloss', async () => {
    const { service } = await build();
    expect((await service.search('latin', 'field')).map((e) => e.lemma)).toEqual(['ager']);
  });

  it('ignores case and surrounding space', async () => {
    const { service } = await build();
    expect(await service.search('latin', '  PUELLA ')).toHaveLength(1);
  });

  it('gives nothing for a blank term', async () => {
    const { service } = await build();
    expect(await service.search('latin', '   ')).toEqual([]);
  });

  it('gives nothing when nothing matches', async () => {
    const { service } = await build();
    expect(await service.search('latin', 'zzzz')).toEqual([]);
  });
});

describe('byClass and byTag', () => {
  it('takes the descendants of a class with it', async () => {
    const { service } = await build();
    expect((await service.byClass('latin', 'second-declension')).map((e) => e.lemma)).toEqual([
      'ager',
      'bellum',
    ]);
  });

  it('filters by tag', async () => {
    const { service } = await build();
    expect((await service.byTag('latin', 'advanced')).map((e) => e.lemma)).toEqual(['ager']);
    expect(await service.byTag('latin', 'nowhere')).toEqual([]);
  });
});
