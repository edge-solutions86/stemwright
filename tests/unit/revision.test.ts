import { describe, expect, it } from 'vitest';
import {
  firstRevision,
  latestRevision,
  requireRevisionInForce,
  retiredOn,
  revisionById,
  revisionByOrdinal,
  revisionHistory,
  revisionIdFor,
  revisionInForce,
  revisionsAcross,
  spansARevisionChange,
  withRevision,
} from '../../src/domain/paradigm/revision.js';
import { paradigmProfile, withSlot } from '../../src/domain/paradigm/profile.js';
import { syncretismMap } from '../../src/domain/paradigm/syncretism.js';
import { NotFoundError } from '../../src/domain/errors.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { dayRange } from '../../src/domain/time/window.js';
import { problemMessages } from '../support/problems.js';
import { firstDeclension, latinNoun } from '../support/profiles.js';

const d = (value: string) => parseCivilDate(value);
const revised = withSlot(latinNoun, firstDeclension, { bundle: 'gen.sg', affix: 'ai' });

const history = revisionHistory('first-declension', [
  { effectiveFrom: d('2024-09-01'), profile: firstDeclension, reason: 'first published' },
  {
    effectiveFrom: d('2025-01-15'),
    profile: revised,
    syncretism: syncretismMap(latinNoun, [{ key: 'is', members: ['dat.pl', 'abl.pl'] }]),
    reason: 'the archaic genitive was added for the epic reading list',
  },
]);

describe('revisionHistory', () => {
  it('numbers revisions from one in date order', () => {
    expect(history.revisions.map((revision) => revision.ordinal)).toEqual([1, 2]);
  });

  it('sorts revisions given out of order', () => {
    const reversed = revisionHistory('first-declension', [
      { effectiveFrom: d('2025-01-15'), profile: revised },
      { effectiveFrom: d('2024-09-01'), profile: firstDeclension },
    ]);
    expect(reversed.revisions[0]?.effectiveFrom).toBe('2024-09-01');
  });

  it('derives a stable id from the profile key and the ordinal', () => {
    expect(history.revisions[0]?.id).toBe(revisionIdFor('first-declension', 1));
  });

  it('defaults the syncretism map to nothing declared', () => {
    expect(history.revisions[0]?.syncretism.groups).toEqual([]);
  });

  it('refuses an empty history', () => {
    expect(problemMessages(() => revisionHistory('x', []))).toEqual([
      'revisions must hold at least one revision',
    ]);
  });

  it('refuses two revisions starting on the same day', () => {
    expect(
      problemMessages(() =>
        revisionHistory('first-declension', [
          { effectiveFrom: d('2024-09-01'), profile: firstDeclension },
          { effectiveFrom: d('2024-09-01'), profile: revised },
        ]),
      ),
    ).toEqual(['revisions.1 starts on 2024-09-01 and so does the one before it']);
  });

  it('refuses a revision carrying another profile', () => {
    const other = paradigmProfile(latinNoun, {
      key: 'second-neuter',
      classKey: 'x',
      slots: [{ bundle: 'nom.sg', affix: 'um' }],
    });
    expect(
      problemMessages(() =>
        revisionHistory('first-declension', [{ effectiveFrom: d('2024-09-01'), profile: other }]),
      ),
    ).toEqual(['revisions.0 carries profile second-neuter in the history of first-declension']);
  });
});

describe('revisionInForce', () => {
  it('gives nothing before the first revision starts', () => {
    expect(revisionInForce(history, d('2024-08-31'))).toBeUndefined();
    expect(() => requireRevisionInForce(history, d('2024-08-31'))).toThrow(NotFoundError);
  });

  it('takes effect on the start day itself', () => {
    expect(revisionInForce(history, d('2024-09-01'))?.ordinal).toBe(1);
  });

  it('stays in force until the day before the next one', () => {
    expect(revisionInForce(history, d('2025-01-14'))?.ordinal).toBe(1);
    expect(revisionInForce(history, d('2025-01-15'))?.ordinal).toBe(2);
  });

  it('keeps the newest one in force indefinitely', () => {
    expect(revisionInForce(history, d('2030-06-01'))?.ordinal).toBe(2);
  });

  it('resolves the syncretism map of the day, not the newest one', () => {
    expect(revisionInForce(history, d('2024-12-01'))?.syncretism.groups).toHaveLength(0);
    expect(revisionInForce(history, d('2025-02-01'))?.syncretism.groups).toHaveLength(1);
  });
});

describe('lookups', () => {
  it('finds the first and the latest', () => {
    expect(firstRevision(history).ordinal).toBe(1);
    expect(latestRevision(history).ordinal).toBe(2);
  });

  it('finds by ordinal and by id', () => {
    expect(revisionByOrdinal(history, 2)?.effectiveFrom).toBe('2025-01-15');
    expect(revisionByOrdinal(history, 3)).toBeUndefined();
    expect(revisionById(history, revisionIdFor('first-declension', 1))?.ordinal).toBe(1);
    expect(revisionById(history, 'rev_zzzzzz')).toBeUndefined();
  });

  it('reports when a revision was retired', () => {
    expect(retiredOn(history, 1)).toBe('2025-01-15');
    expect(retiredOn(history, 2)).toBeUndefined();
  });
});

describe('revisionsAcross', () => {
  it('names one revision for a range inside it', () => {
    const range = dayRange(d('2024-10-01'), d('2024-10-31'));
    expect(revisionsAcross(history, range).map((r) => r.ordinal)).toEqual([1]);
    expect(spansARevisionChange(history, range)).toBe(false);
  });

  it('names both when the range crosses the change', () => {
    const range = dayRange(d('2025-01-10'), d('2025-01-20'));
    expect(revisionsAcross(history, range).map((r) => r.ordinal)).toEqual([1, 2]);
    expect(spansARevisionChange(history, range)).toBe(true);
  });

  it('names nothing for a range before publication', () => {
    const range = dayRange(d('2024-01-01'), d('2024-08-31'));
    expect(revisionsAcross(history, range)).toEqual([]);
    expect(spansARevisionChange(history, range)).toBe(false);
  });
});

describe('withRevision', () => {
  it('appends and renumbers', () => {
    const grown = withRevision(history, {
      effectiveFrom: d('2025-09-01'),
      profile: firstDeclension,
      reason: 'reverted the archaic genitive',
    });
    expect(grown.revisions).toHaveLength(3);
    expect(latestRevision(grown).ordinal).toBe(3);
  });

  it('leaves the original history alone', () => {
    withRevision(history, { effectiveFrom: d('2025-09-01'), profile: firstDeclension });
    expect(history.revisions).toHaveLength(2);
  });

  it('inserts in the middle and renumbers what follows', () => {
    const grown = withRevision(history, {
      effectiveFrom: d('2024-11-01'),
      profile: firstDeclension,
    });
    expect(grown.revisions.map((r) => r.effectiveFrom)).toEqual([
      '2024-09-01',
      '2024-11-01',
      '2025-01-15',
    ]);
    expect(revisionInForce(grown, d('2024-12-01'))?.ordinal).toBe(2);
  });

  it('refuses a start day that is already taken', () => {
    expect(
      problemMessages(() =>
        withRevision(history, { effectiveFrom: d('2025-01-15'), profile: revised }),
      ),
    ).toHaveLength(1);
  });
});
