import { course } from '../../src/domain/course/course.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';

const d = (value: string) => parseCivilDate(value);

export const michaelmas = course({
  key: 'latin-1a',
  title: 'Latin 1A',
  language: 'latin',
  from: d('2024-10-07'),
  to: d('2024-12-13'),
  deckKeys: ['all', 'core'],
  instructorIds: ['lrn_teacher'],
});
