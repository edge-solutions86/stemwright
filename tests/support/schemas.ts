import { featureSchema } from '../../src/domain/features/schema.js';

/** The schemas the unit tests share. Kept small enough to enumerate by hand. */
export const latinNounSchema = featureSchema({
  language: 'latin',
  dimensions: [
    { key: 'case', values: ['nom', 'gen', 'dat', 'acc', 'abl', 'voc'] },
    { key: 'number', values: ['sg', 'pl'] },
  ],
});

export const latinAdjectiveSchema = featureSchema({
  language: 'latin-adj',
  dimensions: [
    { key: 'case', values: ['nom', 'gen', 'dat', 'acc', 'abl', 'voc'] },
    { key: 'number', values: ['sg', 'pl'] },
    { key: 'gender', values: ['masc', 'fem', 'neut'] },
  ],
});

export const greekVerbSchema = featureSchema({
  language: 'greek',
  dimensions: [
    { key: 'tense', values: ['pres', 'impf', 'fut', 'aor', 'perf'] },
    { key: 'mood', values: ['ind', 'subj', 'opt', 'imp'] },
    { key: 'voice', values: ['act', 'mid', 'pass'] },
    {
      key: 'person',
      values: [
        { key: 'first', abbreviation: '1' },
        { key: 'second', abbreviation: '2' },
        { key: 'third', abbreviation: '3' },
      ],
    },
    { key: 'number', values: ['sg', 'du', 'pl'], optional: true },
  ],
});
