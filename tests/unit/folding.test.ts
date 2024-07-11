import { describe, expect, it } from 'vitest';
import {
  FOLD_ORDER,
  FOLD_STEPS,
  applyFolds,
  describeFolds,
  foldedEqual,
  foldsThatMattered,
  isFoldStepKey,
  normaliseFoldList,
  requireFoldStep,
} from '../../src/domain/ortho/folding.js';
import { ValidationError } from '../../src/domain/errors.js';

const cp = (point: number) => String.fromCodePoint(point);
const MACRON = cp(0x0304);
const ACUTE = cp(0x0301);

describe('FOLD_STEPS', () => {
  it('declares every key that the order names', () => {
    for (const key of FOLD_ORDER) {
      expect(FOLD_STEPS.has(key)).toBe(true);
    }
    expect(FOLD_ORDER).toHaveLength(FOLD_STEPS.size);
  });

  it('recognises its own keys and nothing else', () => {
    expect(isFoldStepKey('marks')).toBe(true);
    expect(isFoldStepKey('vowels')).toBe(false);
    expect(() => requireFoldStep('vowels')).toThrow(ValidationError);
  });
});

describe('individual steps', () => {
  it('lowercases', () => {
    expect(applyFolds('Puella', ['lowercase'])).toBe('puella');
  });

  it('strips accents and breathings', () => {
    expect(applyFolds(`a${ACUTE}nthropos`, ['marks'])).toBe('anthropos');
    expect(applyFolds('ἄνθρωπος', ['marks'])).toBe('ανθρωπος');
  });

  it('accepts a medial sigma at the end of a word', () => {
    expect(applyFolds('λογος', ['final-forms'])).toBe(applyFolds('λογοσ', ['final-forms']));
  });

  it('folds the hebrew final letters too', () => {
    expect(applyFolds('מלך', ['final-forms'])).toBe('מלכ');
  });

  it('drops macrons but keeps accents', () => {
    expect(applyFolds(`a${MACRON}mo`, ['length-marks'])).toBe('amo');
    expect(applyFolds(`a${ACUTE}mo`, ['length-marks'])).toBe(`a${ACUTE}mo`.normalize('NFC'));
  });

  it('drops punctuation', () => {
    expect(applyFolds('quo usque, tandem?', ['punctuation'])).toBe('quo usque tandem');
  });

  it('drops spacing entirely', () => {
    expect(applyFolds('in urbe', ['spaces'])).toBe('inurbe');
  });
});

describe('applyFolds', () => {
  it('changes nothing when no step is asked for', () => {
    expect(applyFolds('  Puella  ', [])).toBe('Puella');
  });

  it('is idempotent', () => {
    const once = applyFolds('Ἄνθρωπος,', ['lowercase', 'marks', 'punctuation', 'final-forms']);
    expect(applyFolds(once, ['lowercase', 'marks', 'punctuation', 'final-forms'])).toBe(once);
  });

  it('runs in the declared order whatever order it was given', () => {
    const forward = applyFolds('Ἄνθρωπος', ['lowercase', 'marks']);
    const backward = applyFolds('Ἄνθρωπος', ['marks', 'lowercase']);
    expect(forward).toBe(backward);
    expect(forward).toBe('ανθρωπος');
  });

  it('ignores a step listed twice', () => {
    expect(applyFolds('Puella', ['lowercase', 'lowercase'])).toBe('puella');
  });
});

describe('normaliseFoldList', () => {
  it('sorts into the declared order and drops duplicates', () => {
    expect(normaliseFoldList(['marks', 'lowercase', 'marks'])).toEqual(['lowercase', 'marks']);
  });

  it('refuses a step nobody declared', () => {
    expect(() => normaliseFoldList(['marks', 'vowels'])).toThrow(/is not a fold step: vowels/);
  });

  it('accepts an empty list', () => {
    expect(normaliseFoldList([])).toEqual([]);
  });
});

describe('describeFolds', () => {
  it('reads the steps out in order', () => {
    expect(describeFolds(['marks', 'lowercase'])).toEqual([
      'ignore capitals',
      'ignore accents and breathings',
    ]);
  });

  it('says nothing for an empty list', () => {
    expect(describeFolds([])).toEqual([]);
  });
});

describe('foldedEqual', () => {
  it('is exact when nothing is folded', () => {
    expect(foldedEqual('puella', 'Puella', [])).toBe(false);
    expect(foldedEqual('puella', 'puella', [])).toBe(true);
  });

  it('lets a step bring two spellings together', () => {
    expect(foldedEqual('puella', 'Puella', ['lowercase'])).toBe(true);
  });

  it('stays false when the difference is not one of the steps', () => {
    expect(foldedEqual('puella', 'puellae', ['lowercase', 'marks'])).toBe(false);
  });
});

describe('foldsThatMattered', () => {
  it('names the one step that made the answer pass', () => {
    expect(foldsThatMattered('puella', 'Puella', ['lowercase', 'marks'])).toEqual(['lowercase']);
  });

  it('names nothing when the answer was exact anyway', () => {
    expect(foldsThatMattered('puella', 'puella', ['lowercase', 'marks'])).toEqual([]);
  });

  it('names nothing when the answer is wrong however it is folded', () => {
    expect(foldsThatMattered('puella', 'puellae', ['lowercase', 'marks'])).toEqual([]);
  });

  it('names both when two steps were each needed', () => {
    const mattered = foldsThatMattered('anthropos', `A${ACUTE}nthropos`, ['lowercase', 'marks']);
    expect(mattered).toEqual(['lowercase', 'marks']);
  });
});
