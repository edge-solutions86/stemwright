import { featureSchema } from '../../src/domain/features/schema.js';
import type { LexemeContext, LexemeInput } from '../../src/domain/lexicon/lexeme.js';
import { lexeme } from '../../src/domain/lexicon/lexeme.js';
import { stemSet } from '../../src/domain/lexicon/stem.js';
import { latinClasses } from './classes.js';

export const latinFullSchema = featureSchema({
  language: 'latin',
  dimensions: [
    { key: 'case', values: ['nom', 'gen', 'dat', 'acc', 'abl', 'voc'] },
    { key: 'number', values: ['sg', 'pl'] },
    { key: 'gender', values: ['masc', 'fem', 'neut'], optional: true },
  ],
});

export const latinContext: LexemeContext = { schema: latinFullSchema, classes: latinClasses };

export function makeLexeme(input: LexemeInput) {
  return lexeme(latinContext, input);
}

export const puella = makeLexeme({
  lemma: 'puella',
  classKey: 'first-declension',
  stems: stemSet([{ role: 'base', form: 'puell' }]),
  inherent: { gender: 'fem' },
  glosses: ['girl'],
  tags: ['core', 'chapter-one'],
});

export const bellum = makeLexeme({
  lemma: 'bellum',
  classKey: 'second-declension.neuter',
  stems: stemSet([{ role: 'base', form: 'bell' }]),
  inherent: { gender: 'neut' },
  glosses: ['war', 'campaign'],
});

export const ager = makeLexeme({
  lemma: 'ager',
  classKey: 'second-declension.r-stem',
  stems: stemSet([{ role: 'base', form: 'agr' }]),
  inherent: { gender: 'masc' },
  glosses: ['field'],
});
