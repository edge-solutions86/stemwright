import { describe, expect, it } from 'vitest';
import {
  NO_SHIELDS,
  applyShields,
  describeShields,
  firstOpenDay,
  isShielded,
  shieldCovering,
  shieldSet,
  shieldedDaysIn,
  streakThrough,
} from '../../src/domain/mastery/shield.js';
import { ladder } from '../../src/domain/mastery/ladder.js';
import { buildLedger, strengthFor } from '../../src/domain/mastery/ledger.js';
import { newStrength, recordCorrect, suspend } from '../../src/domain/mastery/strength.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { dayRange } from '../../src/domain/time/window.js';

const d = (value: string) => parseCivilDate(value);
const r = (from: string, to: string) => dayRange(d(from), d(to));
const flat = ladder({ key: 'flat', rungs: [1, 3, 7], spread: 0 });

const shields = shieldSet([
  { window: r('2025-03-10', '2025-03-16'), reason: 'reading week' },
  { window: r('2025-04-02', '2025-04-04'), reason: 'field trip to the epigraphy museum' },
]);

describe('shieldSet', () => {
  it('keeps the windows it was given', () => {
    expect(shields.shields).toHaveLength(2);
    expect(shields.windows).toHaveLength(2);
  });

  it('defaults a missing reason', () => {
    const bare = shieldSet([{ window: r('2025-03-10', '2025-03-11') }]);
    expect(bare.shields[0]?.reason).toBe('no reason recorded');
  });

  it('merges windows that touch', () => {
    const merged = shieldSet([
      { window: r('2025-03-10', '2025-03-12') },
      { window: r('2025-03-13', '2025-03-15') },
    ]);
    expect(merged.shields).toHaveLength(2);
    expect(merged.windows).toHaveLength(1);
  });

  it('builds an empty set', () => {
    expect(shieldSet([]).windows).toEqual([]);
  });
});

describe('isShielded', () => {
  it('covers both endpoints', () => {
    expect(isShielded(shields, d('2025-03-10'))).toBe(true);
    expect(isShielded(shields, d('2025-03-16'))).toBe(true);
  });

  it('leaves the days either side open', () => {
    expect(isShielded(shields, d('2025-03-09'))).toBe(false);
    expect(isShielded(shields, d('2025-03-17'))).toBe(false);
  });

  it('shields nothing when the set is empty', () => {
    expect(isShielded(NO_SHIELDS, d('2025-03-12'))).toBe(false);
  });

  it('names the shield covering a day', () => {
    expect(shieldCovering(shields, d('2025-04-03'))?.reason).toBe(
      'field trip to the epigraphy museum',
    );
    expect(shieldCovering(shields, d('2025-05-01'))).toBeUndefined();
  });
});

describe('firstOpenDay', () => {
  it('leaves an open day where it is', () => {
    expect(firstOpenDay(shields, d('2025-03-09'))).toBe('2025-03-09');
  });

  it('steps past the end of a shield', () => {
    expect(firstOpenDay(shields, d('2025-03-12'))).toBe('2025-03-17');
  });

  it('steps past two shields that abut after normalising', () => {
    const back = shieldSet([
      { window: r('2025-03-10', '2025-03-12') },
      { window: r('2025-03-13', '2025-03-15') },
    ]);
    expect(firstOpenDay(back, d('2025-03-11'))).toBe('2025-03-16');
  });
});

describe('shieldedDaysIn', () => {
  it('counts the days a shield covers inside a range', () => {
    expect(shieldedDaysIn(shields, r('2025-03-01', '2025-03-31'))).toBe(7);
  });

  it('clips a shield that overhangs the range', () => {
    expect(shieldedDaysIn(shields, r('2025-03-14', '2025-03-20'))).toBe(3);
  });

  it('counts nothing when the range misses every shield', () => {
    expect(shieldedDaysIn(shields, r('2025-05-01', '2025-05-31'))).toBe(0);
  });

  it('counts both shields when the range spans them', () => {
    expect(shieldedDaysIn(shields, r('2025-01-01', '2025-12-31'))).toBe(10);
  });
});

describe('applyShields', () => {
  const inside = recordCorrect(newStrength('cell_a'), flat, d('2025-03-11'));
  const before = recordCorrect(newStrength('cell_b'), flat, d('2025-03-01'));
  const ledger = buildLedger('lrn_one', [inside, before]);
  const cells = ['cell_a', 'cell_b', 'cell_c'];

  it('pushes a review due inside a shield to the far side', () => {
    const applied = applyShields(ledger, cells, shields);
    expect(strengthFor(applied.ledger, 'cell_a').dueOn).toBe('2025-03-17');
    expect(applied.outcomes).toEqual([{ cellId: 'cell_a', from: '2025-03-12', to: '2025-03-17' }]);
  });

  it('leaves a review due before the shield alone', () => {
    const applied = applyShields(ledger, cells, shields);
    expect(strengthFor(applied.ledger, 'cell_b').dueOn).toBe('2025-03-02');
  });

  it('leaves a suspended cell alone', () => {
    const suspended = buildLedger('lrn_one', [suspend(inside)]);
    expect(applyShields(suspended, cells, shields).outcomes).toEqual([]);
  });

  it('leaves a cell nobody has answered alone', () => {
    expect(applyShields(buildLedger('lrn_one', []), cells, shields).outcomes).toEqual([]);
  });

  it('changes nothing when there are no shields', () => {
    const applied = applyShields(ledger, cells, NO_SHIELDS);
    expect(applied.ledger).toEqual(ledger);
  });
});

describe('streakThrough', () => {
  const answered = new Set(['2025-03-08', '2025-03-09', '2025-03-17', '2025-03-18', '2025-03-19']);

  it('counts a plain run of answered days', () => {
    expect(streakThrough(new Set(['2025-03-19', '2025-03-18']), NO_SHIELDS, d('2025-03-19'))).toBe(
      2,
    );
  });

  it('steps over a shielded stretch', () => {
    expect(streakThrough(answered, shields, d('2025-03-19'))).toBe(5);
  });

  it('breaks on an ordinary missed day', () => {
    expect(streakThrough(answered, NO_SHIELDS, d('2025-03-19'))).toBe(3);
  });

  it('is nothing when the last day was missed', () => {
    expect(streakThrough(answered, shields, d('2025-03-20'))).toBe(0);
  });

  it('stops at the limit rather than walking forever', () => {
    const always = { has: () => true } as unknown as ReadonlySet<string>;
    expect(streakThrough(always, NO_SHIELDS, d('2025-03-19'), 5)).toBe(5);
  });
});

describe('describeShields', () => {
  it('says so when there are none', () => {
    expect(describeShields(NO_SHIELDS)).toBe('no shielded days');
  });

  it('reads each window and its reason out', () => {
    expect(describeShields(shields)).toBe(
      '2025-03-10 to 2025-03-16 for reading week, 2025-04-02 to 2025-04-04 for field trip to the epigraphy museum',
    );
  });
});
