import { orthoProfile } from '../../src/domain/ortho/tolerance.js';

/** Three levels, chosen so one pair is comparable and one pair is not. */
export const greekOrtho = orthoProfile({
  language: 'greek',
  defaultLevel: 'standard',
  levels: [
    { key: 'exact', label: 'Exact', folds: [] },
    { key: 'standard', label: 'Standard', folds: ['lowercase', 'final-forms'] },
    { key: 'lenient', label: 'Lenient', folds: ['lowercase', 'final-forms', 'marks'] },
    { key: 'unaccented', label: 'Unaccented', folds: ['marks', 'punctuation'] },
  ],
});

export const latinOrtho = orthoProfile({
  language: 'latin',
  defaultLevel: 'standard',
  levels: [
    { key: 'exact', folds: [] },
    { key: 'standard', folds: ['lowercase', 'length-marks'] },
  ],
});
