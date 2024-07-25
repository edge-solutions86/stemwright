import { describe, expect, it } from 'vitest';
import {
  classifyMiss,
  compareAgainstAny,
  compareForm,
  describeMatch,
  isCorrect,
} from '../../src/domain/ortho/compare.js';
import { requireLevel } from '../../src/domain/ortho/tolerance.js';
import { greekOrtho, latinOrtho } from '../support/ortho.js';

const exact = requireLevel(greekOrtho, 'exact');
const standard = requireLevel(greekOrtho, 'standard');
const lenient = requireLevel(greekOrtho, 'lenient');
const latinStandard = requireLevel(latinOrtho, 'standard');

describe('compareForm', () => {
  it('calls a byte identical answer exact', () => {
    const result = compareForm('puella', 'puella', exact);
    expect(result.verdict).toBe('exact');
    expect(result.distance).toBe(0);
    expect(result.foldsUsed).toEqual([]);
    expect(isCorrect(result)).toBe(true);
  });

  it('trims and normalises before comparing', () => {
    expect(compareForm('puella', '  puella  ', exact).verdict).toBe('exact');
  });

  it('calls a difference the level folds tolerated', () => {
    const result = compareForm('Puella', 'puella', latinStandard);
    expect(result.verdict).toBe('tolerated');
    expect(result.foldsUsed).toEqual(['lowercase']);
    expect(isCorrect(result)).toBe(true);
  });

  it('keeps the same difference wrong at a stricter level', () => {
    const result = compareForm('Puella', 'puella', exact);
    expect(result.verdict).toBe('near');
    expect(isCorrect(result)).toBe(false);
  });

  it('calls an answer one cluster away a near miss', () => {
    const result = compareForm('puella', 'puellu', exact);
    expect(result.verdict).toBe('near');
    expect(result.distance).toBe(1);
  });

  it('calls an adjacent swap a near miss however long the word', () => {
    expect(compareForm('bellorum', 'bellourm', exact).verdict).toBe('near');
  });

  it('calls anything further wrong', () => {
    const result = compareForm('puella', 'bellorum', exact);
    expect(result.verdict).toBe('wrong');
    expect(result.distance).toBeGreaterThan(1);
  });

  it('reports similarity for a wrong answer', () => {
    const near = compareForm('puellarum', 'puellorum', exact);
    const far = compareForm('puellarum', 'xyz', exact);
    expect(near.similarity).toBeGreaterThan(far.similarity);
  });

  it('measures distance after folding, not before', () => {
    const result = compareForm('ἄνθρωπος', 'ανθρωποσ', lenient);
    expect(result.verdict).toBe('tolerated');
    expect(result.distance).toBe(0);
  });
});

describe('classifyMiss', () => {
  it('names a capital', () => {
    expect(classifyMiss('Puella', 'puella')).toBe('capital');
  });

  it('names an accent', () => {
    expect(classifyMiss('ἄνθρωπος', 'ανθρωπος')).toBe('accent');
  });

  it('names spacing', () => {
    expect(classifyMiss('in urbe', 'inurbe')).toBe('spacing');
  });

  it('names an order swap', () => {
    expect(classifyMiss('puella', 'peulla')).toBe('order');
  });

  it('names an ending when the opening is shared', () => {
    expect(classifyMiss('puellarum', 'puellorum')).toBe('ending');
    expect(classifyMiss('puella', 'puellae')).toBe('ending');
  });

  it('names the stem when the opening is not shared', () => {
    expect(classifyMiss('puellarum', 'bellorum')).toBe('stem');
  });

  it('falls back to other for identical forms', () => {
    expect(classifyMiss('puella', 'puella')).toBe('other');
  });
});

describe('compareAgainstAny', () => {
  it('takes the best verdict across the acceptable forms', () => {
    const result = compareAgainstAny(['bellorum', 'puella'], 'puella', exact);
    expect(result.verdict).toBe('exact');
    expect(result.expected).toBe('puella');
  });

  it('prefers exact over tolerated', () => {
    const result = compareAgainstAny(['Puella', 'puella'], 'puella', latinStandard);
    expect(result.verdict).toBe('exact');
  });

  it('prefers the closer of two equally wrong forms', () => {
    const result = compareAgainstAny(['xyzzy', 'puellu'], 'puella', exact);
    expect(result.expected).toBe('puellu');
  });

  it('marks wrong when nothing matches', () => {
    expect(compareAgainstAny(['bellorum', 'agrorum'], 'puella', exact).verdict).toBe('wrong');
  });

  it('treats an empty list as an answer against nothing', () => {
    expect(compareAgainstAny([], 'puella', exact).verdict).toBe('wrong');
  });
});

describe('describeMatch', () => {
  it('says right for an exact answer', () => {
    expect(describeMatch(compareForm('puella', 'puella', exact))).toBe('right');
  });

  it('names what the tolerance let through', () => {
    expect(describeMatch(compareForm('Puella', 'puella', latinStandard))).toBe(
      'right, and the capital is not marked at this tolerance',
    );
  });

  it('names the miss and the distance for a near answer', () => {
    expect(describeMatch(compareForm('puella', 'puellu', exact))).toBe(
      'not quite, the ending is off by 1',
    );
  });

  it('gives the expected form for a wrong answer', () => {
    expect(describeMatch(compareForm('puella', 'bellorum', exact))).toBe(
      'wrong, the expected form was puella',
    );
  });
});

describe('the standard level', () => {
  it('folds a final sigma but not an accent', () => {
    expect(compareForm('λόγος', 'λόγοσ', standard).verdict).toBe('tolerated');
    expect(compareForm('λόγος', 'λογος', standard).verdict).toBe('near');
  });
});
