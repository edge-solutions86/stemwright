import { describe, expect, it } from 'vitest';
import {
  NO_SYNCRETISM,
  areSyncretic,
  derivedSyncretism,
  groupKeyOf,
  groupOf,
  reconcileSyncretism,
  syncreticBundles,
  syncretismMap,
  twinsOf,
} from '../../src/domain/paradigm/syncretism.js';
import { problemMessages } from '../support/problems.js';
import { firstDeclension, latinNoun, secondNeuter } from '../support/profiles.js';

const declaredFirst = syncretismMap(latinNoun, [
  {
    key: 'ae',
    members: ['gen.sg', 'dat.sg', 'nom.pl'],
    note: 'puellae answers three cells at once',
  },
  { key: 'is', members: ['dat.pl', 'abl.pl'] },
  { key: 'a', members: ['nom.sg', 'abl.sg'] },
]);

describe('syncretismMap', () => {
  it('canonicalises every member', () => {
    expect(groupOf(declaredFirst, 'case=gen;number=sg')?.key).toBe('ae');
  });

  it('keeps the note it was given', () => {
    expect(groupOf(declaredFirst, 'case=nom;number=pl')?.note).toBe(
      'puellae answers three cells at once',
    );
  });

  it('refuses a group with fewer than two members', () => {
    expect(
      problemMessages(() => syncretismMap(latinNoun, [{ key: 'lonely', members: ['gen.sg'] }])),
    ).toEqual(['groups.0 must hold at least two bundles to be a syncretism']);
  });

  it('refuses a repeated group key', () => {
    expect(
      problemMessages(() =>
        syncretismMap(latinNoun, [
          { key: 'a', members: ['gen.sg', 'dat.sg'] },
          { key: 'a', members: ['nom.pl', 'acc.pl'] },
        ]),
      ),
    ).toEqual(['groups.1 repeats the group key a']);
  });

  it('refuses a bundle claimed by two groups', () => {
    const messages = problemMessages(() =>
      syncretismMap(latinNoun, [
        { key: 'first', members: ['gen.sg', 'dat.sg'] },
        { key: 'second', members: ['dat.sg', 'nom.pl'] },
      ]),
    );
    expect(messages).toEqual([
      'groups.1 claims case=dat;number=sg which group first already holds',
      'groups.1 must hold at least two bundles to be a syncretism',
    ]);
  });

  it('refuses the same bundle listed twice in one group', () => {
    const messages = problemMessages(() =>
      syncretismMap(latinNoun, [{ key: 'a', members: ['gen.sg', 'case=gen;number=sg'] }]),
    );
    expect(messages[0]).toBe('groups.0 lists case=gen;number=sg twice');
  });

  it('refuses a member that leaves a dimension open', () => {
    expect(
      problemMessages(() => syncretismMap(latinNoun, [{ key: 'a', members: ['gen', 'dat.sg'] }])),
    ).toEqual([
      'groups.0 member does not fix every required dimension: gen',
      'groups.0 must hold at least two bundles to be a syncretism',
    ]);
  });
});

describe('twinsOf', () => {
  it('names the other members and never the bundle itself', () => {
    expect(twinsOf(declaredFirst, 'case=gen;number=sg')).toEqual([
      'case=dat;number=sg',
      'case=nom;number=pl',
    ]);
  });

  it('is empty for a bundle in no group', () => {
    expect(twinsOf(declaredFirst, 'case=acc;number=sg')).toEqual([]);
    expect(twinsOf(NO_SYNCRETISM, 'case=gen;number=sg')).toEqual([]);
  });
});

describe('areSyncretic', () => {
  it('is true for two members of one group', () => {
    expect(areSyncretic(declaredFirst, 'case=dat;number=pl', 'case=abl;number=pl')).toBe(true);
  });

  it('is false across groups and for a bundle with itself', () => {
    expect(areSyncretic(declaredFirst, 'case=dat;number=pl', 'case=gen;number=sg')).toBe(false);
    expect(areSyncretic(declaredFirst, 'case=gen;number=sg', 'case=gen;number=sg')).toBe(false);
  });

  it('reads the group key back', () => {
    expect(groupKeyOf(declaredFirst, 'case=abl;number=pl')).toBe('is');
    expect(groupKeyOf(declaredFirst, 'case=acc;number=pl')).toBeUndefined();
  });

  it('lists every bundle in some group', () => {
    expect(syncreticBundles(declaredFirst)).toHaveLength(7);
  });
});

describe('derivedSyncretism', () => {
  it('groups slots that build the same string', () => {
    const derived = derivedSyncretism(firstDeclension);
    expect(areSyncretic(derived, 'case=gen;number=sg', 'case=dat;number=sg')).toBe(true);
    expect(areSyncretic(derived, 'case=dat;number=pl', 'case=abl;number=pl')).toBe(true);
  });

  it('does not group slots that differ', () => {
    const derived = derivedSyncretism(firstDeclension);
    expect(areSyncretic(derived, 'case=acc;number=sg', 'case=acc;number=pl')).toBe(false);
  });

  it('finds the neuter nominative and accusative rule falling out of the endings', () => {
    const derived = derivedSyncretism(secondNeuter);
    expect(areSyncretic(derived, 'case=nom;number=sg', 'case=acc;number=sg')).toBe(true);
    expect(areSyncretic(derived, 'case=nom;number=pl', 'case=acc;number=pl')).toBe(true);
  });
});

describe('reconcileSyncretism', () => {
  it('reports nothing when the declaration matches what the endings produce', () => {
    expect(reconcileSyncretism(firstDeclension, declaredFirst)).toEqual([]);
  });

  it('reports a pair the profile produces that nobody declared', () => {
    const disagreements = reconcileSyncretism(secondNeuter, NO_SYNCRETISM);
    expect(disagreements).toHaveLength(4);
    expect(disagreements.every((entry) => entry.produced && !entry.declared)).toBe(true);
  });

  it('reports a pair declared that the profile keeps apart', () => {
    const overclaimed = syncretismMap(latinNoun, [{ key: 'wrong', members: ['nom.sg', 'gen.sg'] }]);
    const disagreements = reconcileSyncretism(secondNeuter, overclaimed);
    const claim = disagreements.find(
      (entry) => entry.first === 'case=nom;number=sg' && entry.second === 'case=gen;number=sg',
    );
    expect(claim).toEqual({
      first: 'case=nom;number=sg',
      second: 'case=gen;number=sg',
      declared: true,
      produced: false,
    });
  });
});
