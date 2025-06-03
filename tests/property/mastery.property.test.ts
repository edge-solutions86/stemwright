import { describe, expect, it } from 'vitest';
import {
  STANDARD_LADDER,
  clampRung,
  climb,
  intervalAt,
  lapse,
  spreadInterval,
  topRung,
} from '../../src/domain/mastery/ladder.js';
import {
  accuracy,
  daysOverdue,
  deferTo,
  isDue,
  isNew,
  newStrength,
  recordCorrect,
  recordLapse,
  recordNearMiss,
  suspend,
} from '../../src/domain/mastery/strength.js';
import type { CellStrength } from '../../src/domain/mastery/strength.js';
import { addDays, compareCivil, parseCivilDate } from '../../src/domain/time/civil.js';
import type { CivilDate } from '../../src/domain/time/civil.js';
import type { Random } from '../support/random.js';
import { forEachDraw } from '../support/random.js';

const START = parseCivilDate('2025-01-06');

type Step = 'correct' | 'lapse' | 'near';

function walk(random: Random, steps: number): { strength: CellStrength; day: CivilDate } {
  let strength = newStrength('cell_a');
  let day = START;
  for (let index = 0; index < steps; index += 1) {
    const step: Step = random.pick(['correct', 'lapse', 'near'] as const);
    if (step === 'correct') {
      strength = recordCorrect(strength, STANDARD_LADDER, day);
    } else if (step === 'lapse') {
      strength = recordLapse(strength, STANDARD_LADDER, day);
    } else {
      strength = recordNearMiss(strength, STANDARD_LADDER, day);
    }
    day = addDays(day, random.int(0, 20));
  }
  return { strength, day };
}

describe('ladder properties', () => {
  it('never leaves the ladder however far it is pushed', () => {
    forEachDraw(80, 1117, (random) => {
      const rung = clampRung(STANDARD_LADDER, random.int(-50, 50));
      expect(rung).toBeGreaterThanOrEqual(0);
      expect(rung).toBeLessThanOrEqual(topRung(STANDARD_LADDER));
    });
  });

  it('climbs to a rung that is never shorter', () => {
    forEachDraw(60, 3323, (random) => {
      const rung = random.int(0, topRung(STANDARD_LADDER));
      expect(intervalAt(STANDARD_LADDER, climb(STANDARD_LADDER, rung))).toBeGreaterThanOrEqual(
        intervalAt(STANDARD_LADDER, rung),
      );
    });
  });

  it('lapses to a rung that is never longer', () => {
    forEachDraw(60, 4441, (random) => {
      const rung = random.int(0, topRung(STANDARD_LADDER));
      expect(intervalAt(STANDARD_LADDER, lapse(STANDARD_LADDER, rung))).toBeLessThanOrEqual(
        intervalAt(STANDARD_LADDER, rung),
      );
    });
  });

  it('spreads an interval within the configured swing and never below a day', () => {
    forEachDraw(120, 5557, (random) => {
      const rung = random.int(0, topRung(STANDARD_LADDER));
      const base = intervalAt(STANDARD_LADDER, rung);
      const spread = spreadInterval(STANDARD_LADDER, rung, random.word());
      expect(spread).toBeGreaterThanOrEqual(1);
      expect(Math.abs(spread - base)).toBeLessThanOrEqual(Math.ceil(base * STANDARD_LADDER.spread));
    });
  });
});

describe('strength properties', () => {
  it('counts every sighting exactly once', () => {
    forEachDraw(80, 6661, (random) => {
      const steps = random.int(1, 12);
      const { strength } = walk(random, steps);
      expect(strength.seen).toBe(steps);
    });
  });

  it('never records more correct answers than sightings', () => {
    forEachDraw(80, 7717, (random) => {
      const { strength } = walk(random, random.int(1, 12));
      expect(strength.correct).toBeLessThanOrEqual(strength.seen);
      expect(accuracy(strength)).toBeLessThanOrEqual(1);
    });
  });

  it('always carries a due date once it has been answered', () => {
    forEachDraw(80, 8887, (random) => {
      const { strength } = walk(random, random.int(1, 12));
      expect(strength.dueOn).toBeDefined();
      expect(isNew(strength)).toBe(false);
    });
  });

  it('never schedules a review in the past relative to the answer', () => {
    forEachDraw(80, 9973, (random) => {
      let strength = newStrength('cell_a');
      let day = START;
      for (let index = 0; index < random.int(1, 8); index += 1) {
        strength = recordCorrect(strength, STANDARD_LADDER, day);
        expect(compareCivil(strength.dueOn!, day)).toBeGreaterThan(0);
        day = addDays(day, random.int(0, 30));
      }
    });
  });

  it('keeps the rung inside the ladder however it was walked', () => {
    forEachDraw(80, 2237, (random) => {
      const { strength } = walk(random, random.int(1, 20));
      expect(strength.rung).toBeGreaterThanOrEqual(0);
      expect(strength.rung).toBeLessThanOrEqual(topRung(STANDARD_LADDER));
    });
  });

  it('takes a suspended cell out of the due set on every day', () => {
    forEachDraw(60, 3391, (random) => {
      const { strength, day } = walk(random, random.int(1, 8));
      const gone = suspend(strength);
      expect(isDue(gone, addDays(day, random.int(0, 400)))).toBe(false);
    });
  });

  it('never pulls a due date forward when deferring', () => {
    forEachDraw(80, 4457, (random) => {
      const { strength, day } = walk(random, random.int(1, 8));
      const target = addDays(day, random.int(-30, 30));
      const deferred = deferTo(strength, target);
      expect(compareCivil(deferred.dueOn!, strength.dueOn!)).toBeGreaterThanOrEqual(0);
    });
  });

  it('reports nothing overdue before the due date', () => {
    forEachDraw(60, 5573, (random) => {
      const { strength } = walk(random, random.int(1, 8));
      const before = addDays(strength.dueOn!, -random.int(1, 20));
      expect(daysOverdue(strength, before)).toBe(0);
      expect(isDue(strength, before)).toBe(false);
    });
  });
});
