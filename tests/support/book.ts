import { profileBook } from '../../src/domain/paradigm/book.js';
import { revisionHistory } from '../../src/domain/paradigm/revision.js';
import { syncretismMap } from '../../src/domain/paradigm/syncretism.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { firstDeclension, latinNoun, secondNeuter } from './profiles.js';

const d = (value: string) => parseCivilDate(value);

export const firstHistory = revisionHistory('first-declension', [
  {
    effectiveFrom: d('2024-09-01'),
    profile: firstDeclension,
    syncretism: syncretismMap(latinNoun, [
      { key: 'ae', members: ['gen.sg', 'dat.sg', 'nom.pl'] },
      { key: 'is', members: ['dat.pl', 'abl.pl'] },
    ]),
  },
]);

export const neuterHistory = revisionHistory('second-neuter', [
  { effectiveFrom: d('2024-09-01'), profile: secondNeuter },
  {
    effectiveFrom: d('2025-02-01'),
    profile: secondNeuter,
    syncretism: syncretismMap(latinNoun, [
      { key: 'neuter-rule', members: ['nom.sg', 'acc.sg'] },
      { key: 'neuter-plural', members: ['nom.pl', 'acc.pl'] },
    ]),
    reason: 'the neuter rule was made explicit for the second year syllabus',
  },
]);

export const latinBook = profileBook({
  language: 'latin',
  histories: [firstHistory, neuterHistory],
  bindings: {
    'first-declension': 'first-declension',
    'second-declension': 'second-neuter',
  },
});
