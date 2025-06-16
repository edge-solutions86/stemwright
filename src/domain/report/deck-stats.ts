import { groupBy } from '../collections.js';
import type { Deck } from '../drill/deck.js';
import type { Membership } from '../drill/membership.js';
import { skippedFor } from '../drill/membership.js';
import type { Lexeme } from '../lexicon/lexeme.js';

/**
 * What a deck actually contains, for the editor who wrote it rather than the
 * learner who drills it.
 *
 * The number that matters is the balance across bundles. A deck of four hundred
 * nouns whose cells are ninety percent nominative teaches the nominative, and
 * the deck definition that produced it usually looks fine: a selector that
 * narrows by class picks up whatever cells those classes happen to define, and
 * an unbalanced class tree quietly becomes an unbalanced deck.
 */
export interface DeckStats {
  readonly deckKey: string;
  readonly lexemes: number;
  readonly cells: number;
  readonly bundles: number;
  readonly cellsPerLexeme: number;
  readonly skipped: number;
  readonly classes: ReadonlyMap<string, number>;
  readonly perBundle: ReadonlyMap<string, number>;
}

export function deckStats(
  entry: Deck,
  membership: Membership,
  lexemes: readonly Lexeme[],
): DeckStats {
  const byLexeme = new Map(lexemes.map((row) => [row.id as string, row]));
  const classes = new Map<string, number>();

  for (const row of membership.lexemes) {
    const known = byLexeme.get(row.id) ?? row;
    classes.set(known.classKey, (classes.get(known.classKey) ?? 0) + 1);
  }

  const perBundle = new Map(
    [...groupBy(membership.cells, (cell) => cell.bundle)].map(([bundle, cells]) => [
      bundle,
      cells.length,
    ]),
  );

  return {
    deckKey: entry.key,
    lexemes: membership.lexemes.length,
    cells: membership.cells.length,
    bundles: perBundle.size,
    cellsPerLexeme:
      membership.lexemes.length === 0 ? 0 : membership.cells.length / membership.lexemes.length,
    skipped: membership.skipped.filter((row) => row.reason !== 'out-of-scope').length,
    classes,
    perBundle,
  };
}

/**
 * How far the busiest bundle is above the mean, as a multiple. One means the
 * deck is perfectly even and anything above two is worth a look.
 */
export function bundleImbalance(stats: DeckStats): number {
  if (stats.perBundle.size === 0) {
    return 0;
  }
  const counts = [...stats.perBundle.values()];
  const mean = counts.reduce((sum, count) => sum + count, 0) / counts.length;
  return mean === 0 ? 0 : Math.max(...counts) / mean;
}

export function busiestBundles(stats: DeckStats, limit = 5): [string, number][] {
  return [...stats.perBundle.entries()]
    .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
    .slice(0, limit);
}

export function thinnestBundles(stats: DeckStats, limit = 5): [string, number][] {
  return [...stats.perBundle.entries()]
    .sort((a, b) => a[1] - b[1] || a[0].localeCompare(b[0]))
    .slice(0, limit);
}

export function deckWarnings(stats: DeckStats, membership: Membership): string[] {
  const warnings: string[] = [];

  if (stats.cells === 0) {
    warnings.push('the deck holds no cells at all');
    return warnings;
  }
  if (stats.lexemes < 5) {
    warnings.push(`the deck holds only ${stats.lexemes} lexemes`);
  }
  if (bundleImbalance(stats) > 2) {
    const busiest = busiestBundles(stats, 1)[0]!;
    warnings.push(`${busiest[0]} holds ${busiest[1]} cells, well above the average`);
  }
  const unbound = skippedFor(membership, 'unbound-class').length;
  if (unbound > 0) {
    warnings.push(`${unbound} lexemes were skipped for having no paradigm bound`);
  }
  return warnings;
}

export function describeDeck(stats: DeckStats): string {
  return `${stats.deckKey} holds ${stats.cells} cells across ${stats.lexemes} lexemes and ${stats.bundles} bundles`;
}
