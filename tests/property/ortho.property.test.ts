import { describe, expect, it } from 'vitest';
import { compareForm, isCorrect } from '../../src/domain/ortho/compare.js';
import {
  clusterDistance,
  isTransposition,
  sharedPrefixLength,
  similarity,
} from '../../src/domain/ortho/distance.js';
import { FOLD_ORDER, applyFolds, foldedEqual } from '../../src/domain/ortho/folding.js';
import type { FoldStepKey } from '../../src/domain/ortho/folding.js';
import { isStricterThan, requireLevel } from '../../src/domain/ortho/tolerance.js';
import { markClusters, normaliseInput } from '../../src/domain/text/normalise.js';
import type { Random } from '../support/random.js';
import { forEachDraw } from '../support/random.js';
import { greekOrtho } from '../support/ortho.js';

const GREEK = 'αβγδεζηθικλμνξοπρστυφχψω';
const MARKS = [0x0301, 0x0313, 0x0314, 0x0342].map((point) => String.fromCodePoint(point));

function greekWord(random: Random): string {
  const length = random.int(3, 9);
  let word = '';
  for (let index = 0; index < length; index += 1) {
    word += random.pick([...GREEK]);
    if (random.bool(0.25)) {
      word += random.pick(MARKS);
    }
  }
  return random.bool(0.2) ? `${word}ς` : word;
}

function foldsFor(random: Random): FoldStepKey[] {
  return FOLD_ORDER.filter(() => random.bool(0.4));
}

describe('folding properties', () => {
  it('is idempotent whatever the steps', () => {
    forEachDraw(120, 1861, (random) => {
      const word = greekWord(random);
      const folds = foldsFor(random);
      const once = applyFolds(word, folds);
      expect(applyFolds(once, folds)).toBe(once);
    });
  });

  it('does not depend on the order the steps were listed', () => {
    forEachDraw(120, 2903, (random) => {
      const word = greekWord(random);
      const folds = foldsFor(random);
      expect(applyFolds(word, random.shuffle(folds))).toBe(applyFolds(word, folds));
    });
  });

  it('leaves a string equal to itself under any set of steps', () => {
    forEachDraw(80, 3907, (random) => {
      const word = greekWord(random);
      expect(foldedEqual(word, word, foldsFor(random))).toBe(true);
    });
  });

  it('never separates two strings that a subset of the steps already joined', () => {
    forEachDraw(120, 4931, (random) => {
      const first = greekWord(random);
      const second = greekWord(random);
      const folds = foldsFor(random);
      if (!foldedEqual(first, second, folds)) {
        return;
      }
      const wider = FOLD_ORDER.filter((key) => folds.includes(key) || random.bool(0.5));
      expect(foldedEqual(first, second, wider)).toBe(true);
    });
  });
});

describe('distance properties', () => {
  it('is zero exactly when the forms are the same', () => {
    forEachDraw(120, 5939, (random) => {
      const word = greekWord(random);
      expect(clusterDistance(word, word)).toBe(0);
      const other = greekWord(random);
      expect(clusterDistance(word, other) === 0).toBe(word === other);
    });
  });

  it('is symmetric', () => {
    forEachDraw(120, 6947, (random) => {
      const first = greekWord(random);
      const second = greekWord(random);
      expect(clusterDistance(first, second)).toBe(clusterDistance(second, first));
    });
  });

  it('is never more than the longer of the two lengths', () => {
    forEachDraw(120, 7951, (random) => {
      const first = greekWord(random);
      const second = greekWord(random);
      const longest = Math.max(markClusters(first).length, markClusters(second).length);
      expect(clusterDistance(first, second)).toBeLessThanOrEqual(longest);
    });
  });

  it('obeys the triangle inequality', () => {
    forEachDraw(80, 8963, (random) => {
      const a = greekWord(random);
      const b = greekWord(random);
      const c = greekWord(random);
      expect(clusterDistance(a, c)).toBeLessThanOrEqual(
        clusterDistance(a, b) + clusterDistance(b, c),
      );
    });
  });

  it('keeps similarity between nought and one', () => {
    forEachDraw(120, 9973, (random) => {
      const value = similarity(greekWord(random), greekWord(random));
      expect(value).toBeGreaterThanOrEqual(0);
      expect(value).toBeLessThanOrEqual(1);
    });
  });

  it('never shares a prefix longer than the shorter string', () => {
    forEachDraw(80, 1289, (random) => {
      const first = greekWord(random);
      const second = greekWord(random);
      const shortest = Math.min(markClusters(first).length, markClusters(second).length);
      expect(sharedPrefixLength(first, second)).toBeLessThanOrEqual(shortest);
    });
  });

  it('never calls a string a transposition of itself', () => {
    forEachDraw(80, 2297, (random) => {
      const word = greekWord(random);
      expect(isTransposition(word, word)).toBe(false);
    });
  });
});

describe('marking properties', () => {
  it('marks an answer equal to the expected form exact at every level', () => {
    forEachDraw(80, 3301, (random) => {
      const word = greekWord(random);
      const level = requireLevel(greekOrtho, random.pick(['exact', 'standard', 'lenient']));
      expect(compareForm(word, word, level).verdict).toBe('exact');
    });
  });

  it('never marks an answer wrong at a level that already accepted it at a stricter one', () => {
    forEachDraw(120, 4327, (random) => {
      const expected = greekWord(random);
      const given = greekWord(random);
      const strict = requireLevel(greekOrtho, 'standard');
      const loose = requireLevel(greekOrtho, 'lenient');
      expect(isStricterThan(strict, loose)).toBe(true);
      if (isCorrect(compareForm(expected, given, strict))) {
        expect(isCorrect(compareForm(expected, given, loose))).toBe(true);
      }
    });
  });

  it('normalises the answer before comparing it', () => {
    forEachDraw(80, 5333, (random) => {
      const word = greekWord(random);
      const level = requireLevel(greekOrtho, 'exact');
      const padded = `  ${word}  `;
      expect(compareForm(word, padded, level).given).toBe(normaliseInput(word));
    });
  });
});
