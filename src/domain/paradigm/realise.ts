import { ProblemCollector, ValidationError } from '../errors.js';
import type { FeatureBundle } from '../features/bundle.js';
import { bundleKey } from '../features/bundle.js';
import type { FeatureSchema } from '../features/schema.js';
import { asSlug } from '../identifiers.js';
import type { Lexeme } from '../lexicon/lexeme.js';
import { resolveStem } from '../lexicon/stem.js';
import { normaliseInput, requireText } from '../text/normalise.js';
import type { ParadigmProfile, ParadigmSlot } from './profile.js';
import { requireSlot } from './profile.js';

/**
 * Joining a stem to an ending is not concatenation. Latin `reg` plus `si` is
 * `rexi`, `ag` plus `tus` is `actus`, and a Greek stem in a stop plus a sigma
 * behaves differently again. The rules that do this are small, ordered and
 * language specific, so they live in data rather than in code.
 *
 * A rule fires on the seam only: the tail of the stem and the head of the
 * affix. That keeps every rule local and makes the trace readable, which
 * matters because the trace is what an instructor is shown when a learner asks
 * why the expected answer is what it is.
 */
export interface JoinRule {
  readonly key: string;
  readonly stemEnd: string;
  readonly affixStart: string;
  readonly becomes: string;
  readonly note: string | undefined;
}

export interface JoinRuleInput {
  readonly key: string;
  readonly stemEnd: string;
  readonly affixStart: string;
  readonly becomes: string;
  readonly note?: string;
}

export interface RuleSet {
  readonly language: string;
  readonly rules: readonly JoinRule[];
}

export function ruleSet(language: string, inputs: readonly JoinRuleInput[]): RuleSet {
  const problems = new ProblemCollector();
  const rules: JoinRule[] = [];
  const seen = new Set<string>();

  for (const [index, input] of inputs.entries()) {
    const key = asSlug(`rules.${index}.key`, input.key);
    if (seen.has(key)) {
      problems.add(`rules.${index}`, `repeats the rule key ${key}`);
      continue;
    }
    seen.add(key);
    const stemEnd = normaliseInput(input.stemEnd);
    const affixStart = normaliseInput(input.affixStart);
    if (stemEnd === '' && affixStart === '') {
      problems.add(`rules.${index}`, 'must constrain at least one side of the seam');
      continue;
    }
    rules.push({
      key,
      stemEnd,
      affixStart,
      becomes: normaliseInput(input.becomes),
      note:
        input.note === undefined ? undefined : requireText(`rules.${index}.note`, input.note, 240),
    });
  }

  problems.throwIfAny(`join rules for ${language} are not usable`);
  return { language: asSlug('language', language), rules };
}

export const NO_RULES: RuleSet = { language: 'none', rules: [] };

export interface JoinStep {
  readonly rule: string;
  readonly before: string;
  readonly after: string;
}

export interface JoinResult {
  readonly form: string;
  readonly steps: readonly JoinStep[];
}

/**
 * Applies the first rule that matches the seam, then stops. Rules do not cascade
 * on purpose: a rule that rewrote the seam and then let a second rule fire on
 * the result would make the order of the list into hidden control flow, and the
 * order would then be the thing an editor has to reason about rather than the
 * rules themselves.
 */
export function joinAtSeam(rules: RuleSet, stem: string, affix: string): JoinResult {
  const plain = `${stem}${affix}`;
  for (const rule of rules.rules) {
    if (!stem.endsWith(rule.stemEnd) || !affix.startsWith(rule.affixStart)) {
      continue;
    }
    const head = stem.slice(0, stem.length - rule.stemEnd.length);
    const tail = affix.slice(rule.affixStart.length);
    const form = `${head}${rule.becomes}${tail}`;
    return { form, steps: [{ rule: rule.key, before: plain, after: form }] };
  }
  return { form: plain, steps: [] };
}

export interface RealisedCell {
  readonly bundle: string;
  readonly form: string;
  readonly stemRole: string;
  readonly stem: string;
  readonly affix: string;
  readonly fromLiteral: boolean;
  readonly steps: readonly JoinStep[];
}

export function realiseSlot(rules: RuleSet, entry: Lexeme, slot: ParadigmSlot): RealisedCell {
  if (slot.literal !== undefined) {
    return {
      bundle: slot.bundle,
      form: slot.literal,
      stemRole: slot.stemRole,
      stem: '',
      affix: '',
      fromLiteral: true,
      steps: [],
    };
  }
  const stem = resolveStem(entry.stems, slot.stemRole);
  const joined = joinAtSeam(rules, stem.form, slot.affix);
  return {
    bundle: slot.bundle,
    form: joined.form,
    stemRole: stem.role,
    stem: stem.form,
    affix: slot.affix,
    fromLiteral: false,
    steps: joined.steps,
  };
}

export function realise(
  rules: RuleSet,
  entry: Lexeme,
  profile: ParadigmProfile,
  bundle: FeatureBundle,
): RealisedCell {
  return realiseSlot(rules, entry, requireSlot(profile, bundle));
}

/** Every cell of the profile realised for one lexeme, in slot order. */
export function realiseAll(
  rules: RuleSet,
  entry: Lexeme,
  profile: ParadigmProfile,
): RealisedCell[] {
  return [...profile.slots.values()].map((slot) => realiseSlot(rules, entry, slot));
}

export function formOf(
  rules: RuleSet,
  entry: Lexeme,
  profile: ParadigmProfile,
  bundle: FeatureBundle,
): string {
  return realise(rules, entry, profile, bundle).form;
}

/**
 * The bundles whose realised forms collide for this lexeme. This is syncretism
 * as the learner meets it, after the join rules have run, which can differ from
 * both the declaration and what the raw endings suggest.
 */
export function collidingForms(
  rules: RuleSet,
  entry: Lexeme,
  profile: ParadigmProfile,
): ReadonlyMap<string, string[]> {
  const byForm = new Map<string, string[]>();
  for (const realised of realiseAll(rules, entry, profile)) {
    const existing = byForm.get(realised.form);
    if (existing === undefined) {
      byForm.set(realised.form, [realised.bundle]);
    } else {
      existing.push(realised.bundle);
    }
  }
  for (const [form, bundles] of [...byForm.entries()]) {
    if (bundles.length < 2) {
      byForm.delete(form);
    }
  }
  return byForm;
}

export function explainRealisation(
  schema: FeatureSchema,
  realised: RealisedCell,
  bundle: FeatureBundle,
): string {
  if (bundleKey(bundle) !== realised.bundle) {
    throw ValidationError.field('bundle', 'does not match the realised cell');
  }
  if (realised.fromLiteral) {
    return `${realised.form} is stated outright by the profile`;
  }
  const seam = `${realised.stem} plus ${realised.affix === '' ? 'no ending' : realised.affix}`;
  if (realised.steps.length === 0) {
    return `${seam} gives ${realised.form} in ${schema.language}`;
  }
  const applied = realised.steps.map((step) => step.rule).join(', ');
  return `${seam} gives ${realised.form} in ${schema.language} after ${applied}`;
}
