import { describe, expect, it } from 'vitest';
import { deck } from '../../src/domain/drill/deck.js';
import { resolveMembership } from '../../src/domain/drill/membership.js';
import {
  bundleImbalance,
  busiestBundles,
  deckStats,
  deckWarnings,
  describeDeck,
  thinnestBundles,
} from '../../src/domain/report/deck-stats.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { ager, bellum, drillContext, puella, rex } from '../support/drill.js';
import { latinNoun } from '../support/profiles.js';

const DAY = parseCivilDate('2024-10-21');
const lexemes = [puella, bellum, ager, rex];

const statsFor = (input: Parameters<typeof deck>[1]) => {
  const built = deck(latinNoun, input);
  const membership = resolveMembership(drillContext, built, DAY);
  return { stats: deckStats(built, membership, lexemes), membership };
};

describe('deckStats', () => {
  it('counts lexemes, cells and bundles', () => {
    const { stats } = statsFor({ key: 'all' });
    expect(stats.lexemes).toBe(3);
    expect(stats.cells).toBe(30);
    expect(stats.bundles).toBe(10);
    expect(stats.cellsPerLexeme).toBe(10);
  });

  it('counts the lexemes of each class', () => {
    const { stats } = statsFor({ key: 'all' });
    expect(stats.classes.get('first-declension')).toBe(1);
    expect(stats.classes.get('second-declension.neuter')).toBe(1);
  });

  it('counts the cells of each bundle', () => {
    const { stats } = statsFor({ key: 'all' });
    expect(stats.perBundle.get('case=gen;number=sg')).toBe(3);
  });

  it('counts what was skipped for a reason worth reporting', () => {
    const { stats } = statsFor({ key: 'all' });
    expect(stats.skipped).toBe(1);
  });

  it('reports zeros for a deck that selects nothing', () => {
    const { stats } = statsFor({ key: 'none', scope: { cells: 'case=nom,case!=nom' } });
    expect(stats.cells).toBe(0);
    expect(stats.cellsPerLexeme).toBe(0);
    expect(stats.bundles).toBe(0);
  });
});

describe('bundleImbalance', () => {
  it('is one for a perfectly even deck', () => {
    const { stats } = statsFor({ key: 'all' });
    expect(bundleImbalance(stats)).toBeCloseTo(1);
  });

  it('is zero for a deck with no cells', () => {
    const { stats } = statsFor({ key: 'none', scope: { cells: 'case=nom,case!=nom' } });
    expect(bundleImbalance(stats)).toBe(0);
  });

  it('rises when one bundle dominates', () => {
    const { stats } = statsFor({ key: 'all' });
    const skewed = {
      ...stats,
      perBundle: new Map([
        ['a', 90],
        ['b', 5],
        ['c', 5],
      ]),
    };
    expect(bundleImbalance(skewed)).toBeGreaterThan(2);
  });
});

describe('busiest and thinnest', () => {
  const { stats } = statsFor({ key: 'all' });
  const skewed = {
    ...stats,
    perBundle: new Map([
      ['a', 9],
      ['b', 5],
      ['c', 1],
    ]),
  };

  it('ranks by count', () => {
    expect(busiestBundles(skewed, 2)).toEqual([
      ['a', 9],
      ['b', 5],
    ]);
    expect(thinnestBundles(skewed, 2)).toEqual([
      ['c', 1],
      ['b', 5],
    ]);
  });

  it('honours the limit', () => {
    expect(busiestBundles(skewed, 1)).toHaveLength(1);
  });
});

describe('deckWarnings', () => {
  it('warns about a deck with no cells and stops there', () => {
    const { stats, membership } = statsFor({ key: 'none', scope: { cells: 'case=nom,case!=nom' } });
    expect(deckWarnings(stats, membership)).toEqual(['the deck holds no cells at all']);
  });

  it('warns about a small deck', () => {
    const { stats, membership } = statsFor({ key: 'all' });
    expect(deckWarnings(stats, membership)).toContain('the deck holds only 3 lexemes');
  });

  it('warns about a lexeme with no paradigm bound', () => {
    const { stats, membership } = statsFor({ key: 'all' });
    expect(deckWarnings(stats, membership)).toContain(
      '1 lexemes were skipped for having no paradigm bound',
    );
  });

  it('warns about an imbalanced deck', () => {
    const { stats, membership } = statsFor({ key: 'all' });
    const skewed = {
      ...stats,
      lexemes: 40,
      perBundle: new Map([
        ['case=nom;number=sg', 90],
        ['case=gen;number=sg', 5],
        ['case=dat;number=sg', 5],
      ]),
    };
    const warnings = deckWarnings(skewed, membership);
    expect(warnings.some((line) => line.includes('above the average'))).toBe(true);
  });
});

describe('describeDeck', () => {
  it('reads the counts out', () => {
    const { stats } = statsFor({ key: 'all' });
    expect(describeDeck(stats)).toBe('all holds 30 cells across 3 lexemes and 10 bundles');
  });
});
