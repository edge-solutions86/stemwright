import { ProblemCollector, ValidationError } from '../errors.js';
import { asSlug } from '../identifiers.js';
import { normaliseInput, requireText } from '../text/normalise.js';

/**
 * A stem is the part of a word the endings attach to. Most lexemes have one.
 * Plenty have several, keyed by the part of the paradigm they serve: a Greek
 * verb has a present stem, a future stem, an aorist stem and a perfect stem,
 * and they are not derivable from one another.
 *
 * Suppletion is the case where a stem is not a variant of the others at all.
 * `fero` and `tuli` share nothing, and neither does `go` and `went`. The model
 * marks it rather than hiding it, because a drill that asks a learner to build
 * a suppletive form from the citation stem is asking for something impossible.
 */
export interface Stem {
  readonly role: string;
  readonly form: string;
  readonly suppletive: boolean;
  readonly note: string | undefined;
}

export interface StemInput {
  readonly role: string;
  readonly form: string;
  readonly suppletive?: boolean;
  readonly note?: string;
}

export const DEFAULT_STEM_ROLE = 'base';

export function stem(input: StemInput): Stem {
  const role = asSlug('stem.role', input.role);
  const form = requireText(`stem.${role}.form`, input.form, 128);
  return {
    role,
    form,
    suppletive: input.suppletive ?? false,
    note: input.note === undefined ? undefined : requireText(`stem.${role}.note`, input.note, 240),
  };
}

export interface StemSet {
  readonly stems: readonly Stem[];
}

export function stemSet(inputs: readonly StemInput[]): StemSet {
  const problems = new ProblemCollector();
  if (inputs.length === 0) {
    problems.add('stems', 'must hold at least one stem');
  }
  const stems: Stem[] = [];
  const seen = new Set<string>();
  for (const input of inputs) {
    const built = stem(input);
    if (seen.has(built.role)) {
      problems.add(`stems.${built.role}`, 'is declared twice');
      continue;
    }
    seen.add(built.role);
    stems.push(built);
  }
  problems.throwIfAny('stem set is not usable');
  return { stems };
}

export function singleStem(form: string): StemSet {
  return stemSet([{ role: DEFAULT_STEM_ROLE, form }]);
}

export function stemRoles(set: StemSet): string[] {
  return set.stems.map((entry) => entry.role);
}

export function stemFor(set: StemSet, role: string): Stem | undefined {
  return set.stems.find((entry) => entry.role === role);
}

/**
 * Resolves the stem a paradigm slot asks for, falling back to the base stem.
 * The fallback is what makes a one stem lexeme usable by a profile written for
 * a four stem language, and it is why the role a slot names is never assumed to
 * exist.
 */
export function resolveStem(set: StemSet, role: string): Stem {
  const exact = stemFor(set, role);
  if (exact !== undefined) {
    return exact;
  }
  const base = stemFor(set, DEFAULT_STEM_ROLE);
  if (base === undefined) {
    throw ValidationError.field('stems', `has neither a ${role} stem nor a base stem`);
  }
  return base;
}

export function hasSuppletion(set: StemSet): boolean {
  return set.stems.some((entry) => entry.suppletive);
}

export function suppletiveRoles(set: StemSet): string[] {
  return set.stems.filter((entry) => entry.suppletive).map((entry) => entry.role);
}

export function withStem(set: StemSet, input: StemInput): StemSet {
  const built = stem(input);
  const kept = set.stems.filter((entry) => entry.role !== built.role);
  return { stems: [...kept, built] };
}

export function withoutStem(set: StemSet, role: string): StemSet {
  const kept = set.stems.filter((entry) => entry.role !== role);
  if (kept.length === 0) {
    throw ValidationError.field('stems', 'would be left with no stems at all');
  }
  return { stems: kept };
}

/**
 * Two stems are variants when one is a prefix of the other or they share a long
 * enough opening. Used to warn an editor that a stem marked suppletive probably
 * is not, which is a common mistake in a hand written lexicon.
 */
export function looksRelated(first: string, second: string, minimum = 3): boolean {
  const a = normaliseInput(first).toLowerCase();
  const b = normaliseInput(second).toLowerCase();
  if (a === '' || b === '') {
    return false;
  }
  let shared = 0;
  const limit = Math.min(a.length, b.length);
  while (shared < limit && a[shared] === b[shared]) {
    shared += 1;
  }
  return shared >= Math.min(minimum, limit);
}

export function suspectSuppletion(set: StemSet): string[] {
  const base = stemFor(set, DEFAULT_STEM_ROLE);
  if (base === undefined) {
    return [];
  }
  return set.stems
    .filter((entry) => entry.suppletive && looksRelated(entry.form, base.form))
    .map((entry) => entry.role);
}
