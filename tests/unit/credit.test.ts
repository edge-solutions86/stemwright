import { describe, expect, it } from 'vitest';
import {
  DEFAULT_CREDIT,
  NO_CREDIT,
  climbedCount,
  creditFor,
  creditRule,
  describeCredit,
  movedCount,
  propagateCredit,
} from '../../src/domain/mastery/credit.js';
import { ladder } from '../../src/domain/mastery/ladder.js';
import { buildLedger, strengthFor } from '../../src/domain/mastery/ledger.js';
import { newStrength, recordCorrect, suspend } from '../../src/domain/mastery/strength.js';
import { cell } from '../../src/domain/paradigm/cell.js';
import { ValidationError } from '../../src/domain/errors.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';

const d = (value: string) => parseCivilDate(value);
const DAY = d('2025-03-03');
const flat = ladder({ key: 'flat', rungs: [2, 6, 14, 30], spread: 0 });

const known = (cellId: string, times: number) => {
  let strength = newStrength(cellId);
  for (let step = 0; step < times; step += 1) {
    strength = recordCorrect(strength, flat, d('2025-01-01'));
  }
  return strength;
};

describe('creditRule', () => {
  it('takes the default when nothing is patched', () => {
    expect(creditRule()).toEqual(DEFAULT_CREDIT);
  });

  it('patches only what it is given', () => {
    expect(creditRule({ share: 0.25 })).toEqual({ ...DEFAULT_CREDIT, share: 0.25 });
  });

  it('refuses a share outside nought to one', () => {
    expect(() => creditRule({ share: 1.5 })).toThrow(ValidationError);
    expect(() => creditRule({ share: -0.1 })).toThrow(/between nought and one/);
  });
});

describe('creditFor', () => {
  it('moves a known twin out by a share of the interval', () => {
    const twin = known('cell_b', 2);
    const { strength, outcome } = creditFor(twin, flat, creditRule({ share: 0.5 }), DAY);
    expect(outcome.moved).toBe(true);
    expect(outcome.climbed).toBe(true);
    expect(strength.rung).toBe(2);
    expect(strength.dueOn).toBe('2025-03-10');
  });

  it('does not climb when the rule forbids it', () => {
    const twin = known('cell_b', 2);
    const rule = creditRule({ share: 0.5, allowClimb: false });
    const { strength, outcome } = creditFor(twin, flat, rule, DAY);
    expect(outcome.climbed).toBe(false);
    expect(strength.rung).toBe(twin.rung);
  });

  it('leaves a new twin alone by default', () => {
    const { strength, outcome } = creditFor(newStrength('cell_b'), flat, creditRule(), DAY);
    expect(outcome.moved).toBe(false);
    expect(strength.dueOn).toBeUndefined();
  });

  it('introduces a new twin when the rule says to', () => {
    const rule = creditRule({ introduces: true, share: 0.5 });
    const { strength, outcome } = creditFor(newStrength('cell_b'), flat, rule, DAY);
    expect(outcome.moved).toBe(true);
    expect(strength.dueOn).toBe('2025-03-04');
    expect(strength.rung).toBe(0);
  });

  it('leaves a suspended twin alone whatever the rule', () => {
    const twin = suspend(known('cell_b', 2));
    expect(creditFor(twin, flat, creditRule({ share: 1 }), DAY).outcome.moved).toBe(false);
  });

  it('does nothing at all when the share is nought', () => {
    const twin = known('cell_b', 2);
    expect(creditFor(twin, flat, NO_CREDIT, DAY).outcome.moved).toBe(false);
  });

  it('never pulls a due date forward', () => {
    const far = { ...known('cell_b', 1), dueOn: d('2025-12-01') };
    const rule = creditRule({ share: 0.5, allowClimb: false });
    const { strength, outcome } = creditFor(far, flat, rule, DAY);
    expect(strength.dueOn).toBe('2025-12-01');
    expect(outcome.moved).toBe(false);
  });

  it('still climbs a twin whose due date is further out', () => {
    const far = { ...known('cell_b', 1), dueOn: d('2025-12-01') };
    const { strength, outcome } = creditFor(far, flat, creditRule({ share: 0.5 }), DAY);
    expect(strength.rung).toBe(far.rung + 1);
    expect(strength.dueOn).toBe('2025-12-01');
    expect(outcome.climbed).toBe(true);
  });

  it('never schedules a twin sooner than tomorrow', () => {
    const twin = known('cell_b', 1);
    const rule = creditRule({ share: 0.01, allowClimb: false });
    expect(creditFor(twin, flat, rule, DAY).strength.dueOn).toBe('2025-03-04');
  });
});

describe('propagateCredit', () => {
  const ledger = buildLedger('lrn_one', [known('cell_b', 2), known('cell_c', 1)]);

  it('moves every twin it is handed', () => {
    const report = propagateCredit(ledger, flat, creditRule(), ['cell_b', 'cell_c'], DAY);
    expect(movedCount(report)).toBe(2);
    expect(climbedCount(report)).toBe(2);
    expect(strengthFor(report.ledger, 'cell_b').rung).toBe(2);
  });

  it('leaves the original ledger alone', () => {
    propagateCredit(ledger, flat, creditRule(), ['cell_b'], DAY);
    expect(strengthFor(ledger, 'cell_b').rung).toBe(1);
  });

  it('reports an outcome for every twin, moved or not', () => {
    const report = propagateCredit(ledger, flat, creditRule(), ['cell_b', 'cell_new'], DAY);
    expect(report.outcomes).toHaveLength(2);
    expect(movedCount(report)).toBe(1);
  });

  it('changes nothing for an empty twin list', () => {
    const report = propagateCredit(ledger, flat, creditRule(), [], DAY);
    expect(report.ledger).toEqual(ledger);
    expect(report.outcomes).toEqual([]);
  });

  it('changes nothing under the no credit rule', () => {
    const report = propagateCredit(ledger, flat, NO_CREDIT, ['cell_b', 'cell_c'], DAY);
    expect(movedCount(report)).toBe(0);
  });
});

describe('describeCredit', () => {
  const answered = cell('lex_aaaaaa', 'case=gen;number=sg', 'first-declension');

  it('says so when no credit passes', () => {
    expect(describeCredit(NO_CREDIT, answered, 0)).toBe('no credit passes from case=gen;number=sg');
  });

  it('names the share and the count', () => {
    expect(describeCredit(creditRule({ share: 0.5 }), answered, 2)).toBe(
      '2 twins of case=gen;number=sg took 50 percent of the interval',
    );
  });
});
