import { ProblemCollector, NotFoundError } from '../errors.js';
import { asSlug } from '../identifiers.js';
import type { ClassRegistry } from '../lexicon/inflection-class.js';
import { classChain, resolveThroughChain } from '../lexicon/inflection-class.js';
import type { CivilDate } from '../time/civil.js';
import type { ProfileRevision, RevisionHistory } from './revision.js';
import { revisionInForce } from './revision.js';

/**
 * The book is where a class meets its paradigm. It holds every revision history
 * by profile key, and a binding from inflection class to profile key.
 *
 * Bindings are inherited up the class tree, so `second-declension.neuter` with
 * no binding of its own uses whatever `second-declension` uses. That is the
 * useful default and it is also the dangerous one, which is why every lookup
 * returns the class it resolved through rather than only the profile.
 */
export interface ProfileBook {
  readonly language: string;
  readonly histories: ReadonlyMap<string, RevisionHistory>;
  readonly bindings: ReadonlyMap<string, string>;
}

export interface ProfileBookInput {
  readonly language: string;
  readonly histories: readonly RevisionHistory[];
  readonly bindings: Readonly<Record<string, string>>;
}

export function profileBook(input: ProfileBookInput): ProfileBook {
  const problems = new ProblemCollector();
  const histories = new Map<string, RevisionHistory>();

  for (const history of input.histories) {
    if (histories.has(history.profileKey)) {
      problems.add(`histories.${history.profileKey}`, 'appears twice');
      continue;
    }
    histories.set(history.profileKey, history);
  }

  const bindings = new Map<string, string>();
  for (const [rawClass, rawProfile] of Object.entries(input.bindings)) {
    const classKey = asSlug('bindings', rawClass);
    const profileKey = asSlug(`bindings.${classKey}`, rawProfile);
    if (!histories.has(profileKey)) {
      problems.add(`bindings.${classKey}`, `names a profile the book does not hold: ${profileKey}`);
      continue;
    }
    bindings.set(classKey, profileKey);
  }

  problems.throwIfAny(`profile book for ${input.language} is not usable`);
  return { language: asSlug('language', input.language), histories, bindings };
}

export interface ResolvedProfile {
  readonly revision: ProfileRevision;
  /** The class whose binding was used, which may be an ancestor. */
  readonly boundThrough: string;
}

export function historyFor(book: ProfileBook, profileKey: string): RevisionHistory | undefined {
  return book.histories.get(profileKey);
}

export function bindingFor(
  book: ProfileBook,
  classes: ClassRegistry,
  classKey: string,
): { readonly profileKey: string; readonly boundThrough: string } | undefined {
  const resolved = resolveThroughChain(classes, classKey, (candidate) =>
    book.bindings.get(candidate),
  );
  return resolved === undefined
    ? undefined
    : { profileKey: resolved.value, boundThrough: resolved.through };
}

/**
 * The profile revision a class was graded against on a day. Three things can go
 * missing here and they are different failures: the class may have no binding,
 * the binding may name a history that is gone, or the profile may not have been
 * published yet on that day.
 */
export function profileFor(
  book: ProfileBook,
  classes: ClassRegistry,
  classKey: string,
  on: CivilDate,
): ResolvedProfile | undefined {
  const binding = bindingFor(book, classes, classKey);
  if (binding === undefined) {
    return undefined;
  }
  const history = book.histories.get(binding.profileKey);
  if (history === undefined) {
    return undefined;
  }
  const revision = revisionInForce(history, on);
  return revision === undefined ? undefined : { revision, boundThrough: binding.boundThrough };
}

export function requireProfileFor(
  book: ProfileBook,
  classes: ClassRegistry,
  classKey: string,
  on: CivilDate,
): ResolvedProfile {
  const resolved = profileFor(book, classes, classKey, on);
  if (resolved === undefined) {
    throw new NotFoundError('profile', `${classKey} on ${on}`);
  }
  return resolved;
}

/** Classes with no binding anywhere up their chain, which is an editorial hole. */
export function unboundClasses(book: ProfileBook, classes: ClassRegistry): string[] {
  return [...classes.classes.keys()]
    .filter((classKey) => bindingFor(book, classes, classKey) === undefined)
    .sort();
}

/** Bindings that are shadowed by a nearer one and so can never be reached. */
export function shadowedBindings(book: ProfileBook, classes: ClassRegistry): string[] {
  const shadowed: string[] = [];
  for (const classKey of book.bindings.keys()) {
    const chain = classChain(classes, classKey);
    if (chain.length === 0) {
      shadowed.push(classKey);
    }
  }
  return shadowed.sort();
}

export function withBinding(book: ProfileBook, classKey: string, profileKey: string): ProfileBook {
  return profileBook({
    language: book.language,
    histories: [...book.histories.values()],
    bindings: { ...Object.fromEntries(book.bindings), [classKey]: profileKey },
  });
}

export function withHistory(book: ProfileBook, history: RevisionHistory): ProfileBook {
  const kept = [...book.histories.values()].filter(
    (candidate) => candidate.profileKey !== history.profileKey,
  );
  return profileBook({
    language: book.language,
    histories: [...kept, history],
    bindings: Object.fromEntries(book.bindings),
  });
}
