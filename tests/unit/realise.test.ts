import { describe, expect, it } from 'vitest';
import { featureBundle } from '../../src/domain/features/bundle.js';
import {
  NO_RULES,
  collidingForms,
  explainRealisation,
  formOf,
  joinAtSeam,
  realise,
  realiseAll,
  ruleSet,
} from '../../src/domain/paradigm/realise.js';
import { paradigmProfile } from '../../src/domain/paradigm/profile.js';
import { lexeme } from '../../src/domain/lexicon/lexeme.js';
import { stemSet } from '../../src/domain/lexicon/stem.js';
import { latinClasses } from '../support/classes.js';
import { problemMessages } from '../support/problems.js';
import { firstDeclension, latinNoun, secondNeuter } from '../support/profiles.js';

const context = { schema: latinNoun, classes: latinClasses };

const puella = lexeme(context, {
  lemma: 'puella',
  classKey: 'first-declension',
  stems: stemSet([{ role: 'base', form: 'puell' }]),
});

const bellum = lexeme(context, {
  lemma: 'bellum',
  classKey: 'second-declension.neuter',
  stems: stemSet([{ role: 'base', form: 'bell' }]),
});

const latinRules = ruleSet('latin', [
  { key: 'g-before-s', stemEnd: 'g', affixStart: 's', becomes: 'x', note: 'reg plus si is rexi' },
  { key: 'g-before-t', stemEnd: 'g', affixStart: 't', becomes: 'ct' },
  { key: 'double-s', stemEnd: 's', affixStart: 's', becomes: 's' },
]);

describe('ruleSet', () => {
  it('keeps the rules in order', () => {
    expect(latinRules.rules.map((rule) => rule.key)).toEqual([
      'g-before-s',
      'g-before-t',
      'double-s',
    ]);
  });

  it('refuses a repeated key', () => {
    expect(
      problemMessages(() =>
        ruleSet('latin', [
          { key: 'a', stemEnd: 'g', affixStart: 's', becomes: 'x' },
          { key: 'a', stemEnd: 'g', affixStart: 't', becomes: 'ct' },
        ]),
      ),
    ).toEqual(['rules.1 repeats the rule key a']);
  });

  it('refuses a rule that constrains neither side', () => {
    expect(
      problemMessages(() =>
        ruleSet('latin', [{ key: 'a', stemEnd: '', affixStart: '', becomes: 'x' }]),
      ),
    ).toEqual(['rules.0 must constrain at least one side of the seam']);
  });
});

describe('joinAtSeam', () => {
  it('concatenates when nothing matches', () => {
    expect(joinAtSeam(latinRules, 'puell', 'ae')).toEqual({ form: 'puellae', steps: [] });
  });

  it('rewrites the seam when a rule matches both sides', () => {
    const joined = joinAtSeam(latinRules, 'reg', 'si');
    expect(joined.form).toBe('rexi');
    expect(joined.steps).toEqual([{ rule: 'g-before-s', before: 'regsi', after: 'rexi' }]);
  });

  it('takes the first matching rule and stops', () => {
    expect(joinAtSeam(latinRules, 'ag', 'tus').form).toBe('actus');
  });

  it('does not cascade a second rule onto the rewritten form', () => {
    const cascading = ruleSet('x', [
      { key: 'a-to-s', stemEnd: 'a', affixStart: 'b', becomes: 's' },
      { key: 'double-s', stemEnd: 's', affixStart: 's', becomes: 's' },
    ]);
    expect(joinAtSeam(cascading, 'ma', 'bs').form).toBe('mss');
  });

  it('matches a rule that constrains only the stem side', () => {
    const stemOnly = ruleSet('x', [{ key: 'drop-n', stemEnd: 'n', affixStart: '', becomes: '' }]);
    expect(joinAtSeam(stemOnly, 'man', 'us').form).toBe('maus');
  });

  it('joins with an empty affix', () => {
    expect(joinAtSeam(NO_RULES, 'vis', '').form).toBe('vis');
  });
});

describe('realise', () => {
  const genSg = featureBundle(latinNoun, { case: 'gen', number: 'sg' });

  it('builds a form from the stem and the ending', () => {
    expect(formOf(NO_RULES, puella, firstDeclension, genSg)).toBe('puellae');
    expect(formOf(NO_RULES, bellum, secondNeuter, genSg)).toBe('belli');
  });

  it('reports which stem it used', () => {
    expect(realise(NO_RULES, puella, firstDeclension, genSg).stemRole).toBe('base');
  });

  it('falls back to the base stem when the slot names one the lexeme lacks', () => {
    const profile = paradigmProfile(latinNoun, {
      key: 'p',
      classKey: 'c',
      slots: [{ bundle: 'gen.sg', stemRole: 'oblique', affix: 'is' }],
    });
    const realised = realise(NO_RULES, puella, profile, genSg);
    expect(realised.stemRole).toBe('base');
    expect(realised.form).toBe('puellis');
  });

  it('refuses a bundle the profile has no slot for', () => {
    const bundle = featureBundle(latinNoun, { case: 'gen' });
    expect(() => realise(NO_RULES, puella, firstDeclension, bundle)).toThrow(/has no slot/);
  });
});

describe('literal slots', () => {
  const vis = lexeme(context, {
    lemma: 'vis',
    classKey: 'third-declension',
    stems: stemSet([{ role: 'base', form: 'vir' }]),
  });
  const profile = paradigmProfile(latinNoun, {
    key: 'vis',
    classKey: 'third-declension',
    slots: [
      { bundle: 'nom.sg', literal: 'vis' },
      { bundle: 'gen.pl', affix: 'ium' },
    ],
  });

  it('takes the literal form and ignores the stem', () => {
    const bundle = featureBundle(latinNoun, { case: 'nom', number: 'sg' });
    const realised = realise(NO_RULES, vis, profile, bundle);
    expect(realised.form).toBe('vis');
    expect(realised.fromLiteral).toBe(true);
    expect(realised.stem).toBe('');
  });

  it('still builds the other slots from the stem', () => {
    const bundle = featureBundle(latinNoun, { case: 'gen', number: 'pl' });
    expect(formOf(NO_RULES, vis, profile, bundle)).toBe('virium');
  });
});

describe('realiseAll and collidingForms', () => {
  it('realises every slot in order', () => {
    const forms = realiseAll(NO_RULES, puella, firstDeclension).map((entry) => entry.form);
    expect(forms).toEqual([
      'puella',
      'puellae',
      'puellae',
      'puellam',
      'puella',
      'puellae',
      'puellarum',
      'puellis',
      'puellas',
      'puellis',
    ]);
  });

  it('groups the bundles that end up spelled the same', () => {
    const collisions = collidingForms(NO_RULES, puella, firstDeclension);
    expect(collisions.get('puellae')).toHaveLength(3);
    expect(collisions.get('puellis')).toHaveLength(2);
    expect(collisions.has('puellarum')).toBe(false);
  });

  it('finds no collisions when every ending is distinct', () => {
    const distinct = paradigmProfile(latinNoun, {
      key: 'd',
      classKey: 'c',
      slots: [
        { bundle: 'nom.sg', affix: 'a' },
        { bundle: 'gen.sg', affix: 'b' },
      ],
    });
    expect(collidingForms(NO_RULES, puella, distinct).size).toBe(0);
  });
});

describe('explainRealisation', () => {
  const genSg = featureBundle(latinNoun, { case: 'gen', number: 'sg' });

  it('reads a plain join out', () => {
    const realised = realise(NO_RULES, puella, firstDeclension, genSg);
    expect(explainRealisation(latinNoun, realised, genSg)).toBe(
      'puell plus ae gives puellae in latin',
    );
  });

  it('names the rule that fired', () => {
    const rex = lexeme(context, {
      lemma: 'rex',
      classKey: 'third-declension',
      stems: stemSet([{ role: 'base', form: 'reg' }]),
    });
    const profile = paradigmProfile(latinNoun, {
      key: 'p',
      classKey: 'c',
      slots: [{ bundle: 'gen.sg', affix: 'sis' }],
    });
    const realised = realise(latinRules, rex, profile, genSg);
    expect(explainRealisation(latinNoun, realised, genSg)).toBe(
      'reg plus sis gives rexis in latin after g-before-s',
    );
  });

  it('says so for a literal form', () => {
    const profile = paradigmProfile(latinNoun, {
      key: 'p',
      classKey: 'c',
      slots: [{ bundle: 'gen.sg', literal: 'vis' }],
    });
    const realised = realise(NO_RULES, puella, profile, genSg);
    expect(explainRealisation(latinNoun, realised, genSg)).toBe(
      'vis is stated outright by the profile',
    );
  });

  it('refuses to explain a cell against the wrong bundle', () => {
    const realised = realise(NO_RULES, puella, firstDeclension, genSg);
    const other = featureBundle(latinNoun, { case: 'dat', number: 'sg' });
    expect(() => explainRealisation(latinNoun, realised, other)).toThrow(/does not match/);
  });
});
