import { ValidationError } from '../errors.js';
import type { CivilDate } from '../time/civil.js';
import { addDays, compareCivil, daysBetween } from '../time/civil.js';
import type { Ladder } from './ladder.js';
import { climb, intervalAt, isAtTop, lapse, spreadInterval } from './ladder.js';

/**
 * What the desk knows about one learner and one cell. Everything here is
 * derived from answers rather than set by hand, and it is deliberately small:
 * a rung on a ladder, a due date, and enough counting to spot a leech.
 *
 * `rung` and `dueOn` are separate because they answer different questions. The
 * rung says how well the cell is known, the due date says when to ask again,
 * and a shielded day or a suspension moves the second without touching the
 * first. Collapsing them into one number is how a system ends up punishing a
 * learner for a week away.
 */
export interface CellStrength {
  readonly cellId: string;
  readonly rung: number;
  readonly dueOn: CivilDate | undefined;
  readonly seen: number;
  readonly correct: number;
  readonly lapses: number;
  readonly streak: number;
  readonly lastSeenOn: CivilDate | undefined;
  readonly suspended: boolean;
}

export function newStrength(cellId: string): CellStrength {
  return {
    cellId,
    rung: 0,
    dueOn: undefined,
    seen: 0,
    correct: 0,
    lapses: 0,
    streak: 0,
    lastSeenOn: undefined,
    suspended: false,
  };
}

export function isIntroduced(strength: CellStrength): boolean {
  return strength.seen > 0;
}

/** A cell with no due date has never been answered, so it is not due, it is new. */
export function isDue(strength: CellStrength, on: CivilDate): boolean {
  if (strength.suspended || strength.dueOn === undefined) {
    return false;
  }
  return compareCivil(strength.dueOn, on) <= 0;
}

export function isNew(strength: CellStrength): boolean {
  return !strength.suspended && strength.dueOn === undefined;
}

export function daysOverdue(strength: CellStrength, on: CivilDate): number {
  if (strength.dueOn === undefined) {
    return 0;
  }
  return Math.max(0, daysBetween(strength.dueOn, on));
}

export function accuracy(strength: CellStrength): number {
  return strength.seen === 0 ? 0 : strength.correct / strength.seen;
}

function reschedule(
  strength: CellStrength,
  ladder: Ladder,
  rung: number,
  on: CivilDate,
): CivilDate {
  return addDays(on, spreadInterval(ladder, rung, strength.cellId));
}

export function recordCorrect(strength: CellStrength, ladder: Ladder, on: CivilDate): CellStrength {
  const rung = isIntroduced(strength) ? climb(ladder, strength.rung) : strength.rung;
  return {
    ...strength,
    rung,
    dueOn: reschedule(strength, ladder, rung, on),
    seen: strength.seen + 1,
    correct: strength.correct + 1,
    streak: strength.streak + 1,
    lastSeenOn: on,
  };
}

export function recordLapse(strength: CellStrength, ladder: Ladder, on: CivilDate): CellStrength {
  const rung = lapse(ladder, strength.rung);
  return {
    ...strength,
    rung,
    dueOn: addDays(on, intervalAt(ladder, 0)),
    seen: strength.seen + 1,
    lapses: strength.lapses + 1,
    streak: 0,
    lastSeenOn: on,
  };
}

/**
 * A near miss neither climbs nor lapses. The cell comes back at the same rung
 * after the shortest interval, which is what a teacher does with an answer that
 * was almost right rather than either rewarding or punishing it.
 */
export function recordNearMiss(
  strength: CellStrength,
  ladder: Ladder,
  on: CivilDate,
): CellStrength {
  return {
    ...strength,
    dueOn: addDays(on, intervalAt(ladder, 0)),
    seen: strength.seen + 1,
    streak: 0,
    lastSeenOn: on,
  };
}

export function suspend(strength: CellStrength): CellStrength {
  return { ...strength, suspended: true };
}

export function unsuspend(strength: CellStrength, ladder: Ladder, on: CivilDate): CellStrength {
  if (!strength.suspended) {
    return strength;
  }
  return {
    ...strength,
    suspended: false,
    dueOn: addDays(on, intervalAt(ladder, strength.rung)),
  };
}

/**
 * Pushes the due date out without touching the rung. Used for a shielded day
 * and for a deferral, and it never pulls a due date forward, because a cell
 * that was already overdue should not be made more overdue by a rest day.
 */
export function deferTo(strength: CellStrength, on: CivilDate): CellStrength {
  if (strength.dueOn === undefined) {
    return strength;
  }
  return compareCivil(strength.dueOn, on) >= 0 ? strength : { ...strength, dueOn: on };
}

export function isMature(strength: CellStrength, ladder: Ladder): boolean {
  return isAtTop(ladder, strength.rung);
}

export function describeStrength(strength: CellStrength, ladder: Ladder): string {
  if (strength.suspended) {
    return `suspended after ${strength.lapses} lapses`;
  }
  if (isNew(strength)) {
    return 'not introduced yet';
  }
  if (strength.dueOn === undefined) {
    throw new ValidationError('a seen cell must carry a due date');
  }
  return `rung ${strength.rung} of ${ladder.rungs.length - 1}, next on ${strength.dueOn}`;
}
