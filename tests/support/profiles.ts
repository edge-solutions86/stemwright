import { featureSchema } from '../../src/domain/features/schema.js';
import { paradigmProfile } from '../../src/domain/paradigm/profile.js';

/** Case and number only, so a profile can be written out by hand in full. */
export const latinNoun = featureSchema({
  language: 'latin',
  dimensions: [
    { key: 'case', values: ['nom', 'gen', 'dat', 'acc', 'abl'] },
    { key: 'number', values: ['sg', 'pl'] },
  ],
});

/** The first declension, puella style. Every cell is stem plus ending. */
export const firstDeclension = paradigmProfile(latinNoun, {
  key: 'first-declension',
  classKey: 'first-declension',
  label: 'First declension',
  slots: [
    { bundle: 'nom.sg', affix: 'a' },
    { bundle: 'gen.sg', affix: 'ae' },
    { bundle: 'dat.sg', affix: 'ae' },
    { bundle: 'acc.sg', affix: 'am' },
    { bundle: 'abl.sg', affix: 'a' },
    { bundle: 'nom.pl', affix: 'ae' },
    { bundle: 'gen.pl', affix: 'arum' },
    { bundle: 'dat.pl', affix: 'is' },
    { bundle: 'acc.pl', affix: 'as' },
    { bundle: 'abl.pl', affix: 'is' },
  ],
});

/** The second declension neuter, bellum style. */
export const secondNeuter = paradigmProfile(latinNoun, {
  key: 'second-neuter',
  classKey: 'second-declension.neuter',
  slots: [
    { bundle: 'nom.sg', affix: 'um' },
    { bundle: 'gen.sg', affix: 'i' },
    { bundle: 'dat.sg', affix: 'o' },
    { bundle: 'acc.sg', affix: 'um' },
    { bundle: 'abl.sg', affix: 'o' },
    { bundle: 'nom.pl', affix: 'a' },
    { bundle: 'gen.pl', affix: 'orum' },
    { bundle: 'dat.pl', affix: 'is' },
    { bundle: 'acc.pl', affix: 'a' },
    { bundle: 'abl.pl', affix: 'is' },
  ],
});
