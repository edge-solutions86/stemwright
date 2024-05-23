import { describe, expect, it } from 'vitest';
import {
  PARTS_OF_SPEECH,
  childrenOf,
  classChain,
  classOf,
  classRegistry,
  classesFor,
  isDescendantOf,
  isPartOfSpeech,
  requireClass,
  resolveThroughChain,
  rootClasses,
} from '../../src/domain/lexicon/inflection-class.js';
import { latinClasses } from '../support/classes.js';
import { problemMessages } from '../support/problems.js';

describe('isPartOfSpeech', () => {
  it('accepts every declared part and nothing else', () => {
    for (const part of PARTS_OF_SPEECH) {
      expect(isPartOfSpeech(part)).toBe(true);
    }
    expect(isPartOfSpeech('adverb')).toBe(false);
  });
});

describe('classRegistry', () => {
  it('keys every class it was given', () => {
    expect(classOf(latinClasses, 'third-declension.i-stem')?.label).toBe('Third declension i stem');
    expect(classOf(latinClasses, 'fourth-declension')).toBeUndefined();
  });

  it('defaults a label to the key', () => {
    const registry = classRegistry('x', [{ key: 'plain', partOfSpeech: 'noun' }]);
    expect(requireClass(registry, 'plain').label).toBe('plain');
  });

  it('refuses a duplicate key', () => {
    expect(
      problemMessages(() =>
        classRegistry('x', [
          { key: 'a', partOfSpeech: 'noun' },
          { key: 'a', partOfSpeech: 'noun' },
        ]),
      ),
    ).toEqual(['classes.a is declared twice']);
  });

  it('refuses a parent that was never declared', () => {
    expect(
      problemMessages(() => classRegistry('x', [{ key: 'a', partOfSpeech: 'noun', parent: 'b' }])),
    ).toEqual(['classes.a.parent names an unknown class: b']);
  });

  it('refuses a child whose part of speech differs from its parent', () => {
    expect(
      problemMessages(() =>
        classRegistry('x', [
          { key: 'a', partOfSpeech: 'noun' },
          { key: 'b', partOfSpeech: 'verb', parent: 'a' },
        ]),
      ),
    ).toEqual(['classes.b.partOfSpeech is verb but its parent a is noun']);
  });

  it('refuses a cycle and names every class in it', () => {
    const messages = problemMessages(() =>
      classRegistry('x', [
        { key: 'a', partOfSpeech: 'noun', parent: 'b' },
        { key: 'b', partOfSpeech: 'noun', parent: 'a' },
      ]),
    );
    expect(messages).toEqual([
      'classes.a.parent takes part in a cycle',
      'classes.b.parent takes part in a cycle',
    ]);
  });

  it('refuses a class that is its own parent', () => {
    expect(
      problemMessages(() => classRegistry('x', [{ key: 'a', partOfSpeech: 'noun', parent: 'a' }])),
    ).toEqual(['classes.a.parent takes part in a cycle']);
  });

  it('refuses a part of speech that is not one of the six', () => {
    expect(
      problemMessages(() =>
        classRegistry('x', [{ key: 'a', partOfSpeech: 'adverb' as unknown as 'noun' }]),
      ),
    ).toEqual(['classes.a.partOfSpeech is not a part of speech: adverb']);
  });
});

describe('classChain', () => {
  it('starts at the class and walks to the root', () => {
    expect(classChain(latinClasses, 'third-declension.i-stem').map((c) => c.key)).toEqual([
      'third-declension.i-stem',
      'third-declension',
    ]);
  });

  it('is one long for a root class', () => {
    expect(classChain(latinClasses, 'first-declension')).toHaveLength(1);
  });

  it('is empty for a class that is not there', () => {
    expect(classChain(latinClasses, 'nowhere')).toEqual([]);
  });
});

describe('isDescendantOf', () => {
  it('is true for a real ancestor', () => {
    expect(isDescendantOf(latinClasses, 'second-declension.neuter', 'second-declension')).toBe(
      true,
    );
  });

  it('is false for the class itself', () => {
    expect(isDescendantOf(latinClasses, 'second-declension', 'second-declension')).toBe(false);
  });

  it('is false across branches', () => {
    expect(isDescendantOf(latinClasses, 'second-declension.neuter', 'third-declension')).toBe(
      false,
    );
  });
});

describe('childrenOf and rootClasses', () => {
  it('lists direct children in key order', () => {
    expect(childrenOf(latinClasses, 'second-declension').map((c) => c.key)).toEqual([
      'second-declension.neuter',
      'second-declension.r-stem',
    ]);
  });

  it('gives nothing for a leaf', () => {
    expect(childrenOf(latinClasses, 'first-declension')).toEqual([]);
  });

  it('lists the classes with no parent', () => {
    expect(rootClasses(latinClasses).map((c) => c.key)).toEqual([
      'first-conjugation',
      'first-declension',
      'second-declension',
      'third-conjugation',
      'third-declension',
    ]);
  });
});

describe('classesFor', () => {
  it('splits the registry by part of speech', () => {
    expect(classesFor(latinClasses, 'verb').map((c) => c.key)).toEqual([
      'first-conjugation',
      'third-conjugation',
      'third-conjugation.io',
    ]);
    expect(classesFor(latinClasses, 'noun')).toHaveLength(6);
    expect(classesFor(latinClasses, 'pronoun')).toEqual([]);
  });
});

describe('resolveThroughChain', () => {
  const profiles = new Map<string, string>([
    ['second-declension', 'prof-second'],
    ['third-declension', 'prof-third'],
  ]);
  const lookup = (key: string) => profiles.get(key);

  it('takes the class own value when it has one', () => {
    expect(resolveThroughChain(latinClasses, 'second-declension', lookup)).toEqual({
      value: 'prof-second',
      through: 'second-declension',
    });
  });

  it('inherits from the nearest ancestor that has one', () => {
    expect(resolveThroughChain(latinClasses, 'second-declension.neuter', lookup)).toEqual({
      value: 'prof-second',
      through: 'second-declension',
    });
  });

  it('gives nothing when no class in the chain has one', () => {
    expect(resolveThroughChain(latinClasses, 'first-declension', lookup)).toBeUndefined();
  });

  it('reports which class answered so a report can say where it came from', () => {
    const resolved = resolveThroughChain(latinClasses, 'third-declension.i-stem', lookup);
    expect(resolved?.through).toBe('third-declension');
  });
});

describe('requireClass', () => {
  it('names the language when the class is missing', () => {
    expect(() => requireClass(latinClasses, 'fourth')).toThrow(/is not a class of latin: fourth/);
  });
});
