import { describe, expect, it } from 'vitest';
import {
  bindingFor,
  historyFor,
  profileBook,
  profileFor,
  requireProfileFor,
  unboundClasses,
  withBinding,
  withHistory,
} from '../../src/domain/paradigm/book.js';
import { revisionHistory } from '../../src/domain/paradigm/revision.js';
import { NotFoundError } from '../../src/domain/errors.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { latinClasses } from '../support/classes.js';
import { firstHistory, latinBook, neuterHistory } from '../support/book.js';
import { problemMessages } from '../support/problems.js';
import { firstDeclension } from '../support/profiles.js';

const d = (value: string) => parseCivilDate(value);

describe('profileBook', () => {
  it('holds every history by key', () => {
    expect(historyFor(latinBook, 'first-declension')).toBe(firstHistory);
    expect(historyFor(latinBook, 'third-declension')).toBeUndefined();
  });

  it('refuses a binding naming a profile it does not hold', () => {
    expect(
      problemMessages(() =>
        profileBook({
          language: 'latin',
          histories: [firstHistory],
          bindings: { 'third-declension': 'third-declension' },
        }),
      ),
    ).toEqual([
      'bindings.third-declension names a profile the book does not hold: third-declension',
    ]);
  });

  it('refuses the same history twice', () => {
    expect(
      problemMessages(() =>
        profileBook({
          language: 'latin',
          histories: [firstHistory, firstHistory],
          bindings: {},
        }),
      ),
    ).toEqual(['histories.first-declension appears twice']);
  });

  it('accepts a book with no bindings at all', () => {
    const bare = profileBook({ language: 'latin', histories: [firstHistory], bindings: {} });
    expect(bare.bindings.size).toBe(0);
  });
});

describe('bindingFor', () => {
  it('finds a binding on the class itself', () => {
    expect(bindingFor(latinBook, latinClasses, 'first-declension')).toEqual({
      profileKey: 'first-declension',
      boundThrough: 'first-declension',
    });
  });

  it('inherits a binding from an ancestor', () => {
    expect(bindingFor(latinBook, latinClasses, 'second-declension.neuter')).toEqual({
      profileKey: 'second-neuter',
      boundThrough: 'second-declension',
    });
  });

  it('gives nothing for a class with no binding anywhere up the chain', () => {
    expect(bindingFor(latinBook, latinClasses, 'third-declension')).toBeUndefined();
  });
});

describe('profileFor', () => {
  it('resolves the revision in force on the day', () => {
    const resolved = profileFor(
      latinBook,
      latinClasses,
      'second-declension.neuter',
      d('2024-10-01'),
    );
    expect(resolved?.revision.ordinal).toBe(1);
    expect(resolved?.boundThrough).toBe('second-declension');
  });

  it('moves to the later revision once it takes effect', () => {
    const resolved = profileFor(
      latinBook,
      latinClasses,
      'second-declension.r-stem',
      d('2025-03-01'),
    );
    expect(resolved?.revision.ordinal).toBe(2);
    expect(resolved?.revision.syncretism.groups).toHaveLength(2);
  });

  it('gives nothing before the profile was published', () => {
    expect(
      profileFor(latinBook, latinClasses, 'first-declension', d('2024-01-01')),
    ).toBeUndefined();
  });

  it('gives nothing for an unbound class', () => {
    expect(
      profileFor(latinBook, latinClasses, 'third-declension', d('2025-01-01')),
    ).toBeUndefined();
  });

  it('throws with the class and the day when a caller demands one', () => {
    expect(() =>
      requireProfileFor(latinBook, latinClasses, 'third-declension', d('2025-01-01')),
    ).toThrow(NotFoundError);
    expect(() =>
      requireProfileFor(latinBook, latinClasses, 'third-declension', d('2025-01-01')),
    ).toThrow(/third-declension on 2025-01-01/);
  });
});

describe('unboundClasses', () => {
  it('names every class with no binding up its chain', () => {
    expect(unboundClasses(latinBook, latinClasses)).toEqual([
      'first-conjugation',
      'third-conjugation',
      'third-conjugation.io',
      'third-declension',
      'third-declension.i-stem',
    ]);
  });

  it('shrinks as bindings are added', () => {
    const full = withBinding(latinBook, 'third-declension', 'first-declension');
    expect(unboundClasses(full, latinClasses)).toEqual([
      'first-conjugation',
      'third-conjugation',
      'third-conjugation.io',
    ]);
  });
});

describe('withBinding and withHistory', () => {
  it('adds a binding and leaves the original alone', () => {
    const grown = withBinding(latinBook, 'third-declension', 'first-declension');
    expect(bindingFor(grown, latinClasses, 'third-declension.i-stem')?.profileKey).toBe(
      'first-declension',
    );
    expect(bindingFor(latinBook, latinClasses, 'third-declension.i-stem')).toBeUndefined();
  });

  it('replaces a history under the same key', () => {
    const shorter = revisionHistory('second-neuter', [
      { effectiveFrom: d('2024-09-01'), profile: neuterHistory.revisions[0]!.profile },
    ]);
    const changed = withHistory(latinBook, shorter);
    expect(historyFor(changed, 'second-neuter')?.revisions).toHaveLength(1);
    expect(historyFor(latinBook, 'second-neuter')?.revisions).toHaveLength(2);
  });

  it('adds a history the book did not hold', () => {
    const third = revisionHistory('first-declension', [
      { effectiveFrom: d('2024-09-01'), profile: firstDeclension },
    ]);
    expect(historyFor(withHistory(latinBook, third), 'first-declension')?.revisions).toHaveLength(
      1,
    );
  });
});
