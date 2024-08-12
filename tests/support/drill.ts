import { buildLexicon } from '../../src/domain/lexicon/lexicon.js';
import { lexeme } from '../../src/domain/lexicon/lexeme.js';
import { stemSet } from '../../src/domain/lexicon/stem.js';
import type { DrillContext } from '../../src/domain/drill/membership.js';
import { latinClasses } from './classes.js';
import { latinBook } from './book.js';
import { latinNoun } from './profiles.js';

const lexemeContext = { schema: latinNoun, classes: latinClasses };

export const puella = lexeme(lexemeContext, {
  lemma: 'puella',
  classKey: 'first-declension',
  stems: stemSet([{ role: 'base', form: 'puell' }]),
  glosses: ['girl'],
  tags: ['core'],
});

export const bellum = lexeme(lexemeContext, {
  lemma: 'bellum',
  classKey: 'second-declension.neuter',
  stems: stemSet([{ role: 'base', form: 'bell' }]),
  glosses: ['war'],
  tags: ['core'],
});

export const ager = lexeme(lexemeContext, {
  lemma: 'ager',
  classKey: 'second-declension.r-stem',
  stems: stemSet([{ role: 'base', form: 'agr' }]),
  glosses: ['field'],
  tags: ['advanced'],
});

/** Third declension is deliberately unbound in the book, so it gets skipped. */
export const rex = lexeme(lexemeContext, {
  lemma: 'rex',
  classKey: 'third-declension',
  stems: stemSet([{ role: 'base', form: 'reg' }]),
  glosses: ['king'],
  tags: ['core'],
});

export const latinLexicon = buildLexicon('latin', [puella, bellum, ager, rex]);

export const drillContext: DrillContext = {
  schema: latinNoun,
  classes: latinClasses,
  book: latinBook,
  lexicon: latinLexicon,
};
