import { ValidationError } from '../errors.js';
import { normaliseInput, stripMarks, toForm } from '../text/normalise.js';

/**
 * A fold is a named, reversible in spirit simplification of a written form.
 * Grading never compares raw strings: it folds both sides by the same list of
 * steps and compares the results, so what counts as the same answer is a
 * property of the deck rather than of the comparison code.
 *
 * Every step is total and idempotent. Applying a list twice gives what applying
 * it once gave, which is what lets a fold list be stored on a deck, on a cell
 * override and on a report without anybody having to reason about order.
 */
export type FoldStepKey =
  | 'lowercase'
  | 'marks'
  | 'final-forms'
  | 'length-marks'
  | 'punctuation'
  | 'spaces';

export interface FoldStep {
  readonly key: FoldStepKey;
  readonly label: string;
  readonly apply: (text: string) => string;
}

/**
 * Letters that take a different shape at the end of a word. A learner typing on
 * a keyboard that does not switch automatically produces the medial form, and
 * whether that counts as wrong is a teaching decision, not a technical one.
 */
const FINAL_FORMS = new Map<string, string>([
  ['ς', 'σ'],
  ['ך', 'כ'],
  ['ם', 'מ'],
  ['ן', 'נ'],
  ['ף', 'פ'],
  ['ץ', 'צ'],
]);

/** Marks that only say how long a vowel is, which many editions omit. */
const LENGTH_MARKS = new Set([0x0304, 0x0306]);

const PUNCTUATION = /[.,;:!?'"()[\]{}]/gu;

function foldFinalForms(text: string): string {
  let out = '';
  for (const point of text) {
    out += FINAL_FORMS.get(point) ?? point;
  }
  return out;
}

function foldLengthMarks(text: string): string {
  let out = '';
  for (const point of toForm(text, 'NFD')) {
    if (!LENGTH_MARKS.has(point.codePointAt(0) ?? 0)) {
      out += point;
    }
  }
  return toForm(out, 'NFC');
}

const STEP_LIST: readonly FoldStep[] = [
  { key: 'lowercase', label: 'ignore capitals', apply: (text) => text.toLowerCase() },
  { key: 'marks', label: 'ignore accents and breathings', apply: stripMarks },
  {
    key: 'final-forms',
    label: 'accept a medial letter at the end of a word',
    apply: foldFinalForms,
  },
  { key: 'length-marks', label: 'ignore macrons and breves', apply: foldLengthMarks },
  {
    key: 'punctuation',
    label: 'ignore punctuation',
    apply: (text) => text.replace(PUNCTUATION, ''),
  },
  { key: 'spaces', label: 'ignore spacing', apply: (text) => text.replace(/\s+/gu, '') },
];

export const FOLD_STEPS: ReadonlyMap<FoldStepKey, FoldStep> = new Map(
  STEP_LIST.map((step) => [step.key, step]),
);

export const FOLD_ORDER: readonly FoldStepKey[] = [
  'punctuation',
  'lowercase',
  'final-forms',
  'length-marks',
  'marks',
  'spaces',
];

export function isFoldStepKey(value: string): value is FoldStepKey {
  return FOLD_STEPS.has(value as FoldStepKey);
}

export function requireFoldStep(key: string): FoldStep {
  const step = FOLD_STEPS.get(key as FoldStepKey);
  if (step === undefined) {
    throw ValidationError.field('fold', `is not a fold step: ${key}`);
  }
  return step;
}

/**
 * Steps always run in `FOLD_ORDER`, whatever order the caller listed them in.
 * Two decks asking for the same set therefore fold identically, which they
 * would not if the list order were honoured.
 */
export function applyFolds(text: string, keys: readonly FoldStepKey[]): string {
  const wanted = new Set(keys);
  let out = normaliseInput(text);
  for (const key of FOLD_ORDER) {
    if (wanted.has(key)) {
      out = requireFoldStep(key).apply(out);
    }
  }
  return out;
}

export function describeFolds(keys: readonly FoldStepKey[]): string[] {
  const wanted = new Set(keys);
  return FOLD_ORDER.filter((key) => wanted.has(key)).map((key) => requireFoldStep(key).label);
}

export function normaliseFoldList(keys: readonly string[]): FoldStepKey[] {
  const out: FoldStepKey[] = [];
  for (const key of keys) {
    if (!isFoldStepKey(key)) {
      throw ValidationError.field('folds', `is not a fold step: ${key}`);
    }
    if (!out.includes(key)) {
      out.push(key);
    }
  }
  return FOLD_ORDER.filter((key) => out.includes(key));
}

/** True when the two forms are the same once folded. */
export function foldedEqual(a: string, b: string, keys: readonly FoldStepKey[]): boolean {
  return applyFolds(a, keys) === applyFolds(b, keys);
}

/**
 * The subset of the given steps that actually changes this pair. Used to tell a
 * learner what they got away with rather than only that they were right.
 */
export function foldsThatMattered(
  expected: string,
  answer: string,
  keys: readonly FoldStepKey[],
): FoldStepKey[] {
  if (!foldedEqual(expected, answer, keys)) {
    return [];
  }
  const mattered: FoldStepKey[] = [];
  for (const key of normaliseFoldList(keys)) {
    const without = normaliseFoldList(keys).filter((candidate) => candidate !== key);
    if (!foldedEqual(expected, answer, without)) {
      mattered.push(key);
    }
  }
  return mattered;
}
