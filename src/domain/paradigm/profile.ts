import { ProblemCollector, ValidationError } from '../errors.js';
import type { FeatureBundle } from '../features/bundle.js';
import { bundleKey, enumerateBundles, isComplete, parseBundleKey } from '../features/bundle.js';
import { parseBundleNotation } from '../features/notation.js';
import type { FeatureSchema } from '../features/schema.js';
import { asSlug } from '../identifiers.js';
import { normaliseInput, requireText } from '../text/normalise.js';
import { DEFAULT_STEM_ROLE } from '../lexicon/stem.js';

/**
 * A paradigm profile says how every cell of one inflection class is built. One
 * slot per bundle: which stem to attach to, what to attach, and whether the
 * form is irregular enough that the profile just states it outright.
 *
 * A profile is not a table of answers. It is a table of recipes, and the answer
 * only exists once a lexeme is put through it. That is what lets one profile
 * serve six hundred nouns and what makes a profile edit a change to all six
 * hundred at once, which is why profiles are revised rather than mutated.
 */
export interface ParadigmSlot {
  readonly bundle: string;
  readonly stemRole: string;
  readonly affix: string;
  /** A full form that replaces stem plus affix, for a cell that is irregular. */
  readonly literal: string | undefined;
  readonly note: string | undefined;
}

export interface ParadigmSlotInput {
  /** Either the dotted notation or a canonical bundle key. */
  readonly bundle: string;
  readonly stemRole?: string;
  readonly affix?: string;
  readonly literal?: string;
  readonly note?: string;
}

export interface ParadigmProfile {
  readonly key: string;
  readonly language: string;
  readonly classKey: string;
  readonly label: string;
  readonly slots: ReadonlyMap<string, ParadigmSlot>;
}

export interface ParadigmProfileInput {
  readonly key: string;
  readonly classKey: string;
  readonly label?: string;
  readonly slots: readonly ParadigmSlotInput[];
}

/**
 * Accepts both spellings of a bundle so a hand written profile can say `gen.sg`
 * and a machine written one can say `case=gen;number=sg`.
 */
export function readBundle(schema: FeatureSchema, text: string, path: string): FeatureBundle {
  const cleaned = normaliseInput(text);
  return cleaned.includes('=')
    ? parseBundleKey(schema, cleaned)
    : parseBundleNotation(schema, cleaned, path);
}

export function paradigmProfile(
  schema: FeatureSchema,
  input: ParadigmProfileInput,
): ParadigmProfile {
  const problems = new ProblemCollector();
  const key = asSlug('profile.key', input.key);
  const classKey = asSlug('profile.classKey', input.classKey);
  const slots = new Map<string, ParadigmSlot>();

  if (input.slots.length === 0) {
    problems.add('slots', 'must hold at least one slot');
  }

  for (const [index, slotInput] of input.slots.entries()) {
    const path = `slots.${index}`;
    let bundle: FeatureBundle;
    try {
      bundle = readBundle(schema, slotInput.bundle, path);
    } catch (error) {
      problems.add(path, (error as Error).message);
      continue;
    }

    const canonical = bundleKey(bundle);
    if (!isComplete(schema, bundle)) {
      problems.add(path, `does not fix every required dimension: ${slotInput.bundle}`);
      continue;
    }
    if (slots.has(canonical)) {
      problems.add(path, `repeats the bundle ${canonical}`);
      continue;
    }
    if (slotInput.affix === undefined && slotInput.literal === undefined) {
      problems.add(path, 'must give either an affix or a literal form');
      continue;
    }

    slots.set(canonical, {
      bundle: canonical,
      stemRole: asSlug(`${path}.stemRole`, slotInput.stemRole ?? DEFAULT_STEM_ROLE),
      affix: normaliseInput(slotInput.affix ?? ''),
      literal:
        slotInput.literal === undefined
          ? undefined
          : requireText(`${path}.literal`, slotInput.literal, 128),
      note:
        slotInput.note === undefined ? undefined : requireText(`${path}.note`, slotInput.note, 240),
    });
  }

  problems.throwIfAny(`paradigm profile ${key} is not usable`);

  return {
    key,
    language: schema.language,
    classKey,
    label: input.label === undefined ? key : requireText('profile.label', input.label, 96),
    slots,
  };
}

export function slotFor(profile: ParadigmProfile, bundle: FeatureBundle): ParadigmSlot | undefined {
  return profile.slots.get(bundleKey(bundle));
}

export function requireSlot(profile: ParadigmProfile, bundle: FeatureBundle): ParadigmSlot {
  const slot = slotFor(profile, bundle);
  if (slot === undefined) {
    throw ValidationError.field(
      'bundle',
      `has no slot in profile ${profile.key}: ${bundleKey(bundle)}`,
    );
  }
  return slot;
}

export function slotCount(profile: ParadigmProfile): number {
  return profile.slots.size;
}

export function slotBundles(profile: ParadigmProfile): string[] {
  return [...profile.slots.keys()];
}

export function stemRolesUsed(profile: ParadigmProfile): string[] {
  return [...new Set([...profile.slots.values()].map((slot) => slot.stemRole))].sort();
}

export function literalSlots(profile: ParadigmProfile): ParadigmSlot[] {
  return [...profile.slots.values()].filter((slot) => slot.literal !== undefined);
}

/** The bundles the schema can express that the profile says nothing about. */
export function holesIn(schema: FeatureSchema, profile: ParadigmProfile): string[] {
  return enumerateBundles(schema)
    .map(bundleKey)
    .filter((candidate) => !profile.slots.has(candidate));
}

export function isTotal(schema: FeatureSchema, profile: ParadigmProfile): boolean {
  return holesIn(schema, profile).length === 0;
}

export function withSlot(
  schema: FeatureSchema,
  profile: ParadigmProfile,
  slot: ParadigmSlotInput,
): ParadigmProfile {
  const kept = [...profile.slots.values()].filter(
    (existing) => existing.bundle !== bundleKey(readBundle(schema, slot.bundle, 'slot')),
  );
  return paradigmProfile(schema, {
    key: profile.key,
    classKey: profile.classKey,
    label: profile.label,
    slots: [...kept, slot],
  });
}
