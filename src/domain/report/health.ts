import { countBy } from '../collections.js';
import type { Deck } from '../drill/deck.js';
import type { DrillContext, Membership } from '../drill/membership.js';
import { classOf } from '../lexicon/inflection-class.js';
import { allLexemes } from '../lexicon/lexicon.js';
import { bindingFor, historyFor, shadowedBindings, unboundClasses } from '../paradigm/book.js';
import { revisionInForce } from '../paradigm/revision.js';
import type { CivilDate } from '../time/civil.js';
import { bundleImbalance, deckStats } from './deck-stats.js';

/**
 * The editorial state of one language, read on a day.
 *
 * Everything here is a question the drill layer answers silently. A class with
 * no paradigm bound produces a learner whose queue is empty; a profile whose
 * first revision starts next Monday produces the same empty queue for a
 * different reason; a deck that selects nothing produces it for a third. From
 * the learner's side all three look identical, so this exists to tell an editor
 * which one they have before a class sits down to a blank screen.
 *
 * It takes the day because every one of those answers depends on it, and it
 * takes the memberships already resolved rather than resolving them itself, so
 * a caller cannot check one day and report another.
 */
export type FindingLevel = 'error' | 'warning' | 'note';

export interface Finding {
  readonly level: FindingLevel;
  readonly area: string;
  readonly message: string;
}

export interface HealthInput {
  readonly context: DrillContext;
  readonly decks: readonly { readonly deck: Deck; readonly membership: Membership }[];
  readonly on: CivilDate;
}

const LEVEL_ORDER: Record<FindingLevel, number> = { error: 0, warning: 1, note: 2 };

function checkClasses(input: HealthInput): Finding[] {
  const { book, classes } = input.context;
  const findings: Finding[] = [];

  for (const classKey of unboundClasses(book, classes)) {
    findings.push({
      level: 'error',
      area: 'classes',
      message: `${classKey} has no paradigm bound to it or to anything above it`,
    });
  }
  for (const classKey of shadowedBindings(book, classes)) {
    findings.push({
      level: 'warning',
      area: 'classes',
      message: `the binding on ${classKey} is shadowed and can never be reached`,
    });
  }
  return findings;
}

function checkProfiles(input: HealthInput): Finding[] {
  const { book } = input.context;
  const findings: Finding[] = [];

  for (const [profileKey, history] of book.histories) {
    if (history.revisions.length === 0) {
      findings.push({
        level: 'error',
        area: 'profiles',
        message: `${profileKey} holds no revisions at all`,
      });
      continue;
    }
    if (revisionInForce(history, input.on) === undefined) {
      const first = history.revisions[0]!;
      findings.push({
        level: 'warning',
        area: 'profiles',
        message: `${profileKey} has nothing in force on ${input.on}, the first starts ${first.effectiveFrom}`,
      });
    }
  }
  return findings;
}

/**
 * A lexeme whose class is not in the registry is a stronger failure than one
 * whose class is simply unbound: the first cannot be graded under any profile,
 * and the second is waiting on an editorial decision that has a place to go.
 */
function checkLexemes(input: HealthInput): Finding[] {
  const { book, classes, lexicon } = input.context;
  const entries = allLexemes(lexicon);
  const findings: Finding[] = [];

  const missing = entries.filter((entry) => classOf(classes, entry.classKey) === undefined);
  for (const [classKey, count] of countBy(missing, (entry) => entry.classKey)) {
    findings.push({
      level: 'error',
      area: 'lexicon',
      message: `${count} lexemes name the class ${classKey}, which the registry does not hold`,
    });
  }

  const unbound = entries.filter(
    (entry) =>
      classOf(classes, entry.classKey) !== undefined &&
      bindingFor(book, classes, entry.classKey) === undefined,
  );
  if (unbound.length > 0) {
    findings.push({
      level: 'warning',
      area: 'lexicon',
      message: `${unbound.length} lexemes cannot be drilled because their class has no paradigm`,
    });
  }

  const orphaned = entries.filter((entry) => {
    const binding = bindingFor(book, classes, entry.classKey);
    return binding !== undefined && historyFor(book, binding.profileKey) === undefined;
  });
  if (orphaned.length > 0) {
    findings.push({
      level: 'error',
      area: 'lexicon',
      message: `${orphaned.length} lexemes are bound to a profile the book has lost`,
    });
  }

  if (entries.length === 0) {
    findings.push({ level: 'note', area: 'lexicon', message: 'the lexicon is empty' });
  }
  return findings;
}

function checkDecks(input: HealthInput): Finding[] {
  const findings: Finding[] = [];

  for (const { deck, membership } of input.decks) {
    const stats = deckStats(deck, membership, []);
    if (stats.cells === 0) {
      findings.push({
        level: 'error',
        area: 'decks',
        message: `${deck.key} selects nothing on ${input.on}`,
      });
      continue;
    }
    const imbalance = bundleImbalance(stats);
    if (imbalance > 2) {
      findings.push({
        level: 'warning',
        area: 'decks',
        message: `${deck.key} is uneven, its busiest bundle holds ${imbalance.toFixed(1)} times the average`,
      });
    }
    if (stats.lexemes < 5) {
      findings.push({
        level: 'note',
        area: 'decks',
        message: `${deck.key} holds only ${stats.lexemes} lexemes`,
      });
    }
  }

  if (input.decks.length === 0) {
    findings.push({ level: 'note', area: 'decks', message: 'no decks are defined' });
  }
  return findings;
}

/**
 * Every check, worst first, and within a level in the order the areas were
 * written. Ordering by level rather than by area is deliberate: an operator
 * reading a long report reads the top of it, and what they need at the top is
 * whatever stops a class drilling today.
 */
export function checkHealth(input: HealthInput): Finding[] {
  const findings = [
    ...checkClasses(input),
    ...checkProfiles(input),
    ...checkLexemes(input),
    ...checkDecks(input),
  ];
  return findings
    .map((finding, index) => ({ finding, index }))
    .sort(
      (a, b) => LEVEL_ORDER[a.finding.level] - LEVEL_ORDER[b.finding.level] || a.index - b.index,
    )
    .map((row) => row.finding);
}

export function countLevels(findings: readonly Finding[]): Record<FindingLevel, number> {
  const counted = countBy(findings, (finding) => finding.level);
  return {
    error: counted.get('error') ?? 0,
    warning: counted.get('warning') ?? 0,
    note: counted.get('note') ?? 0,
  };
}

/** True when nothing found would stop a class drilling today. */
export function isHealthy(findings: readonly Finding[]): boolean {
  return countLevels(findings).error === 0;
}

export function describeHealth(findings: readonly Finding[], language: string): string {
  const counts = countLevels(findings);
  if (findings.length === 0) {
    return `${language} has nothing to report`;
  }
  return `${language} has ${counts.error} errors, ${counts.warning} warnings and ${counts.note} notes`;
}
