import { ProblemCollector, ValidationError } from '../errors.js';
import { asSlug } from '../identifiers.js';
import { requireText } from '../text/normalise.js';

/**
 * An inflection class is the answer to "which paradigm does this word follow".
 * Classes form a tree: `o-stem` has `o-stem.contract` under it, and a contract
 * noun is an o stem in every respect the child does not override.
 *
 * The tree is load bearing rather than decorative. A paradigm profile is
 * attached to a class, and a lexeme with no profile of its own inherits the
 * nearest ancestor that has one, so moving a profile up the tree silently
 * changes what a hundred lexemes are graded against. Every lookup here is
 * therefore explicit about which class it resolved to.
 */
export type PartOfSpeech = 'noun' | 'verb' | 'adjective' | 'pronoun' | 'numeral' | 'participle';

export const PARTS_OF_SPEECH: readonly PartOfSpeech[] = [
  'noun',
  'verb',
  'adjective',
  'pronoun',
  'numeral',
  'participle',
];

export interface InflectionClass {
  readonly key: string;
  readonly label: string;
  readonly partOfSpeech: PartOfSpeech;
  readonly parent: string | undefined;
  readonly note: string | undefined;
}

export interface InflectionClassInput {
  readonly key: string;
  readonly label?: string;
  readonly partOfSpeech: PartOfSpeech;
  readonly parent?: string;
  readonly note?: string;
}

export interface ClassRegistry {
  readonly language: string;
  readonly classes: ReadonlyMap<string, InflectionClass>;
}

export function isPartOfSpeech(value: string): value is PartOfSpeech {
  return (PARTS_OF_SPEECH as readonly string[]).includes(value);
}

export function classRegistry(
  language: string,
  inputs: readonly InflectionClassInput[],
): ClassRegistry {
  const problems = new ProblemCollector();
  const classes = new Map<string, InflectionClass>();

  for (const input of inputs) {
    const key = asSlug('class', input.key);
    if (classes.has(key)) {
      problems.add(`classes.${key}`, 'is declared twice');
      continue;
    }
    const partOfSpeech: string = input.partOfSpeech;
    if (!isPartOfSpeech(partOfSpeech)) {
      problems.add(`classes.${key}.partOfSpeech`, `is not a part of speech: ${partOfSpeech}`);
      continue;
    }
    classes.set(key, {
      key,
      label: input.label === undefined ? key : requireText(`classes.${key}.label`, input.label, 96),
      partOfSpeech,
      parent:
        input.parent === undefined ? undefined : asSlug(`classes.${key}.parent`, input.parent),
      note:
        input.note === undefined ? undefined : requireText(`classes.${key}.note`, input.note, 400),
    });
  }

  for (const declared of classes.values()) {
    if (declared.parent === undefined) {
      continue;
    }
    const parent = classes.get(declared.parent);
    if (parent === undefined) {
      problems.add(`classes.${declared.key}.parent`, `names an unknown class: ${declared.parent}`);
      continue;
    }
    if (parent.partOfSpeech !== declared.partOfSpeech) {
      problems.add(
        `classes.${declared.key}.partOfSpeech`,
        `is ${declared.partOfSpeech} but its parent ${parent.key} is ${parent.partOfSpeech}`,
      );
    }
  }

  for (const declared of classes.values()) {
    if (hasCycle(classes, declared.key)) {
      problems.add(`classes.${declared.key}.parent`, 'takes part in a cycle');
    }
  }

  problems.throwIfAny(`inflection classes for ${language} are not usable`);
  return { language: asSlug('language', language), classes };
}

function hasCycle(classes: ReadonlyMap<string, InflectionClass>, start: string): boolean {
  const seen = new Set<string>([start]);
  let current = classes.get(start)?.parent;
  while (current !== undefined) {
    if (seen.has(current)) {
      return true;
    }
    seen.add(current);
    current = classes.get(current)?.parent;
  }
  return false;
}

export function classOf(registry: ClassRegistry, key: string): InflectionClass | undefined {
  return registry.classes.get(key);
}

export function requireClass(registry: ClassRegistry, key: string): InflectionClass {
  const found = registry.classes.get(key);
  if (found === undefined) {
    throw ValidationError.field('class', `is not a class of ${registry.language}: ${key}`);
  }
  return found;
}

/** The class itself first, then each ancestor up to the root. */
export function classChain(registry: ClassRegistry, key: string): InflectionClass[] {
  const chain: InflectionClass[] = [];
  let current: string | undefined = key;
  while (current !== undefined) {
    const found = registry.classes.get(current);
    if (found === undefined) {
      break;
    }
    chain.push(found);
    current = found.parent;
  }
  return chain;
}

export function isDescendantOf(registry: ClassRegistry, key: string, ancestor: string): boolean {
  return classChain(registry, key)
    .slice(1)
    .some((candidate) => candidate.key === ancestor);
}

export function childrenOf(registry: ClassRegistry, key: string): InflectionClass[] {
  return [...registry.classes.values()]
    .filter((candidate) => candidate.parent === key)
    .sort((a, b) => a.key.localeCompare(b.key));
}

export function rootClasses(registry: ClassRegistry): InflectionClass[] {
  return [...registry.classes.values()]
    .filter((candidate) => candidate.parent === undefined)
    .sort((a, b) => a.key.localeCompare(b.key));
}

export function classesFor(registry: ClassRegistry, partOfSpeech: PartOfSpeech): InflectionClass[] {
  return [...registry.classes.values()]
    .filter((candidate) => candidate.partOfSpeech === partOfSpeech)
    .sort((a, b) => a.key.localeCompare(b.key));
}

/**
 * Walks up from a class and returns the first ancestor for which the caller has
 * something. This is how a profile, an orthography override or a note is
 * resolved, and the class it resolved through comes back with it so a report
 * can say where the answer came from.
 */
export function resolveThroughChain<T>(
  registry: ClassRegistry,
  key: string,
  lookup: (classKey: string) => T | undefined,
): { readonly value: T; readonly through: string } | undefined {
  for (const candidate of classChain(registry, key)) {
    const value = lookup(candidate.key);
    if (value !== undefined) {
      return { value, through: candidate.key };
    }
  }
  return undefined;
}
