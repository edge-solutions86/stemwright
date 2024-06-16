import { ProblemCollector } from '../errors.js';
import { bundleKey, isComplete } from '../features/bundle.js';
import type { FeatureSchema } from '../features/schema.js';
import { asSlug } from '../identifiers.js';
import { requireText } from '../text/normalise.js';
import type { ParadigmProfile } from './profile.js';
import { readBundle } from './profile.js';

/**
 * Syncretism is two grammatical cells wearing the same clothes. Latin
 * `puellae` is genitive singular, dative singular and nominative plural, and a
 * learner who types it has answered all three whichever one was asked.
 *
 * The map is declared rather than inferred, because the two are not the same
 * question. What the profile happens to produce is an accident of the endings.
 * What the language treats as one form is a claim about the grammar, and a
 * teacher may want a drill to insist on the distinction even where the surface
 * forms coincide. `reconcileSyncretism` reports where the two disagree so an
 * editor can decide which one is wrong.
 */
export interface SyncretismGroup {
  readonly key: string;
  readonly members: readonly string[];
  readonly note: string | undefined;
}

export interface SyncretismGroupInput {
  readonly key: string;
  readonly members: readonly string[];
  readonly note?: string;
}

export interface SyncretismMap {
  readonly groups: readonly SyncretismGroup[];
  readonly byBundle: ReadonlyMap<string, string>;
}

export const NO_SYNCRETISM: SyncretismMap = { groups: [], byBundle: new Map() };

export function syncretismMap(
  schema: FeatureSchema,
  inputs: readonly SyncretismGroupInput[],
): SyncretismMap {
  const problems = new ProblemCollector();
  const groups: SyncretismGroup[] = [];
  const byBundle = new Map<string, string>();
  const seenKeys = new Set<string>();

  for (const [index, input] of inputs.entries()) {
    const path = `groups.${index}`;
    const key = asSlug(`${path}.key`, input.key);
    if (seenKeys.has(key)) {
      problems.add(path, `repeats the group key ${key}`);
      continue;
    }
    seenKeys.add(key);

    const members: string[] = [];
    for (const member of input.members) {
      let canonical: string;
      try {
        const bundle = readBundle(schema, member, path);
        if (!isComplete(schema, bundle)) {
          problems.add(path, `member does not fix every required dimension: ${member}`);
          continue;
        }
        canonical = bundleKey(bundle);
      } catch (error) {
        problems.add(path, (error as Error).message);
        continue;
      }
      if (members.includes(canonical)) {
        problems.add(path, `lists ${canonical} twice`);
        continue;
      }
      const owner = byBundle.get(canonical);
      if (owner !== undefined) {
        problems.add(path, `claims ${canonical} which group ${owner} already holds`);
        continue;
      }
      members.push(canonical);
    }

    if (members.length < 2) {
      problems.add(path, 'must hold at least two bundles to be a syncretism');
      continue;
    }
    for (const member of members) {
      byBundle.set(member, key);
    }
    groups.push({
      key,
      members,
      note: input.note === undefined ? undefined : requireText(`${path}.note`, input.note, 240),
    });
  }

  problems.throwIfAny('syncretism map is not usable');
  return { groups, byBundle };
}

export function groupKeyOf(map: SyncretismMap, bundle: string): string | undefined {
  return map.byBundle.get(bundle);
}

export function groupOf(map: SyncretismMap, bundle: string): SyncretismGroup | undefined {
  const key = map.byBundle.get(bundle);
  return key === undefined ? undefined : map.groups.find((group) => group.key === key);
}

/** The other members of the bundle group, never including the bundle itself. */
export function twinsOf(map: SyncretismMap, bundle: string): string[] {
  const group = groupOf(map, bundle);
  return group === undefined ? [] : group.members.filter((member) => member !== bundle);
}

export function areSyncretic(map: SyncretismMap, first: string, second: string): boolean {
  if (first === second) {
    return false;
  }
  const key = map.byBundle.get(first);
  return key !== undefined && key === map.byBundle.get(second);
}

export function syncreticBundles(map: SyncretismMap): string[] {
  return [...map.byBundle.keys()].sort();
}

/**
 * The syncretism the profile actually produces: two slots that would build the
 * same string from the same stem. This is what the drill would see even if
 * nobody declared anything.
 */
export function derivedSyncretism(profile: ParadigmProfile): SyncretismMap {
  const buckets = new Map<string, string[]>();
  for (const slot of profile.slots.values()) {
    const signature =
      slot.literal === undefined
        ? `affix:${slot.stemRole}:${slot.affix}`
        : `literal:${slot.literal}`;
    const existing = buckets.get(signature);
    if (existing === undefined) {
      buckets.set(signature, [slot.bundle]);
    } else {
      existing.push(slot.bundle);
    }
  }

  const groups: SyncretismGroup[] = [];
  const byBundle = new Map<string, string>();
  let index = 0;
  for (const members of buckets.values()) {
    if (members.length < 2) {
      continue;
    }
    index += 1;
    const key = `derived-${index}`;
    groups.push({ key, members, note: undefined });
    for (const member of members) {
      byBundle.set(member, key);
    }
  }
  return { groups, byBundle };
}

export interface SyncretismDisagreement {
  readonly first: string;
  readonly second: string;
  readonly declared: boolean;
  readonly produced: boolean;
}

/**
 * Every pair the declaration and the profile disagree about. A declared pair the
 * profile does not produce means the endings are wrong or the claim is. A
 * produced pair nobody declared means the drill will accept an answer the
 * syllabus wanted kept apart.
 */
export function reconcileSyncretism(
  profile: ParadigmProfile,
  declared: SyncretismMap,
): SyncretismDisagreement[] {
  const produced = derivedSyncretism(profile);
  const bundles = [...profile.slots.keys()];
  const out: SyncretismDisagreement[] = [];

  for (let i = 0; i < bundles.length; i += 1) {
    for (let j = i + 1; j < bundles.length; j += 1) {
      const first = bundles[i]!;
      const second = bundles[j]!;
      const isDeclared = areSyncretic(declared, first, second);
      const isProduced = areSyncretic(produced, first, second);
      if (isDeclared !== isProduced) {
        out.push({ first, second, declared: isDeclared, produced: isProduced });
      }
    }
  }
  return out;
}
