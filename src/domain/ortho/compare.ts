import { normaliseInput, stripMarks } from '../text/normalise.js';
import type { FoldStepKey } from './folding.js';
import { applyFolds, foldsThatMattered } from './folding.js';
import { clusterDistance, isTransposition, sharedPrefixLength, similarity } from './distance.js';
import type { ToleranceLevel } from './tolerance.js';

/**
 * Marking one typed answer against one expected form.
 *
 * There are four outcomes and they are not a scale. `exact` and `tolerated` are
 * both right, and the difference between them is what the learner is told, not
 * what they score. `near` is wrong but worth a hint. `wrong` is wrong.
 *
 * Keeping `tolerated` distinct from `exact` is the point of the whole module. A
 * deck that folds accents still wants to be able to say "that was right, and
 * the accent goes on the second syllable", and a mark that threw the
 * information away could not.
 */
export type MatchVerdict = 'exact' | 'tolerated' | 'near' | 'wrong';

/** What the learner most likely got wrong, for the hint rather than the mark. */
export type MissKind = 'accent' | 'capital' | 'ending' | 'stem' | 'order' | 'spacing' | 'other';

export interface MatchResult {
  readonly verdict: MatchVerdict;
  readonly expected: string;
  readonly given: string;
  readonly foldsUsed: readonly FoldStepKey[];
  readonly distance: number;
  readonly miss: MissKind | undefined;
  readonly similarity: number;
}

/** How close a wrong answer has to be before it counts as a near miss. */
export const NEAR_MISS_DISTANCE = 1;

export function classifyMiss(expected: string, given: string): MissKind {
  const left = normaliseInput(expected);
  const right = normaliseInput(given);
  if (left === right) {
    return 'other';
  }
  if (left.replace(/\s+/gu, '') === right.replace(/\s+/gu, '')) {
    return 'spacing';
  }
  if (left.toLowerCase() === right.toLowerCase()) {
    return 'capital';
  }
  if (stripMarks(left) === stripMarks(right)) {
    return 'accent';
  }
  if (isTransposition(left, right)) {
    return 'order';
  }

  const shared = sharedPrefixLength(left, right);
  const stemLength = Math.ceil(Math.min(left.length, right.length) / 2);
  return shared >= stemLength ? 'ending' : 'stem';
}

export function compareForm(expected: string, given: string, level: ToleranceLevel): MatchResult {
  const cleanExpected = normaliseInput(expected);
  const cleanGiven = normaliseInput(given);
  const foldedExpected = applyFolds(cleanExpected, level.folds);
  const foldedGiven = applyFolds(cleanGiven, level.folds);
  const distance = clusterDistance(foldedExpected, foldedGiven);
  const closeness = similarity(foldedExpected, foldedGiven);

  if (cleanExpected === cleanGiven) {
    return {
      verdict: 'exact',
      expected: cleanExpected,
      given: cleanGiven,
      foldsUsed: [],
      distance: 0,
      miss: undefined,
      similarity: 1,
    };
  }

  if (foldedExpected === foldedGiven) {
    return {
      verdict: 'tolerated',
      expected: cleanExpected,
      given: cleanGiven,
      foldsUsed: foldsThatMattered(cleanExpected, cleanGiven, level.folds),
      distance: 0,
      miss: classifyMiss(cleanExpected, cleanGiven),
      similarity: 1,
    };
  }

  const near = distance <= NEAR_MISS_DISTANCE || isTransposition(foldedExpected, foldedGiven);
  return {
    verdict: near ? 'near' : 'wrong',
    expected: cleanExpected,
    given: cleanGiven,
    foldsUsed: [],
    distance,
    miss: classifyMiss(cleanExpected, cleanGiven),
    similarity: closeness,
  };
}

export function isCorrect(result: MatchResult): boolean {
  return result.verdict === 'exact' || result.verdict === 'tolerated';
}

/**
 * Marks against several acceptable forms and keeps the best outcome. A cell
 * with a variant spelling has more than one right answer, and the learner
 * should get the better of the two rather than whichever was listed first.
 */
export function compareAgainstAny(
  expectedForms: readonly string[],
  given: string,
  level: ToleranceLevel,
): MatchResult {
  const ranking: Record<MatchVerdict, number> = { exact: 3, tolerated: 2, near: 1, wrong: 0 };
  let best: MatchResult | undefined;
  for (const expected of expectedForms) {
    const result = compareForm(expected, given, level);
    if (best === undefined || ranking[result.verdict] > ranking[best.verdict]) {
      best = result;
      continue;
    }
    if (ranking[result.verdict] === ranking[best.verdict] && result.distance < best.distance) {
      best = result;
    }
  }
  return best ?? compareForm('', given, level);
}

export function describeMatch(result: MatchResult): string {
  switch (result.verdict) {
    case 'exact':
      return 'right';
    case 'tolerated':
      return result.miss === undefined
        ? 'right, with a difference the deck ignores'
        : `right, and the ${result.miss} is not marked at this tolerance`;
    case 'near':
      return `not quite, the ${result.miss ?? 'form'} is off by ${result.distance}`;
    case 'wrong':
      return `wrong, the expected form was ${result.expected}`;
  }
}
