import { describe, expect, it } from 'vitest';
import { STANDARD_LADDER, intervalAt, ladder } from '../../src/domain/mastery/ladder.js';
import {
  accuracy,
  daysOverdue,
  deferTo,
  describeStrength,
  isDue,
  isIntroduced,
  isMature,
  isNew,
  newStrength,
  recordCorrect,
  recordLapse,
  recordNearMiss,
  suspend,
  unsuspend,
} from '../../src/domain/mastery/strength.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';

const d = (value: string) => parseCivilDate(value);
const DAY = d('2025-03-03');
const flat = ladder({ key: 'flat', rungs: [1, 3, 7, 16], spread: 0 });

describe('newStrength', () => {
  it('starts unseen, unscheduled and at the bottom rung', () => {
    const fresh = newStrength('cell_a');
    expect(fresh.rung).toBe(0);
    expect(fresh.dueOn).toBeUndefined();
    expect(fresh.seen).toBe(0);
    expect(isNew(fresh)).toBe(true);
    expect(isIntroduced(fresh)).toBe(false);
  });

  it('is not due, because it has never been asked', () => {
    expect(isDue(newStrength('cell_a'), DAY)).toBe(false);
  });
});

describe('recordCorrect', () => {
  it('does not climb on the first sighting', () => {
    const first = recordCorrect(newStrength('cell_a'), flat, DAY);
    expect(first.rung).toBe(0);
    expect(first.dueOn).toBe('2025-03-04');
    expect(first.seen).toBe(1);
    expect(first.streak).toBe(1);
  });

  it('climbs on every sighting after the first', () => {
    const first = recordCorrect(newStrength('cell_a'), flat, DAY);
    const second = recordCorrect(first, flat, d('2025-03-04'));
    expect(second.rung).toBe(1);
    expect(second.dueOn).toBe('2025-03-07');
  });

  it('stops climbing at the top of the ladder', () => {
    let strength = recordCorrect(newStrength('cell_a'), flat, DAY);
    for (let step = 0; step < 8; step += 1) {
      strength = recordCorrect(strength, flat, DAY);
    }
    expect(strength.rung).toBe(3);
    expect(isMature(strength, flat)).toBe(true);
  });

  it('records the day it was last seen', () => {
    expect(recordCorrect(newStrength('cell_a'), flat, DAY).lastSeenOn).toBe('2025-03-03');
  });
});

describe('recordLapse', () => {
  it('drops the rung and reschedules for the shortest interval', () => {
    const once = recordCorrect(newStrength('c'), flat, DAY);
    const known = recordCorrect(recordCorrect(once, flat, DAY), flat, DAY);
    expect(known.rung).toBe(2);
    const lapsed = recordLapse(known, flat, d('2025-03-20'));
    expect(lapsed.rung).toBe(0);
    expect(lapsed.dueOn).toBe('2025-03-21');
    expect(lapsed.lapses).toBe(1);
    expect(lapsed.streak).toBe(0);
  });

  it('counts the sighting even though it was wrong', () => {
    const lapsed = recordLapse(newStrength('cell_a'), flat, DAY);
    expect(lapsed.seen).toBe(1);
    expect(lapsed.correct).toBe(0);
  });

  it('cannot drop below the bottom rung', () => {
    expect(recordLapse(newStrength('cell_a'), flat, DAY).rung).toBe(0);
  });
});

describe('recordNearMiss', () => {
  const known = recordCorrect(recordCorrect(newStrength('c'), flat, DAY), flat, DAY);

  it('leaves the rung where it was', () => {
    expect(recordNearMiss(known, flat, d('2025-03-10')).rung).toBe(known.rung);
  });

  it('brings the cell back at the shortest interval and breaks the streak', () => {
    const near = recordNearMiss(known, flat, d('2025-03-10'));
    expect(near.dueOn).toBe('2025-03-11');
    expect(near.streak).toBe(0);
  });

  it('does not count as a lapse or as correct', () => {
    const near = recordNearMiss(newStrength('cell_a'), flat, DAY);
    expect(near.lapses).toBe(0);
    expect(near.correct).toBe(0);
    expect(near.seen).toBe(1);
  });
});

describe('isDue and daysOverdue', () => {
  const seen = recordCorrect(newStrength('cell_a'), flat, DAY);

  it('is due on the day it comes round and after', () => {
    expect(isDue(seen, d('2025-03-03'))).toBe(false);
    expect(isDue(seen, d('2025-03-04'))).toBe(true);
    expect(isDue(seen, d('2025-03-09'))).toBe(true);
  });

  it('counts how far past due it is', () => {
    expect(daysOverdue(seen, d('2025-03-04'))).toBe(0);
    expect(daysOverdue(seen, d('2025-03-09'))).toBe(5);
    expect(daysOverdue(seen, d('2025-03-01'))).toBe(0);
  });

  it('reports nothing overdue for a cell that was never asked', () => {
    expect(daysOverdue(newStrength('cell_a'), DAY)).toBe(0);
  });
});

describe('suspension', () => {
  const seen = recordCorrect(recordCorrect(newStrength('cell_a'), flat, DAY), flat, DAY);

  it('takes a suspended cell out of the due set', () => {
    expect(isDue(suspend(seen), d('2030-01-01'))).toBe(false);
    expect(isNew(suspend(seen))).toBe(false);
  });

  it('reschedules from the rung it kept when it comes back', () => {
    const revived = unsuspend(suspend(seen), flat, d('2025-06-01'));
    expect(revived.suspended).toBe(false);
    expect(revived.rung).toBe(seen.rung);
    expect(revived.dueOn).toBe('2025-06-04');
  });

  it('leaves a cell that was not suspended alone', () => {
    expect(unsuspend(seen, flat, d('2025-06-01'))).toBe(seen);
  });
});

describe('deferTo', () => {
  const seen = recordCorrect(newStrength('cell_a'), flat, DAY);

  it('pushes a due date later', () => {
    expect(deferTo(seen, d('2025-03-10')).dueOn).toBe('2025-03-10');
  });

  it('never pulls a due date forward', () => {
    expect(deferTo(seen, d('2025-03-01')).dueOn).toBe('2025-03-04');
  });

  it('leaves a new cell alone', () => {
    const fresh = newStrength('cell_a');
    expect(deferTo(fresh, d('2025-03-10'))).toBe(fresh);
  });
});

describe('accuracy', () => {
  it('is zero for a cell nobody has answered', () => {
    expect(accuracy(newStrength('cell_a'))).toBe(0);
  });

  it('is the share of sightings that were right', () => {
    const mixed = recordLapse(recordCorrect(newStrength('cell_a'), flat, DAY), flat, DAY);
    expect(accuracy(mixed)).toBe(0.5);
  });
});

describe('describeStrength', () => {
  it('says a new cell is not introduced', () => {
    expect(describeStrength(newStrength('cell_a'), STANDARD_LADDER)).toBe('not introduced yet');
  });

  it('names the rung and the next day', () => {
    const seen = recordCorrect(newStrength('cell_a'), flat, DAY);
    expect(describeStrength(seen, flat)).toBe('rung 0 of 3, next on 2025-03-04');
  });

  it('says how many lapses a suspension followed', () => {
    const leech = suspend(recordLapse(recordLapse(newStrength('cell_a'), flat, DAY), flat, DAY));
    expect(describeStrength(leech, flat)).toBe('suspended after 2 lapses');
  });
});

describe('the first interval', () => {
  it('is the bottom rung of whatever ladder is in use', () => {
    const seen = recordCorrect(newStrength('cell_a'), STANDARD_LADDER, DAY);
    expect(seen.dueOn).toBe(`2025-03-0${String(3 + intervalAt(STANDARD_LADDER, 0))}`);
  });
});
