import { classRegistry } from '../../src/domain/lexicon/inflection-class.js';

/** A small Latin class tree with two levels, shared by the lexicon tests. */
export const latinClasses = classRegistry('latin', [
  { key: 'first-declension', label: 'First declension', partOfSpeech: 'noun' },
  { key: 'second-declension', label: 'Second declension', partOfSpeech: 'noun' },
  {
    key: 'second-declension.neuter',
    label: 'Second declension neuter',
    partOfSpeech: 'noun',
    parent: 'second-declension',
  },
  {
    key: 'second-declension.r-stem',
    label: 'Second declension in r',
    partOfSpeech: 'noun',
    parent: 'second-declension',
    note: 'ager and puer keep the r of the nominative through the paradigm',
  },
  { key: 'third-declension', label: 'Third declension', partOfSpeech: 'noun' },
  {
    key: 'third-declension.i-stem',
    label: 'Third declension i stem',
    partOfSpeech: 'noun',
    parent: 'third-declension',
  },
  { key: 'first-conjugation', label: 'First conjugation', partOfSpeech: 'verb' },
  { key: 'third-conjugation', label: 'Third conjugation', partOfSpeech: 'verb' },
  {
    key: 'third-conjugation.io',
    label: 'Third conjugation in io',
    partOfSpeech: 'verb',
    parent: 'third-conjugation',
  },
]);
