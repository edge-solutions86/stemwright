import { describe, expect, it } from 'vitest';
import {
  clusterDistance,
  firstDivergence,
  isTransposition,
  pointDistance,
  sharedPrefixLength,
  sharedSuffixLength,
  similarity,
} from '../../src/domain/ortho/distance.js';

const cp = (point: number) => String.fromCodePoint(point);
const ACUTE = cp(0x0301);

describe('clusterDistance', () => {
  it('is zero for identical strings', () => {
    expect(clusterDistance('puella', 'puella')).toBe(0);
  });

  it('counts one substitution', () => {
    expect(clusterDistance('puella', 'puellu')).toBe(1);
  });

  it('counts one insertion and one deletion', () => {
    expect(clusterDistance('puella', 'puellae')).toBe(1);
    expect(clusterDistance('puellae', 'puella')).toBe(1);
  });

  it('counts an accent as one change, not as several code points', () => {
    expect(clusterDistance('anthropos', `a${ACUTE}nthropos`)).toBe(1);
    expect(pointDistance('anthropos', `a${ACUTE}nthropos`)).toBe(1);
  });

  it('counts a composed accent the same as a decomposed one', () => {
    expect(clusterDistance('ανθρωπος', 'άνθρωπος')).toBe(1);
  });

  it('is the length of the other string when one is empty', () => {
    expect(clusterDistance('', 'puella')).toBe(6);
    expect(clusterDistance('puella', '')).toBe(6);
    expect(clusterDistance('', '')).toBe(0);
  });

  it('is symmetric', () => {
    expect(clusterDistance('bellum', 'bellorum')).toBe(clusterDistance('bellorum', 'bellum'));
  });
});

describe('isTransposition', () => {
  it('is true for one adjacent swap', () => {
    expect(isTransposition('puella', 'peulla')).toBe(true);
  });

  it('is false for a swap that is not adjacent', () => {
    expect(isTransposition('puella', 'auellp')).toBe(false);
  });

  it('is false when the lengths differ', () => {
    expect(isTransposition('puella', 'puellae')).toBe(false);
  });

  it('is false for identical strings', () => {
    expect(isTransposition('puella', 'puella')).toBe(false);
  });

  it('is false for a single letter string', () => {
    expect(isTransposition('a', 'a')).toBe(false);
  });

  it('is false when three positions differ', () => {
    expect(isTransposition('abcd', 'badc')).toBe(false);
  });
});

describe('firstDivergence', () => {
  it('gives nothing when the forms agree', () => {
    expect(firstDivergence('puella', 'puella')).toBeUndefined();
  });

  it('names the position and both clusters', () => {
    expect(firstDivergence('puella', 'puellu')).toEqual({
      index: 5,
      expected: 'a',
      given: 'u',
    });
  });

  it('reports a missing cluster at the end', () => {
    expect(firstDivergence('puellae', 'puella')).toEqual({
      index: 6,
      expected: 'e',
      given: undefined,
    });
  });

  it('reports an extra cluster at the end', () => {
    expect(firstDivergence('puella', 'puellae')).toEqual({
      index: 6,
      expected: undefined,
      given: 'e',
    });
  });

  it('counts an accented vowel as one position', () => {
    expect(firstDivergence('ανθρωπος', 'άνθρωπος')?.index).toBe(0);
  });
});

describe('shared prefix and suffix', () => {
  it('counts the shared opening', () => {
    expect(sharedPrefixLength('puella', 'puellae')).toBe(6);
    expect(sharedPrefixLength('bellum', 'puella')).toBe(0);
  });

  it('counts the shared ending', () => {
    expect(sharedSuffixLength('puellarum', 'bellorum')).toBe(3);
    expect(sharedSuffixLength('puella', 'bellum')).toBe(0);
  });

  it('never counts past the shorter string', () => {
    expect(sharedPrefixLength('pu', 'puella')).toBe(2);
    expect(sharedSuffixLength('um', 'bellum')).toBe(2);
  });
});

describe('similarity', () => {
  it('is one for identical strings and for two empty ones', () => {
    expect(similarity('puella', 'puella')).toBe(1);
    expect(similarity('', '')).toBe(1);
  });

  it('is zero when nothing is shared', () => {
    expect(similarity('abc', 'xyz')).toBe(0);
  });

  it('falls as the forms diverge', () => {
    const close = similarity('puella', 'puellae');
    const far = similarity('puella', 'bellorum');
    expect(close).toBeGreaterThan(far);
    expect(close).toBeLessThan(1);
  });

  it('is never negative', () => {
    expect(similarity('a', 'abcdefgh')).toBeGreaterThanOrEqual(0);
  });
});
