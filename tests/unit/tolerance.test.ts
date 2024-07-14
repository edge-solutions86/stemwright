import { describe, expect, it } from 'vitest';
import {
  areComparable,
  defaultLevelOf,
  describeTolerance,
  isStricterThan,
  levelKeys,
  levelOf,
  orthoProfile,
  requireLevel,
  resolveTolerance,
} from '../../src/domain/ortho/tolerance.js';
import { problemMessages } from '../support/problems.js';
import { greekOrtho, latinOrtho } from '../support/ortho.js';

describe('orthoProfile', () => {
  it('keeps the levels in the order they were declared', () => {
    expect(levelKeys(greekOrtho)).toEqual(['exact', 'standard', 'lenient', 'unaccented']);
  });

  it('sorts the folds of each level into the canonical order', () => {
    expect(requireLevel(greekOrtho, 'lenient').folds).toEqual([
      'lowercase',
      'final-forms',
      'marks',
    ]);
  });

  it('defaults a label to the key', () => {
    expect(requireLevel(latinOrtho, 'exact').label).toBe('exact');
  });

  it('refuses a repeated level key', () => {
    expect(
      problemMessages(() =>
        orthoProfile({
          language: 'x',
          defaultLevel: 'a',
          levels: [
            { key: 'a', folds: [] },
            { key: 'a', folds: ['marks'] },
          ],
        }),
      ),
    ).toEqual(['levels.1 repeats the level key a']);
  });

  it('refuses a fold step nobody declared', () => {
    expect(
      problemMessages(() =>
        orthoProfile({
          language: 'x',
          defaultLevel: 'b',
          levels: [
            { key: 'a', folds: ['vowels'] },
            { key: 'b', folds: ['marks'] },
          ],
        }),
      ),
    ).toEqual(['levels.0 folds is not a fold step: vowels']);
  });

  it('refuses a default that names no level', () => {
    expect(
      problemMessages(() =>
        orthoProfile({ language: 'x', defaultLevel: 'b', levels: [{ key: 'a', folds: [] }] }),
      ),
    ).toEqual(['defaultLevel names a level that is not declared: b']);
  });

  it('refuses a profile with no levels', () => {
    expect(
      problemMessages(() => orthoProfile({ language: 'x', defaultLevel: 'a', levels: [] })),
    ).toEqual(['levels must declare at least one level']);
  });
});

describe('lookups', () => {
  it('finds a level and reports a miss', () => {
    expect(levelOf(greekOrtho, 'exact')?.folds).toEqual([]);
    expect(levelOf(greekOrtho, 'sloppy')).toBeUndefined();
    expect(() => requireLevel(greekOrtho, 'sloppy')).toThrow(/is not a level of greek/);
  });

  it('reads the default level back', () => {
    expect(defaultLevelOf(greekOrtho).key).toBe('standard');
  });
});

describe('isStricterThan', () => {
  const exact = requireLevel(greekOrtho, 'exact');
  const standard = requireLevel(greekOrtho, 'standard');
  const lenient = requireLevel(greekOrtho, 'lenient');
  const unaccented = requireLevel(greekOrtho, 'unaccented');

  it('calls a subset of folds stricter', () => {
    expect(isStricterThan(exact, standard)).toBe(true);
    expect(isStricterThan(standard, lenient)).toBe(true);
  });

  it('is not true in the other direction', () => {
    expect(isStricterThan(lenient, standard)).toBe(false);
  });

  it('is not true of a level with itself', () => {
    expect(isStricterThan(standard, standard)).toBe(false);
  });

  it('leaves levels that fold different things incomparable', () => {
    expect(isStricterThan(standard, unaccented)).toBe(false);
    expect(isStricterThan(unaccented, standard)).toBe(false);
    expect(areComparable(standard, unaccented)).toBe(false);
  });

  it('calls a level comparable with itself', () => {
    expect(areComparable(standard, standard)).toBe(true);
  });
});

describe('resolveTolerance', () => {
  it('falls back to the language default', () => {
    expect(resolveTolerance(greekOrtho)).toEqual({
      level: requireLevel(greekOrtho, 'standard'),
      source: 'language',
    });
  });

  it('takes the deck level over the default', () => {
    expect(resolveTolerance(greekOrtho, { deckLevel: 'lenient' }).source).toBe('deck');
  });

  it('takes the cell level over the deck', () => {
    const resolved = resolveTolerance(greekOrtho, { deckLevel: 'lenient', cellLevel: 'exact' });
    expect(resolved.level.key).toBe('exact');
    expect(resolved.source).toBe('cell');
  });

  it('takes the assignment level over everything', () => {
    const resolved = resolveTolerance(greekOrtho, {
      deckLevel: 'lenient',
      cellLevel: 'exact',
      assignmentLevel: 'standard',
    });
    expect(resolved.level.key).toBe('standard');
    expect(resolved.source).toBe('assignment');
  });

  it('refuses a level the language does not declare wherever it came from', () => {
    expect(() => resolveTolerance(greekOrtho, { cellLevel: 'sloppy' })).toThrow(/not a level/);
  });
});

describe('describeTolerance', () => {
  it('says so when nothing is folded', () => {
    expect(describeTolerance(resolveTolerance(greekOrtho, { deckLevel: 'exact' }))).toBe(
      'Exact marks exactly as written, set by the deck',
    );
  });

  it('reads the folds out and names where the level came from', () => {
    expect(describeTolerance(resolveTolerance(greekOrtho, { cellLevel: 'lenient' }))).toBe(
      'Lenient will ignore capitals and accept a medial letter at the end of a word and ignore accents and breathings, set by the cell',
    );
  });
});
