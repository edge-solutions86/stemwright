import { ValidationError } from '../errors.js';
import { markClusters } from '../text/normalise.js';
import type { MatchResult } from './compare.js';
import { firstDivergence, sharedPrefixLength } from './distance.js';

/**
 * What to show a learner who got it wrong.
 *
 * Hints are graded rather than all or nothing, because the useful hint is
 * almost never the answer. A learner who typed the right stem and the wrong
 * ending needs to be told it is the ending; one who typed a different word
 * needs to be told the length; and only somebody who has asked three times
 * needs the form itself.
 *
 * Nothing here decides when to show a hint. That is a teaching decision and it
 * belongs to the deck.
 */
export type HintLevel = 'shape' | 'kind' | 'opening' | 'reveal';

/**
 * Ordered by how much of the answer each one gives away, not by how much it
 * says. Naming the kind of miss is more informative than a skeleton and still
 * reveals no letters, so it comes before the opening rather than after it, and
 * the ladder never goes backwards.
 */
export const HINT_LEVELS: readonly HintLevel[] = ['shape', 'kind', 'opening', 'reveal'];

export function isHintLevel(value: string): value is HintLevel {
  return (HINT_LEVELS as readonly string[]).includes(value);
}

export function nextLevel(level: HintLevel): HintLevel {
  const index = HINT_LEVELS.indexOf(level);
  return HINT_LEVELS[Math.min(index + 1, HINT_LEVELS.length - 1)]!;
}

export interface Hint {
  readonly level: HintLevel;
  readonly text: string;
  readonly revealed: number;
}

/** A skeleton of the form: one dot per cluster, with nothing given away. */
export function shapeOf(expected: string, marker = '.'): string {
  return marker.repeat(markClusters(expected).length);
}

/**
 * Reveals the shared opening and hides the rest. A learner who typed nothing
 * gets the first cluster, because a skeleton with no letters at all is a hint
 * only in the sense that it fills a box on a screen.
 */
export function openingOf(expected: string, given: string, marker = '.'): string {
  const clusters = markClusters(expected);
  const shared = Math.max(1, Math.min(sharedPrefixLength(expected, given), clusters.length - 1));
  return clusters.slice(0, shared).join('') + marker.repeat(clusters.length - shared);
}

function revealedIn(text: string, marker = '.'): number {
  return markClusters(text).filter((cluster) => cluster !== marker).length;
}

export function hintFor(result: MatchResult, level: HintLevel): Hint {
  if (result.verdict === 'exact' || result.verdict === 'tolerated') {
    throw ValidationError.field('hint', 'was asked for on an answer that was already right');
  }

  const clusters = markClusters(result.expected);

  switch (level) {
    case 'shape':
      return {
        level,
        text: `${clusters.length} letters: ${shapeOf(result.expected)}`,
        revealed: 0,
      };
    case 'opening': {
      const text = openingOf(result.expected, result.given);
      return { level, text, revealed: revealedIn(text) };
    }
    case 'kind': {
      const divergence = firstDivergence(result.expected, result.given);
      const at = divergence === undefined ? clusters.length : divergence.index + 1;
      return {
        level,
        text: `the ${result.miss ?? 'form'} is wrong, from letter ${at}`,
        revealed: 0,
      };
    }
    case 'reveal':
      return { level, text: result.expected, revealed: clusters.length };
  }
}

/** The whole ladder of hints for one wrong answer, in order. */
export function hintLadder(result: MatchResult): Hint[] {
  return HINT_LEVELS.map((level) => hintFor(result, level));
}

export function revealsAnswer(hint: Hint, expected: string): boolean {
  return hint.revealed >= markClusters(expected).length;
}

export function describeHint(hint: Hint): string {
  return `${hint.level}: ${hint.text}`;
}
