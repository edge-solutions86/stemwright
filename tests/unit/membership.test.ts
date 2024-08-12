import { describe, expect, it } from 'vitest';
import { deck } from '../../src/domain/drill/deck.js';
import {
  bundlesCovered,
  cellsByLexeme,
  isEmptyMembership,
  membershipSize,
  membershipWarnings,
  resolveMembership,
  skippedFor,
} from '../../src/domain/drill/membership.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { ager, bellum, drillContext, puella, rex } from '../support/drill.js';
import { latinNoun } from '../support/profiles.js';

const d = (value: string) => parseCivilDate(value);
const TERM = d('2024-10-01');
const build = (input: Parameters<typeof deck>[1]) => deck(latinNoun, input);

describe('resolveMembership', () => {
  it('takes every bound lexeme when the deck constrains nothing', () => {
    const membership = resolveMembership(drillContext, build({ key: 'all' }), TERM);
    expect(membership.lexemes.map((entry) => entry.lemma)).toEqual(['ager', 'bellum', 'puella']);
    expect(membershipSize(membership)).toBe(30);
  });

  it('skips a lexeme whose class has no profile bound', () => {
    const membership = resolveMembership(drillContext, build({ key: 'all' }), TERM);
    expect(skippedFor(membership, 'unbound-class').map((entry) => entry.lemma)).toEqual(['rex']);
    expect(membership.lexemes).not.toContain(rex);
  });

  it('narrows by class and takes descendants with it', () => {
    const membership = resolveMembership(
      drillContext,
      build({ key: 'second', scope: { classKeys: ['second-declension'] } }),
      TERM,
    );
    expect(membership.lexemes.map((entry) => entry.lemma)).toEqual(['ager', 'bellum']);
  });

  it('narrows by tag', () => {
    const membership = resolveMembership(
      drillContext,
      build({ key: 'core', scope: { tags: ['core'] } }),
      TERM,
    );
    expect(membership.lexemes.map((entry) => entry.lemma)).toEqual(['bellum', 'puella']);
  });

  it('intersects class and tag rather than uniting them', () => {
    const membership = resolveMembership(
      drillContext,
      build({ key: 'both', scope: { classKeys: ['second-declension'], tags: ['core'] } }),
      TERM,
    );
    expect(membership.lexemes.map((entry) => entry.lemma)).toEqual(['bellum']);
  });

  it('narrows by named lexeme', () => {
    const membership = resolveMembership(
      drillContext,
      build({ key: 'named', scope: { lexemeIds: [puella.id, ager.id] } }),
      TERM,
    );
    expect(membership.lexemes.map((entry) => entry.lemma)).toEqual(['ager', 'puella']);
  });

  it('narrows the cells without dropping the lexeme', () => {
    const membership = resolveMembership(
      drillContext,
      build({ key: 'plural', scope: { cells: 'number=pl' } }),
      TERM,
    );
    expect(membership.lexemes).toHaveLength(3);
    expect(membershipSize(membership)).toBe(15);
    expect(bundlesCovered(membership)).toHaveLength(5);
  });

  it('skips a lexeme whose cells are all outside the selector', () => {
    const membership = resolveMembership(
      drillContext,
      build({ key: 'none', scope: { cells: 'case=nom,case!=nom' } }),
      TERM,
    );
    expect(skippedFor(membership, 'no-cell-in-scope')).toHaveLength(3);
    expect(isEmptyMembership(membership)).toBe(true);
  });

  it('skips everything before the profiles were published', () => {
    const membership = resolveMembership(drillContext, build({ key: 'all' }), d('2024-01-01'));
    expect(skippedFor(membership, 'profile-not-published')).toHaveLength(3);
    expect(skippedFor(membership, 'unbound-class')).toHaveLength(1);
    expect(isEmptyMembership(membership)).toBe(true);
  });

  it('records the day it was resolved for', () => {
    expect(resolveMembership(drillContext, build({ key: 'all' }), TERM).on).toBe('2024-10-01');
  });
});

describe('cellsByLexeme', () => {
  it('groups the cells under their lexeme', () => {
    const membership = resolveMembership(drillContext, build({ key: 'all' }), TERM);
    const grouped = cellsByLexeme(membership);
    expect(grouped.size).toBe(3);
    expect(grouped.get(bellum.id)).toHaveLength(10);
  });

  it('is empty when nothing was selected', () => {
    const membership = resolveMembership(drillContext, build({ key: 'all' }), d('2024-01-01'));
    expect(cellsByLexeme(membership).size).toBe(0);
  });
});

describe('membershipWarnings', () => {
  it('says nothing about lexemes that are merely out of scope', () => {
    const membership = resolveMembership(
      drillContext,
      build({ key: 'named', scope: { lexemeIds: [puella.id] } }),
      TERM,
    );
    expect(membershipWarnings(membership)).toEqual([]);
  });

  it('warns about an unbound class', () => {
    const membership = resolveMembership(drillContext, build({ key: 'all' }), TERM);
    expect(membershipWarnings(membership)).toEqual([
      '1 lexemes have no paradigm bound to their class',
    ]);
  });

  it('warns when the deck selects nothing at all', () => {
    const membership = resolveMembership(
      drillContext,
      build({ key: 'none', scope: { cells: 'case=nom,case!=nom' } }),
      TERM,
    );
    expect(membershipWarnings(membership)).toContain('the deck selects nothing at all');
  });

  it('warns about a profile with no revision in force', () => {
    const membership = resolveMembership(drillContext, build({ key: 'all' }), d('2024-01-01'));
    expect(membershipWarnings(membership)).toEqual([
      '1 lexemes have no paradigm bound to their class',
      '3 lexemes use a profile with no revision in force on 2024-01-01',
      'the deck selects nothing at all',
    ]);
  });
});
