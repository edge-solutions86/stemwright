import { describe, expect, it } from 'vitest';
import {
  DEFAULT_LEECH,
  applyLeechPolicy,
  describeLeech,
  isLeech,
  lapseCount,
  leechPolicy,
  leechesIn,
  sweepLeeches,
} from '../../src/domain/mastery/leech.js';
import { ladder } from '../../src/domain/mastery/ladder.js';
import { buildLedger, strengthFor } from '../../src/domain/mastery/ledger.js';
import {
  newStrength,
  recordCorrect,
  recordLapse,
  suspend,
} from '../../src/domain/mastery/strength.js';
import { ValidationError } from '../../src/domain/errors.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';

const d = (value: string) => parseCivilDate(value);
const DAY = d('2025-03-03');
const flat = ladder({ key: 'flat', rungs: [2, 6, 14], spread: 0 });

const lapsedTimes = (cellId: string, times: number) => {
  let strength = newStrength(cellId);
  for (let step = 0; step < times; step += 1) {
    strength = recordLapse(strength, flat, d('2025-01-01'));
  }
  return strength;
};

describe('leechPolicy', () => {
  it('takes the default when nothing is patched', () => {
    expect(leechPolicy()).toEqual(DEFAULT_LEECH);
  });

  it('refuses a threshold below one', () => {
    expect(() => leechPolicy({ threshold: 0 })).toThrow(ValidationError);
    expect(() => leechPolicy({ threshold: 2.5 })).toThrow(/whole number above zero/);
  });

  it('refuses a negative demotion rest and accepts zero', () => {
    expect(() => leechPolicy({ demotionRest: -1 })).toThrow(/must not be negative/);
    expect(leechPolicy({ demotionRest: 0 }).demotionRest).toBe(0);
  });
});

describe('lapseCount', () => {
  const strength = lapsedTimes('cell_a', 4);

  it('counts every lapse by default', () => {
    expect(lapseCount(strength, leechPolicy())).toBe(4);
  });

  it('counts none once the last answer was right', () => {
    const recovered = recordCorrect(strength, flat, DAY);
    expect(lapseCount(recovered, leechPolicy({ consecutiveOnly: true }))).toBe(0);
    expect(lapseCount(recovered, leechPolicy())).toBe(4);
  });

  it('counts them again when the streak breaks', () => {
    const relapsed = recordLapse(recordCorrect(strength, flat, DAY), flat, DAY);
    expect(lapseCount(relapsed, leechPolicy({ consecutiveOnly: true }))).toBe(5);
  });
});

describe('isLeech', () => {
  it('fires at the threshold and not before', () => {
    const policy = leechPolicy({ threshold: 3 });
    expect(isLeech(lapsedTimes('cell_a', 2), policy)).toBe(false);
    expect(isLeech(lapsedTimes('cell_a', 3), policy)).toBe(true);
  });

  it('never fires on a cell that is already suspended', () => {
    expect(isLeech(suspend(lapsedTimes('cell_a', 9)), leechPolicy({ threshold: 3 }))).toBe(false);
  });

  it('never fires on a cell nobody has answered', () => {
    expect(isLeech(newStrength('cell_a'), leechPolicy({ threshold: 1 }))).toBe(false);
  });
});

describe('applyLeechPolicy', () => {
  const leech = lapsedTimes('cell_a', 4);

  it('changes nothing when the cell is not a leech', () => {
    const quiet = lapsedTimes('cell_a', 1);
    const applied = applyLeechPolicy(quiet, leechPolicy({ threshold: 3 }), flat, DAY);
    expect(applied.strength).toBe(quiet);
    expect(applied.outcome.fired).toBe(false);
    expect(applied.outcome.action).toBeUndefined();
  });

  it('suspends under the suspend action', () => {
    const applied = applyLeechPolicy(leech, leechPolicy({ threshold: 3 }), flat, DAY);
    expect(applied.strength.suspended).toBe(true);
    expect(applied.outcome).toEqual({
      cellId: 'cell_a',
      fired: true,
      action: 'suspend',
      lapses: 4,
    });
  });

  it('sends the cell to the bottom rung under the demote action', () => {
    const climbed = recordCorrect(recordCorrect(leech, flat, DAY), flat, DAY);
    const policy = leechPolicy({ threshold: 3, action: 'demote', demotionRest: 5 });
    const applied = applyLeechPolicy(climbed, policy, flat, DAY);
    expect(applied.strength.rung).toBe(0);
    expect(applied.strength.suspended).toBe(false);
    expect(applied.strength.dueOn).toBe('2025-03-08');
  });

  it('waits at least the bottom interval when the rest is shorter', () => {
    const policy = leechPolicy({ threshold: 3, action: 'demote', demotionRest: 0 });
    expect(applyLeechPolicy(leech, policy, flat, DAY).strength.dueOn).toBe('2025-03-05');
  });

  it('changes nothing but the report under the tag action', () => {
    const policy = leechPolicy({ threshold: 3, action: 'tag' });
    const applied = applyLeechPolicy(leech, policy, flat, DAY);
    expect(applied.strength).toBe(leech);
    expect(applied.outcome.fired).toBe(true);
    expect(applied.outcome.action).toBe('tag');
  });
});

describe('leechesIn', () => {
  const ledger = buildLedger('lrn_one', [
    lapsedTimes('cell_a', 5),
    lapsedTimes('cell_b', 3),
    lapsedTimes('cell_c', 1),
  ]);
  const cells = ['cell_a', 'cell_b', 'cell_c', 'cell_d'];

  it('names the cells over the threshold, worst first', () => {
    const found = leechesIn(ledger, cells, leechPolicy({ threshold: 3 }));
    expect(found.map((entry) => entry.cellId)).toEqual(['cell_a', 'cell_b']);
  });

  it('names none when the threshold is above them all', () => {
    expect(leechesIn(ledger, cells, leechPolicy({ threshold: 9 }))).toEqual([]);
  });

  it('only looks at the cells it was asked about', () => {
    expect(leechesIn(ledger, ['cell_b'], leechPolicy({ threshold: 3 }))).toHaveLength(1);
  });
});

describe('sweepLeeches', () => {
  const ledger = buildLedger('lrn_one', [lapsedTimes('cell_a', 5), lapsedTimes('cell_b', 3)]);
  const cells = ['cell_a', 'cell_b', 'cell_c'];

  it('acts on every leech and reports each one', () => {
    const swept = sweepLeeches(ledger, cells, leechPolicy({ threshold: 3 }), flat, DAY);
    expect(swept.outcomes).toHaveLength(2);
    expect(strengthFor(swept.ledger, 'cell_a').suspended).toBe(true);
    expect(strengthFor(swept.ledger, 'cell_b').suspended).toBe(true);
  });

  it('leaves the original ledger alone', () => {
    sweepLeeches(ledger, cells, leechPolicy({ threshold: 3 }), flat, DAY);
    expect(strengthFor(ledger, 'cell_a').suspended).toBe(false);
  });

  it('changes nothing under the tag action', () => {
    const policy = leechPolicy({ threshold: 3, action: 'tag' });
    const swept = sweepLeeches(ledger, cells, policy, flat, DAY);
    expect(swept.ledger).toBe(ledger);
    expect(swept.outcomes).toHaveLength(2);
  });

  it('reports nothing when there are no leeches', () => {
    const swept = sweepLeeches(ledger, cells, leechPolicy({ threshold: 9 }), flat, DAY);
    expect(swept.outcomes).toEqual([]);
  });
});

describe('describeLeech', () => {
  it('reads each action out', () => {
    expect(describeLeech(leechPolicy({ threshold: 4 }))).toBe('suspend a cell after 4 lapses');
    expect(describeLeech(leechPolicy({ threshold: 4, action: 'demote' }))).toBe(
      'send a cell back to the bottom rung after 4 lapses',
    );
    expect(describeLeech(leechPolicy({ threshold: 4, action: 'tag' }))).toBe(
      'flag a cell for review after 4 lapses and change nothing',
    );
  });

  it('says when it is counting consecutive lapses', () => {
    expect(describeLeech(leechPolicy({ threshold: 2, consecutiveOnly: true }))).toBe(
      'suspend a cell after 2 consecutive lapses',
    );
  });
});
