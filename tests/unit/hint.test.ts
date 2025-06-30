import { describe, expect, it } from 'vitest';
import { compareForm } from '../../src/domain/ortho/compare.js';
import {
  HINT_LEVELS,
  describeHint,
  hintFor,
  hintLadder,
  isHintLevel,
  nextLevel,
  openingOf,
  revealsAnswer,
  shapeOf,
} from '../../src/domain/ortho/hint.js';
import { requireLevel } from '../../src/domain/ortho/tolerance.js';
import { ValidationError } from '../../src/domain/errors.js';
import { latinOrtho } from '../support/ortho.js';

const exact = requireLevel(latinOrtho, 'exact');
const wrongEnding = compareForm('puellarum', 'puellorum', exact);
const wrongWord = compareForm('puellarum', 'bellum', exact);
const right = compareForm('puellarum', 'puellarum', exact);

describe('the levels', () => {
  it('names four levels in order', () => {
    expect(HINT_LEVELS).toEqual(['shape', 'kind', 'opening', 'reveal']);
  });

  it('recognises its own levels', () => {
    expect(isHintLevel('opening')).toBe(true);
    expect(isHintLevel('answer')).toBe(false);
  });

  it('steps forward and stops at the last one', () => {
    expect(nextLevel('shape')).toBe('kind');
    expect(nextLevel('reveal')).toBe('reveal');
  });
});

describe('shapeOf', () => {
  it('gives one marker per cluster', () => {
    expect(shapeOf('puella')).toBe('......');
  });

  it('counts an accented vowel as one', () => {
    expect(shapeOf('ἄνθρωπος')).toBe('........');
  });

  it('takes the marker it is given', () => {
    expect(shapeOf('abc', '_')).toBe('___');
  });
});

describe('openingOf', () => {
  it('reveals the shared opening and hides the rest', () => {
    expect(openingOf('puellarum', 'puellorum')).toBe('puell....');
  });

  it('never reveals the whole form', () => {
    expect(openingOf('puella', 'puella')).toBe('puell.');
  });

  it('reveals the first letter when nothing is shared', () => {
    expect(openingOf('puella', 'bellum')).toBe('p.....');
  });

  it('reveals the first letter for an empty answer', () => {
    expect(openingOf('puella', '')).toBe('p.....');
  });
});

describe('hintFor', () => {
  it('gives the length at the shape level', () => {
    const hint = hintFor(wrongEnding, 'shape');
    expect(hint.text).toBe('9 letters: .........');
    expect(hint.revealed).toBe(0);
  });

  it('gives the shared opening at the opening level', () => {
    const hint = hintFor(wrongEnding, 'opening');
    expect(hint.text).toBe('puell....');
    expect(hint.revealed).toBe(5);
  });

  it('names the kind of miss and where it starts', () => {
    expect(hintFor(wrongEnding, 'kind').text).toBe('the ending is wrong, from letter 6');
  });

  it('names the stem when the whole word is wrong', () => {
    expect(hintFor(wrongWord, 'kind').text).toContain('stem');
  });

  it('gives the form at the reveal level', () => {
    const hint = hintFor(wrongEnding, 'reveal');
    expect(hint.text).toBe('puellarum');
    expect(revealsAnswer(hint, 'puellarum')).toBe(true);
  });

  it('refuses to hint at an answer that was already right', () => {
    expect(() => hintFor(right, 'shape')).toThrow(ValidationError);
    expect(() => hintFor(right, 'shape')).toThrow(/already right/);
  });
});

describe('hintLadder', () => {
  it('gives one hint per level in order', () => {
    expect(hintLadder(wrongEnding).map((hint) => hint.level)).toEqual([...HINT_LEVELS]);
  });

  it('reveals more at each step', () => {
    const ladder = hintLadder(wrongEnding);
    for (let index = 1; index < ladder.length; index += 1) {
      expect(ladder[index]!.revealed).toBeGreaterThanOrEqual(ladder[index - 1]!.revealed);
    }
  });

  it('only reveals the answer at the last step', () => {
    const revealing = hintLadder(wrongEnding).filter((hint) => revealsAnswer(hint, 'puellarum'));
    expect(revealing).toHaveLength(1);
    expect(revealing[0]?.level).toBe('reveal');
  });
});

describe('describeHint', () => {
  it('names the level', () => {
    expect(describeHint(hintFor(wrongEnding, 'shape'))).toBe('shape: 9 letters: .........');
  });
});
