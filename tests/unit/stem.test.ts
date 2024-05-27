import { describe, expect, it } from 'vitest';
import {
  DEFAULT_STEM_ROLE,
  hasSuppletion,
  looksRelated,
  resolveStem,
  singleStem,
  stem,
  stemFor,
  stemRoles,
  stemSet,
  suppletiveRoles,
  suspectSuppletion,
  withStem,
  withoutStem,
} from '../../src/domain/lexicon/stem.js';
import { ValidationError } from '../../src/domain/errors.js';
import { problemMessages } from '../support/problems.js';

const greekVerb = stemSet([
  { role: 'base', form: 'leip' },
  { role: 'future', form: 'leips' },
  { role: 'aorist', form: 'lip', note: 'the second aorist takes the zero grade' },
  { role: 'perfect', form: 'leloip' },
]);

describe('stem', () => {
  it('keeps the role and the form', () => {
    const built = stem({ role: 'aorist', form: 'lip' });
    expect(built).toEqual({ role: 'aorist', form: 'lip', suppletive: false, note: undefined });
  });

  it('lower cases the role and trims the form', () => {
    expect(stem({ role: 'Aorist', form: '  lip  ' })).toMatchObject({
      role: 'aorist',
      form: 'lip',
    });
  });

  it('refuses a blank form', () => {
    expect(() => stem({ role: 'base', form: '   ' })).toThrow(ValidationError);
  });

  it('refuses a role that is not a slug', () => {
    expect(() => stem({ role: 'second aorist', form: 'lip' })).toThrow(/not a slug/);
  });
});

describe('stemSet', () => {
  it('holds the stems in the order they were given', () => {
    expect(stemRoles(greekVerb)).toEqual(['base', 'future', 'aorist', 'perfect']);
  });

  it('refuses an empty set', () => {
    expect(problemMessages(() => stemSet([]))).toEqual(['stems must hold at least one stem']);
  });

  it('refuses a repeated role', () => {
    expect(
      problemMessages(() =>
        stemSet([
          { role: 'base', form: 'a' },
          { role: 'base', form: 'b' },
        ]),
      ),
    ).toEqual(['stems.base is declared twice']);
  });

  it('builds a one stem set from a bare form', () => {
    expect(stemRoles(singleStem('mens'))).toEqual([DEFAULT_STEM_ROLE]);
  });
});

describe('stemFor and resolveStem', () => {
  it('finds a role that is there', () => {
    expect(stemFor(greekVerb, 'perfect')?.form).toBe('leloip');
    expect(stemFor(greekVerb, 'pluperfect')).toBeUndefined();
  });

  it('falls back to the base stem for a role that is missing', () => {
    expect(resolveStem(greekVerb, 'pluperfect').role).toBe('base');
  });

  it('prefers the exact role over the fallback', () => {
    expect(resolveStem(greekVerb, 'aorist').form).toBe('lip');
  });

  it('refuses when there is neither the role nor a base', () => {
    const noBase = stemSet([{ role: 'future', form: 'leips' }]);
    expect(() => resolveStem(noBase, 'aorist')).toThrow(/neither a aorist stem nor a base stem/);
  });
});

describe('suppletion', () => {
  const latinFero = stemSet([
    { role: 'base', form: 'fer' },
    { role: 'perfect', form: 'tul', suppletive: true },
    { role: 'supine', form: 'lat', suppletive: true },
  ]);

  it('reports whether any stem is marked suppletive', () => {
    expect(hasSuppletion(latinFero)).toBe(true);
    expect(hasSuppletion(greekVerb)).toBe(false);
  });

  it('names the suppletive roles', () => {
    expect(suppletiveRoles(latinFero)).toEqual(['perfect', 'supine']);
  });

  it('finds none to be suspect when the forms really do differ', () => {
    expect(suspectSuppletion(latinFero)).toEqual([]);
  });

  it('flags a stem marked suppletive that shares an opening with the base', () => {
    const suspect = stemSet([
      { role: 'base', form: 'leip' },
      { role: 'aorist', form: 'leipa', suppletive: true },
    ]);
    expect(suspectSuppletion(suspect)).toEqual(['aorist']);
  });

  it('flags nothing when there is no base stem to compare against', () => {
    const noBase = stemSet([{ role: 'perfect', form: 'tul', suppletive: true }]);
    expect(suspectSuppletion(noBase)).toEqual([]);
  });
});

describe('looksRelated', () => {
  it('is true when the opening is shared', () => {
    expect(looksRelated('leip', 'leloip')).toBe(false);
    expect(looksRelated('leip', 'leips')).toBe(true);
  });

  it('is false when nothing is shared', () => {
    expect(looksRelated('fer', 'tul')).toBe(false);
  });

  it('takes the shorter string as the limit', () => {
    expect(looksRelated('ab', 'abcdef')).toBe(true);
    expect(looksRelated('ax', 'abcdef')).toBe(false);
  });

  it('is false when either side is blank', () => {
    expect(looksRelated('', 'abc')).toBe(false);
  });
});

describe('withStem and withoutStem', () => {
  it('adds a role that was not there', () => {
    expect(stemRoles(withStem(greekVerb, { role: 'passive', form: 'leiphth' }))).toContain(
      'passive',
    );
  });

  it('replaces a role that was', () => {
    const changed = withStem(greekVerb, { role: 'aorist', form: 'elip' });
    expect(stemFor(changed, 'aorist')?.form).toBe('elip');
    expect(changed.stems).toHaveLength(4);
  });

  it('leaves the original alone', () => {
    withStem(greekVerb, { role: 'aorist', form: 'elip' });
    expect(stemFor(greekVerb, 'aorist')?.form).toBe('lip');
  });

  it('drops a role and refuses to empty the set', () => {
    expect(stemRoles(withoutStem(greekVerb, 'perfect'))).not.toContain('perfect');
    expect(() => withoutStem(singleStem('mens'), 'base')).toThrow(/no stems at all/);
  });
});
