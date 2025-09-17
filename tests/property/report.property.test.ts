import { describe, expect, it } from 'vitest';
import { enrol } from '../../src/domain/course/enrolment.js';
import {
  clampToCeiling,
  learnerPreferences,
  noPreferences,
  resolvePolicyFor,
} from '../../src/domain/course/preferences.js';
import { deck } from '../../src/domain/drill/deck.js';
import { resolveMembership } from '../../src/domain/drill/membership.js';
import { applyPolicyPatch, drillPolicy } from '../../src/domain/drill/policy.js';
import { STANDARD_LADDER, topRung } from '../../src/domain/mastery/ladder.js';
import { buildLedger, ledgerCounts } from '../../src/domain/mastery/ledger.js';
import {
  newStrength,
  recordCorrect,
  recordLapse,
  suspend,
} from '../../src/domain/mastery/strength.js';
import type { CellStrength } from '../../src/domain/mastery/strength.js';
import {
  coverageByBundle,
  coverageByLexeme,
  coverageTotal,
  maturityOf,
  touchedShare,
  weakestRows,
} from '../../src/domain/report/coverage.js';
import { forecast, totalDue } from '../../src/domain/report/forecast.js';
import { addDays, parseCivilDate } from '../../src/domain/time/civil.js';
import { dayRange } from '../../src/domain/time/window.js';
import type { Random } from '../support/random.js';
import { forEachDraw } from '../support/random.js';
import { michaelmas } from '../support/course.js';
import { drillContext } from '../support/drill.js';
import { latinNoun } from '../support/profiles.js';

const DAY = parseCivilDate('2024-10-21');
const membership = resolveMembership(drillContext, deck(latinNoun, { key: 'all' }), DAY);
const cells = membership.cells;

function ledgerFor(random: Random) {
  const strengths: CellStrength[] = [];
  for (const cell of cells) {
    if (random.next() < 0.3) {
      continue;
    }
    let strength = newStrength(cell.id);
    for (let step = 0; step < random.int(1, 9); step += 1) {
      strength = random.bool(0.65)
        ? recordCorrect(strength, STANDARD_LADDER, DAY)
        : recordLapse(strength, STANDARD_LADDER, DAY);
    }
    strengths.push(random.bool(0.1) ? suspend(strength) : strength);
  }
  return buildLedger('lrn_one', strengths);
}

describe('coverage properties', () => {
  it('counts every cell exactly once down either axis', () => {
    forEachDraw(30, 1223, (random) => {
      const ledger = ledgerFor(random);
      for (const rows of [
        coverageByBundle(cells, ledger, STANDARD_LADDER, DAY),
        coverageByLexeme(cells, ledger, STANDARD_LADDER, DAY),
      ]) {
        expect(rows.reduce((sum, row) => sum + row.total, 0)).toBe(cells.length);
      }
    });
  });

  it('splits every row into the four states with nothing left over', () => {
    forEachDraw(30, 2333, (random) => {
      const ledger = ledgerFor(random);
      for (const row of coverageByBundle(cells, ledger, STANDARD_LADDER, DAY)) {
        expect(row.untouched + row.learning + row.mature + row.suspended).toBe(row.total);
      }
    });
  });

  it('never counts more due than there are cells in a row', () => {
    forEachDraw(30, 3449, (random) => {
      const ledger = ledgerFor(random);
      for (const row of coverageByBundle(cells, ledger, STANDARD_LADDER, DAY)) {
        expect(row.due).toBeLessThanOrEqual(row.total);
      }
    });
  });

  it('keeps every share between nought and one', () => {
    forEachDraw(30, 4547, (random) => {
      const ledger = ledgerFor(random);
      for (const row of coverageByLexeme(cells, ledger, STANDARD_LADDER, DAY)) {
        expect(maturityOf(row)).toBeGreaterThanOrEqual(0);
        expect(maturityOf(row)).toBeLessThanOrEqual(1);
        expect(touchedShare(row)).toBeGreaterThanOrEqual(0);
        expect(touchedShare(row)).toBeLessThanOrEqual(1);
      }
    });
  });

  it('totals to the same counts the ledger reports', () => {
    forEachDraw(30, 5651, (random) => {
      const ledger = ledgerFor(random);
      const total = coverageTotal(coverageByBundle(cells, ledger, STANDARD_LADDER, DAY));
      const counts = ledgerCounts(
        ledger,
        cells.map((cell) => cell.id),
        STANDARD_LADDER,
        DAY,
      );
      expect(total.total).toBe(counts.known);
      expect(total.mature).toBe(counts.mature);
      expect(total.suspended).toBe(counts.suspended);
      expect(total.untouched).toBe(counts.untouched);
    });
  });

  it('ranks the weakest rows no higher in maturity than the rest', () => {
    forEachDraw(30, 6761, (random) => {
      const rows = coverageByBundle(cells, ledgerFor(random), STANDARD_LADDER, DAY);
      const weakest = weakestRows(rows, 3);
      const worst = Math.max(...weakest.map(maturityOf));
      for (const row of rows.filter((candidate) => !weakest.includes(candidate))) {
        expect(maturityOf(row)).toBeGreaterThanOrEqual(worst - 1e-9);
      }
    });
  });

  it('never reports a cell at a rung above the top of the ladder', () => {
    forEachDraw(30, 7873, (random) => {
      for (const strength of ledgerFor(random).byCell.values()) {
        expect(strength.rung).toBeLessThanOrEqual(topRung(STANDARD_LADDER));
      }
    });
  });
});

describe('forecast properties', () => {
  it('never forecasts more reviews than there are scheduled cells', () => {
    forEachDraw(30, 8887, (random) => {
      const ledger = ledgerFor(random);
      const range = dayRange(DAY, addDays(DAY, random.int(1, 60)));
      const scheduled = [...ledger.byCell.values()].filter(
        (strength) => !strength.suspended && strength.dueOn !== undefined,
      ).length;
      const days = forecast(cells, ledger, range, drillPolicy());
      expect(totalDue(days)).toBeLessThanOrEqual(scheduled);
    });
  });

  it('covers every day of the range it was given', () => {
    forEachDraw(30, 9901, (random) => {
      const length = random.int(1, 40);
      const range = dayRange(DAY, addDays(DAY, length - 1));
      expect(forecast(cells, ledgerFor(random), range, drillPolicy())).toHaveLength(length);
    });
  });
});

describe('preference properties', () => {
  const enrolment = enrol(michaelmas, { learnerId: 'lrn_one' });

  it('never lets a learner raise a budget above the layer above', () => {
    forEachDraw(60, 1013, (random) => {
      const base = drillPolicy({
        newPerDay: random.int(1, 20),
        reviewsPerDay: random.int(1, 90),
      });
      const preferences = learnerPreferences({
        learnerId: 'lrn_one',
        patch: { newPerDay: random.int(0, 60), reviewsPerDay: random.int(1, 200) },
      });
      const resolved = resolvePolicyFor(base, {}, enrolment, preferences);
      expect(resolved.policy.newPerDay).toBeLessThanOrEqual(base.newPerDay);
      expect(resolved.policy.reviewsPerDay).toBeLessThanOrEqual(base.reviewsPerDay);
    });
  });

  it('changes nothing when the learner asks for nothing', () => {
    forEachDraw(30, 2027, (random) => {
      const base = drillPolicy({ newPerDay: random.int(1, 20) });
      const resolved = resolvePolicyFor(base, {}, enrolment, noPreferences('lrn_one'));
      expect(resolved.policy).toEqual(applyPolicyPatch(base, {}));
      expect(resolved.refused).toEqual([]);
    });
  });

  it('refuses exactly the fields it dropped', () => {
    forEachDraw(60, 3037, (random) => {
      const base = drillPolicy({ newPerDay: random.int(1, 10) });
      const asked = { newPerDay: random.int(0, 30) };
      const clamped = clampToCeiling(base, asked);
      expect(clamped.refused.includes('newPerDay')).toBe(asked.newPerDay > base.newPerDay);
    });
  });
});
