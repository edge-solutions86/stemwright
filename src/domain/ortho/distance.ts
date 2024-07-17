import { codePoints, markClusters } from '../text/normalise.js';

/**
 * Edit distance over mark clusters rather than code points. A Greek vowel
 * carrying a breathing and an accent is one thing a learner typed wrong, not
 * three, and counting it as three makes every accented word look further from
 * the answer than it is.
 */
export function clusterDistance(a: string, b: string): number {
  return levenshtein(markClusters(a), markClusters(b));
}

export function pointDistance(a: string, b: string): number {
  return levenshtein(codePoints(a), codePoints(b));
}

function levenshtein(left: readonly string[], right: readonly string[]): number {
  if (left.length === 0) {
    return right.length;
  }
  if (right.length === 0) {
    return left.length;
  }

  let previous = Array.from({ length: right.length + 1 }, (_, index) => index);
  for (let i = 1; i <= left.length; i += 1) {
    const current = [i];
    for (let j = 1; j <= right.length; j += 1) {
      const substitution = previous[j - 1]! + (left[i - 1] === right[j - 1] ? 0 : 1);
      const deletion = previous[j]! + 1;
      const insertion = current[j - 1]! + 1;
      current.push(Math.min(substitution, deletion, insertion));
    }
    previous = current;
  }
  return previous[right.length]!;
}

/** True when the two differ only by swapping one adjacent pair. */
export function isTransposition(a: string, b: string): boolean {
  const left = markClusters(a);
  const right = markClusters(b);
  if (left.length !== right.length || left.length < 2) {
    return false;
  }
  const differing: number[] = [];
  for (let index = 0; index < left.length; index += 1) {
    if (left[index] !== right[index]) {
      differing.push(index);
    }
  }
  if (differing.length !== 2) {
    return false;
  }
  const [first, second] = differing as [number, number];
  return second === first + 1 && left[first] === right[second] && left[second] === right[first];
}

export interface Divergence {
  readonly index: number;
  readonly expected: string | undefined;
  readonly given: string | undefined;
}

/** The first cluster where the two forms part company, counted from the left. */
export function firstDivergence(expected: string, given: string): Divergence | undefined {
  const left = markClusters(expected);
  const right = markClusters(given);
  const limit = Math.max(left.length, right.length);
  for (let index = 0; index < limit; index += 1) {
    if (left[index] !== right[index]) {
      return { index, expected: left[index], given: right[index] };
    }
  }
  return undefined;
}

export function sharedPrefixLength(a: string, b: string): number {
  const left = markClusters(a);
  const right = markClusters(b);
  let shared = 0;
  while (shared < left.length && shared < right.length && left[shared] === right[shared]) {
    shared += 1;
  }
  return shared;
}

export function sharedSuffixLength(a: string, b: string): number {
  const left = markClusters(a);
  const right = markClusters(b);
  let shared = 0;
  while (
    shared < left.length &&
    shared < right.length &&
    left[left.length - 1 - shared] === right[right.length - 1 - shared]
  ) {
    shared += 1;
  }
  return shared;
}

/**
 * A similarity in nought to one, used to rank candidates when a learner's
 * answer matches no cell exactly and the drill wants to say what they probably
 * meant. Identical strings score one and strings sharing nothing score zero.
 */
export function similarity(a: string, b: string): number {
  const longest = Math.max(markClusters(a).length, markClusters(b).length);
  if (longest === 0) {
    return 1;
  }
  return 1 - clusterDistance(a, b) / longest;
}
