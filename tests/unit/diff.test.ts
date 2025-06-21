import { describe, expect, it } from 'vitest';
import {
  changesOfKind,
  describeChange,
  diffLexicons,
  schedulingChanges,
  summariseDiff,
} from '../../src/domain/lexicon/diff.js';
import { stemSet } from '../../src/domain/lexicon/stem.js';
import { bellum, makeLexeme, puella } from '../support/lexemes.js';

const changedGloss = makeLexeme({
  lemma: 'puella',
  classKey: 'first-declension',
  stems: stemSet([{ role: 'base', form: 'puell' }]),
  inherent: { gender: 'fem' },
  glosses: ['girl', 'maiden'],
  tags: ['core', 'chapter-one'],
});

const changedClass = makeLexeme({
  lemma: 'puella',
  classKey: 'third-declension',
  stems: stemSet([{ role: 'base', form: 'puell' }]),
  inherent: { gender: 'fem' },
  glosses: ['girl'],
  tags: ['core', 'chapter-one'],
});

const changedStem = makeLexeme({
  lemma: 'puella',
  classKey: 'first-declension',
  stems: stemSet([
    { role: 'base', form: 'puell' },
    { role: 'oblique', form: 'puella' },
  ]),
  inherent: { gender: 'fem' },
  glosses: ['girl'],
  tags: ['core', 'chapter-one'],
});

describe('diffLexicons', () => {
  it('reports nothing when the two sides are the same', () => {
    expect(diffLexicons([puella, bellum], [puella, bellum])).toEqual([]);
  });

  it('reports an addition', () => {
    const changes = diffLexicons([puella], [puella, bellum]);
    expect(changes).toHaveLength(1);
    expect(changes[0]).toMatchObject({ kind: 'added', lemma: 'bellum' });
  });

  it('reports a removal', () => {
    const changes = diffLexicons([puella, bellum], [puella]);
    expect(changes[0]).toMatchObject({ kind: 'removed', lemma: 'bellum' });
  });

  it('reports a change at the field that changed', () => {
    const changes = diffLexicons([puella], [changedGloss]);
    expect(changes).toHaveLength(1);
    expect(changes[0]?.kind).toBe('changed');
    expect(changes[0]?.fields).toEqual([
      { field: 'glosses', before: 'girl', after: 'girl; maiden' },
    ]);
  });

  it('reports a class change', () => {
    const changes = diffLexicons([puella], [changedClass]);
    expect(changes[0]?.fields[0]).toEqual({
      field: 'class',
      before: 'first-declension',
      after: 'third-declension',
    });
  });

  it('reports a stem that was added', () => {
    const changes = diffLexicons([puella], [changedStem]);
    expect(changes[0]?.fields).toEqual([{ field: 'stem.oblique', before: '', after: 'puella' }]);
  });

  it('lists the fields in name order', () => {
    const both = makeLexeme({
      lemma: 'puella',
      classKey: 'third-declension',
      stems: stemSet([{ role: 'base', form: 'puell' }]),
      inherent: { gender: 'fem' },
      glosses: ['girl', 'maiden'],
      tags: ['core', 'chapter-one'],
    });
    expect(diffLexicons([puella], [both])[0]?.fields.map((field) => field.field)).toEqual([
      'class',
      'glosses',
    ]);
  });

  it('reports additions and removals together', () => {
    const changes = diffLexicons([puella], [bellum]);
    expect(changesOfKind(changes, 'added')).toHaveLength(1);
    expect(changesOfKind(changes, 'removed')).toHaveLength(1);
  });
});

describe('schedulingChanges', () => {
  it('counts an addition and a removal', () => {
    expect(schedulingChanges(diffLexicons([puella], [bellum]))).toHaveLength(2);
  });

  it('counts a class change', () => {
    expect(schedulingChanges(diffLexicons([puella], [changedClass]))).toHaveLength(1);
  });

  it('does not count a gloss change', () => {
    expect(schedulingChanges(diffLexicons([puella], [changedGloss]))).toEqual([]);
  });

  it('does not count a stem change', () => {
    expect(schedulingChanges(diffLexicons([puella], [changedStem]))).toEqual([]);
  });
});

describe('describeChange', () => {
  it('reads an addition and a removal out', () => {
    expect(describeChange(diffLexicons([], [puella])[0]!)).toBe('added puella');
    expect(describeChange(diffLexicons([puella], [])[0]!)).toBe('removed puella');
  });

  it('names the fields of a change', () => {
    expect(describeChange(diffLexicons([puella], [changedGloss])[0]!)).toBe(
      'changed puella: glosses from girl to girl; maiden',
    );
  });

  it('says nothing where a field was empty', () => {
    expect(describeChange(diffLexicons([puella], [changedStem])[0]!)).toContain('from nothing to');
  });
});

describe('summariseDiff', () => {
  it('says so when nothing changed', () => {
    expect(summariseDiff(diffLexicons([puella], [puella]))).toBe('nothing changed');
  });

  it('counts each kind and how many move a schedule', () => {
    const changes = diffLexicons([puella, bellum], [changedClass]);
    expect(summariseDiff(changes)).toBe(
      '0 added, 1 removed, 1 changed, 2 of which move a schedule',
    );
  });
});
