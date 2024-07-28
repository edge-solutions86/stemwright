import { ProblemCollector, ValidationError } from '../errors.js';
import { asSlug } from '../identifiers.js';
import type { FoldStepKey } from './folding.js';
import { describeFolds, normaliseFoldList } from './folding.js';

/**
 * Tolerance is how forgiving the marking is, and it is named rather than
 * numeric because the names are what appear on a syllabus. A first year deck
 * runs at `lenient` and the prose composition paper runs at `exact`, and both
 * mean something specific about accents that a number would not carry.
 *
 * The levels themselves are per language. Ignoring accents is a real choice in
 * Greek and meaningless in Latin, so the fold list behind a level name is part
 * of the language's orthography profile rather than a constant.
 */
export interface ToleranceLevel {
  readonly key: string;
  readonly label: string;
  readonly folds: readonly FoldStepKey[];
}

export interface ToleranceLevelInput {
  readonly key: string;
  readonly label?: string;
  readonly folds: readonly string[];
}

export interface OrthoProfile {
  readonly language: string;
  readonly levels: readonly ToleranceLevel[];
  readonly defaultLevel: string;
}

export interface OrthoProfileInput {
  readonly language: string;
  readonly levels: readonly ToleranceLevelInput[];
  readonly defaultLevel: string;
}

export function orthoProfile(input: OrthoProfileInput): OrthoProfile {
  const problems = new ProblemCollector();
  const language = asSlug('language', input.language);
  const levels: ToleranceLevel[] = [];
  const seen = new Set<string>();

  for (const [index, raw] of input.levels.entries()) {
    const key = asSlug(`levels.${index}.key`, raw.key);
    if (seen.has(key)) {
      problems.add(`levels.${index}`, `repeats the level key ${key}`);
      continue;
    }
    seen.add(key);
    const folds = problems.absorbFlat(`levels.${index}`, () => normaliseFoldList(raw.folds));
    if (folds === undefined) {
      continue;
    }
    levels.push({ key, label: raw.label ?? key, folds });
  }

  if (levels.length === 0) {
    problems.add('levels', 'must declare at least one level');
  }
  const defaultLevel = asSlug('defaultLevel', input.defaultLevel);
  if (levels.length > 0 && !seen.has(defaultLevel)) {
    problems.add('defaultLevel', `names a level that is not declared: ${defaultLevel}`);
  }

  problems.throwIfAny(`orthography profile for ${language} is not usable`);
  return { language, levels, defaultLevel };
}

export function levelOf(profile: OrthoProfile, key: string): ToleranceLevel | undefined {
  return profile.levels.find((level) => level.key === key);
}

export function requireLevel(profile: OrthoProfile, key: string): ToleranceLevel {
  const level = levelOf(profile, key);
  if (level === undefined) {
    throw ValidationError.field('level', `is not a level of ${profile.language}: ${key}`);
  }
  return level;
}

export function defaultLevelOf(profile: OrthoProfile): ToleranceLevel {
  return requireLevel(profile, profile.defaultLevel);
}

export function levelKeys(profile: OrthoProfile): string[] {
  return profile.levels.map((level) => level.key);
}

/**
 * A level is stricter than another when its fold list is a proper subset. This
 * is a partial order on purpose: two levels that fold different things are not
 * comparable, and pretending otherwise would let an override loosen marking
 * while claiming to tighten it.
 */
export function isStricterThan(a: ToleranceLevel, b: ToleranceLevel): boolean {
  if (a.folds.length >= b.folds.length) {
    return false;
  }
  return a.folds.every((fold) => b.folds.includes(fold));
}

export function areComparable(a: ToleranceLevel, b: ToleranceLevel): boolean {
  return a.key === b.key || isStricterThan(a, b) || isStricterThan(b, a);
}

export interface ToleranceRequest {
  /** The level the deck runs at, or nothing to take the language default. */
  readonly deckLevel?: string;
  /** A level fixed on one cell, which beats the deck. */
  readonly cellLevel?: string;
  /** A level fixed on one assignment, which beats both. */
  readonly assignmentLevel?: string;
}

export interface ResolvedTolerance {
  readonly level: ToleranceLevel;
  readonly source: 'assignment' | 'cell' | 'deck' | 'language';
}

/**
 * Resolves which level applies, most specific first. The source comes back with
 * it because a learner who is marked down at a tolerance their deck did not ask
 * for is owed an answer about where the tightening came from.
 */
export function resolveTolerance(
  profile: OrthoProfile,
  request: ToleranceRequest = {},
): ResolvedTolerance {
  if (request.assignmentLevel !== undefined) {
    return { level: requireLevel(profile, request.assignmentLevel), source: 'assignment' };
  }
  if (request.cellLevel !== undefined) {
    return { level: requireLevel(profile, request.cellLevel), source: 'cell' };
  }
  if (request.deckLevel !== undefined) {
    return { level: requireLevel(profile, request.deckLevel), source: 'deck' };
  }
  return { level: defaultLevelOf(profile), source: 'language' };
}

export function describeTolerance(resolved: ResolvedTolerance): string {
  const folds = describeFolds(resolved.level.folds);
  const from = `set by the ${resolved.source}`;
  if (folds.length === 0) {
    return `${resolved.level.label} marks exactly as written, ${from}`;
  }
  return `${resolved.level.label} will ${folds.join(' and ')}, ${from}`;
}
