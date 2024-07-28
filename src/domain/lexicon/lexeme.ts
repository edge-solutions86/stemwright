import { ProblemCollector } from '../errors.js';
import type { FeatureBundle } from '../features/bundle.js';
import { EMPTY_BUNDLE, bundleKey, featureBundle } from '../features/bundle.js';
import type { FeatureSchema } from '../features/schema.js';
import type { Id } from '../identifiers.js';
import { asSlug, derivedId } from '../identifiers.js';
import { normaliseInput, requireText } from '../text/normalise.js';
import type { ClassRegistry } from './inflection-class.js';
import { classOf } from './inflection-class.js';
import type { StemSet } from './stem.js';

/**
 * A lexeme is a dictionary word, not a form of one. `oikos` is a lexeme and
 * `oikou` is a cell of it.
 *
 * Inherent features are the ones the word carries rather than chooses. A Latin
 * noun has a gender and does not decline for it, so gender is inherent, and a
 * bundle asking for a gender the lexeme does not have is asking about a cell
 * that does not exist. An adjective is the opposite case: it declines for
 * gender, so gender is not inherent and every gender is a real cell.
 */
export interface Lexeme {
  readonly id: Id<'lexeme'>;
  readonly language: string;
  readonly lemma: string;
  readonly classKey: string;
  readonly stems: StemSet;
  readonly inherent: FeatureBundle;
  readonly glosses: readonly string[];
  readonly homograph: number;
  readonly tags: readonly string[];
  readonly note: string | undefined;
}

export interface LexemeInput {
  readonly lemma: string;
  readonly classKey: string;
  readonly stems: StemSet;
  readonly inherent?: Readonly<Record<string, string>>;
  readonly glosses?: readonly string[];
  readonly homograph?: number;
  readonly tags?: readonly string[];
  readonly note?: string;
}

export interface LexemeContext {
  readonly schema: FeatureSchema;
  readonly classes: ClassRegistry;
}

/**
 * The id is derived rather than allocated, from the language, the lemma and the
 * homograph index. Re importing the same lexicon therefore produces the same
 * ids, which is what makes an import idempotent and a journal replay safe.
 */
export function lexemeIdFor(language: string, lemma: string, homograph: number): Id<'lexeme'> {
  return derivedId('lexeme', language, normaliseInput(lemma).toLowerCase(), String(homograph));
}

export function lexeme(context: LexemeContext, input: LexemeInput): Lexeme {
  const problems = new ProblemCollector();
  const lemma = requireText('lemma', input.lemma, 96);
  const classKey = asSlug('classKey', input.classKey);

  const declared = classOf(context.classes, classKey);
  if (declared === undefined) {
    problems.add('classKey', `is not a class of ${context.classes.language}: ${classKey}`);
  }

  const homograph = input.homograph ?? 1;
  if (!Number.isInteger(homograph) || homograph < 1) {
    problems.add('homograph', `must be a positive whole number: ${String(homograph)}`);
  }

  const inherent =
    problems.absorb('inherent', () => featureBundle(context.schema, input.inherent ?? {})) ??
    EMPTY_BUNDLE;

  const glosses = (input.glosses ?? []).map((gloss, index) =>
    requireText(`glosses.${index}`, gloss, 200),
  );
  const tags = (input.tags ?? []).map((tag, index) => asSlug(`tags.${index}`, tag));
  if (new Set(tags).size !== tags.length) {
    problems.add('tags', 'holds the same tag twice');
  }

  problems.throwIfAny(`lexeme ${lemma} is not usable`);

  return {
    id: lexemeIdFor(context.classes.language, lemma, homograph),
    language: context.classes.language,
    lemma,
    classKey,
    stems: input.stems,
    inherent,
    glosses,
    homograph,
    tags,
    note: input.note === undefined ? undefined : requireText('note', input.note, 500),
  };
}

/** The form printed in a word list: the lemma, with a superscript for a homograph. */
export function citationForm(entry: Lexeme): string {
  return entry.homograph === 1 ? entry.lemma : `${entry.lemma} (${entry.homograph})`;
}

export function primaryGloss(entry: Lexeme): string | undefined {
  return entry.glosses[0];
}

export function hasTag(entry: Lexeme, tag: string): boolean {
  return entry.tags.includes(tag);
}

/**
 * A bundle is answerable for a lexeme when it does not contradict the inherent
 * features. Asking a masculine noun for its feminine genitive is not a hard
 * question, it is a question with no answer.
 */
export function contradictsInherent(entry: Lexeme, bundle: FeatureBundle): boolean {
  return entry.inherent.pairs.some((pair) => {
    const asked = bundle.pairs.find((candidate) => candidate.dimension === pair.dimension);
    return asked !== undefined && asked.value !== pair.value;
  });
}

export function inherentKey(entry: Lexeme): string {
  return bundleKey(entry.inherent);
}

export function sameLemma(a: Lexeme, b: Lexeme): boolean {
  return a.language === b.language && a.lemma.toLowerCase() === b.lemma.toLowerCase();
}

export function compareLexemes(a: Lexeme, b: Lexeme): number {
  const byLemma = a.lemma.localeCompare(b.lemma);
  return byLemma !== 0 ? byLemma : a.homograph - b.homograph;
}
