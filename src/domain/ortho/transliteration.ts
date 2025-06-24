import { ProblemCollector, ValidationError } from '../errors.js';
import { asSlug } from '../identifiers.js';
import { markClusters, normaliseInput, stripMarks } from '../text/normalise.js';

/**
 * Typing a language you do not have a keyboard for.
 *
 * A learner on a laptop with no Greek layout types `anthrwpos` and means
 * `ανθρωπος`, and refusing that is refusing most of the people the desk is for.
 * So a language may declare a transliteration table, and an answer is tried
 * both as typed and as transliterated.
 *
 * The table is longest match first, which is the only thing that makes a
 * multi letter mapping work: `th` has to be tried before `t`, and a table
 * applied in declaration order would turn `theos` into something with a stray
 * `h` in it.
 */
export interface TransliterationRule {
  readonly from: string;
  readonly to: string;
}

export interface TransliterationTable {
  readonly language: string;
  readonly rules: readonly TransliterationRule[];
}

export function transliterationTable(
  language: string,
  rules: readonly TransliterationRule[],
): TransliterationTable {
  const problems = new ProblemCollector();
  const seen = new Set<string>();

  for (const [index, rule] of rules.entries()) {
    const from = normaliseInput(rule.from).toLowerCase();
    if (from === '') {
      problems.add(`rules.${index}.from`, 'must not be blank');
      continue;
    }
    if (seen.has(from)) {
      problems.add(`rules.${index}.from`, `is mapped twice: ${from}`);
    }
    seen.add(from);
  }

  problems.throwIfAny(`transliteration for ${language} is not usable`);

  const sorted = [...rules]
    .map((rule) => ({
      from: normaliseInput(rule.from).toLowerCase(),
      to: normaliseInput(rule.to),
    }))
    .sort((a, b) => b.from.length - a.from.length || a.from.localeCompare(b.from));

  return { language: asSlug('language', language), rules: sorted };
}

export const NO_TRANSLITERATION: TransliterationTable = { language: 'none', rules: [] };

/** The Greek table a first year course needs, in the beta code most editions use. */
export const GREEK_BETA_CODE = transliterationTable('greek', [
  { from: 'th', to: 'θ' },
  { from: 'ph', to: 'φ' },
  { from: 'ch', to: 'χ' },
  { from: 'ps', to: 'ψ' },
  { from: 'ks', to: 'ξ' },
  { from: 'ou', to: 'ου' },
  { from: 'a', to: 'α' },
  { from: 'b', to: 'β' },
  { from: 'g', to: 'γ' },
  { from: 'd', to: 'δ' },
  { from: 'e', to: 'ε' },
  { from: 'z', to: 'ζ' },
  { from: 'h', to: 'η' },
  { from: 'i', to: 'ι' },
  { from: 'k', to: 'κ' },
  { from: 'l', to: 'λ' },
  { from: 'm', to: 'μ' },
  { from: 'n', to: 'ν' },
  { from: 'o', to: 'ο' },
  { from: 'p', to: 'π' },
  { from: 'r', to: 'ρ' },
  { from: 's', to: 'σ' },
  { from: 't', to: 'τ' },
  { from: 'u', to: 'υ' },
  { from: 'w', to: 'ω' },
  { from: 'x', to: 'χ' },
]);

export function transliterate(table: TransliterationTable, text: string): string {
  const source = normaliseInput(text).toLowerCase();
  let out = '';
  let index = 0;

  while (index < source.length) {
    const rule = table.rules.find((candidate) => source.startsWith(candidate.from, index));
    if (rule === undefined) {
      out += source[index]!;
      index += 1;
      continue;
    }
    out += rule.to;
    index += rule.from.length;
  }
  return out;
}

/** True when the typed text becomes the expected form once transliterated. */
export function matchesTransliterated(
  table: TransliterationTable,
  expected: string,
  given: string,
): boolean {
  if (table.rules.length === 0) {
    return false;
  }
  const target = stripMarks(normaliseInput(expected)).toLowerCase();
  return transliterate(table, given) === target;
}

export function isTransliterable(table: TransliterationTable, text: string): boolean {
  const source = normaliseInput(text).toLowerCase();
  return (
    source !== '' &&
    markClusters(source).every((cluster) =>
      table.rules.some((rule) => rule.from === cluster.toLowerCase()),
    )
  );
}

export function requireRule(table: TransliterationTable, from: string): TransliterationRule {
  const rule = table.rules.find((candidate) => candidate.from === from);
  if (rule === undefined) {
    throw ValidationError.field('from', `is not in the ${table.language} table: ${from}`);
  }
  return rule;
}
