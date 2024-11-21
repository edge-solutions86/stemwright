import { describe, expect, it } from 'vitest';
import { assignment } from '../../src/domain/course/assignment.js';
import { enrol } from '../../src/domain/course/enrolment.js';
import {
  assignmentCells,
  assignmentProgress,
  describeProgress,
  masteryShare,
  unmetTargets,
} from '../../src/domain/course/progress.js';
import { deck } from '../../src/domain/drill/deck.js';
import { attempt } from '../../src/domain/mastery/attempt.js';
import type { AttemptInput } from '../../src/domain/mastery/attempt.js';
import { ladder } from '../../src/domain/mastery/ladder.js';
import { buildLedger, emptyLedger } from '../../src/domain/mastery/ledger.js';
import { newStrength, recordCorrect } from '../../src/domain/mastery/strength.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { michaelmas } from '../support/course.js';
import { drillContext } from '../support/drill.js';
import { latinNoun } from '../support/profiles.js';

const d = (value: string) => parseCivilDate(value);
const TERM = d('2024-10-21');
const flat = ladder({ key: 'flat', rungs: [1, 3], spread: 0 });

const allDeck = deck(latinNoun, { key: 'all' });
const student = enrol(michaelmas, { learnerId: 'lrn_one' });

const week3 = assignment(latinNoun, michaelmas, {
  key: 'week-3',
  deckKey: 'all',
  narrowing: 'number=sg',
  from: d('2024-10-21'),
  to: d('2024-10-27'),
  masteredCells: 5,
  practiceDays: 2,
});

const cells = assignmentCells(drillContext, week3, allDeck, TERM);

const mastered = (cellId: string) =>
  recordCorrect(recordCorrect(newStrength(cellId), flat, TERM), flat, TERM);

let sequence = 0;
const answeredOn = (cellId: string, day: string) => {
  sequence += 1;
  const input: AttemptInput = {
    learnerId: 'lrn_one',
    sessionId: 'sess_aaaaaa',
    cellId,
    deckKey: 'all',
    on: d(day),
    sequence,
    given: 'x',
    expected: 'x',
    verdict: 'exact',
    toleranceLevel: 'standard',
    revisionId: 'rev_aaaaaa',
  };
  return attempt(input);
};

const progressOn = (
  on: string,
  ledger = emptyLedger('lrn_one'),
  attempts: ReturnType<typeof answeredOn>[] = [],
  enrolment = student,
  chosenDeck = allDeck,
) => assignmentProgress(drillContext, week3, chosenDeck, enrolment, ledger, attempts, flat, d(on));

describe('assignmentCells', () => {
  it('intersects the deck scope with the assignment narrowing', () => {
    expect(cells).toHaveLength(15);
    expect(cells.every((cell) => cell.bundle.endsWith('number=sg'))).toBe(true);
  });

  it('is empty when the two selectors do not overlap', () => {
    const plural = deck(latinNoun, { key: 'all', scope: { cells: 'number=pl' } });
    expect(assignmentCells(drillContext, week3, plural, TERM)).toEqual([]);
  });
});

describe('assignmentProgress', () => {
  it('counts nothing for a learner who has not started', () => {
    const progress = progressOn('2024-10-21');
    expect(progress.mastered).toBe(0);
    expect(progress.practiceDays).toBe(0);
    expect(progress.met).toBe(false);
    expect(progress.cells).toBe(15);
  });

  it('counts cells at the top of the ladder as mastered', () => {
    const ledger = buildLedger(
      'lrn_one',
      cells.slice(0, 5).map((cell) => mastered(cell.id)),
    );
    const progress = progressOn('2024-10-21', ledger);
    expect(progress.mastered).toBe(5);
    expect(progress.masteryMet).toBe(true);
    expect(progress.practiceMet).toBe(false);
    expect(progress.met).toBe(false);
  });

  it('counts distinct days of practice inside the window', () => {
    const attempts = [
      answeredOn(cells[0]!.id, '2024-10-21'),
      answeredOn(cells[1]!.id, '2024-10-21'),
      answeredOn(cells[2]!.id, '2024-10-23'),
    ];
    const progress = progressOn('2024-10-27', emptyLedger('lrn_one'), attempts);
    expect(progress.practiceDays).toBe(2);
    expect(progress.practiceMet).toBe(true);
  });

  it('ignores practice before the assignment opened', () => {
    const attempts = [answeredOn(cells[0]!.id, '2024-10-14')];
    expect(progressOn('2024-10-27', emptyLedger('lrn_one'), attempts).practiceDays).toBe(0);
  });

  it('ignores practice outside the enrolment window', () => {
    const latecomer = enrol(michaelmas, { learnerId: 'lrn_two', from: d('2024-10-25') });
    const attempts = [answeredOn(cells[0]!.id, '2024-10-22')];
    const progress = progressOn('2024-10-27', emptyLedger('lrn_one'), attempts, latecomer);
    expect(progress.practiceDays).toBe(0);
  });

  it('stops counting at today rather than at the deadline', () => {
    const attempts = [answeredOn(cells[0]!.id, '2024-10-26')];
    expect(progressOn('2024-10-22', emptyLedger('lrn_one'), attempts).practiceDays).toBe(0);
  });

  it('reports both targets met', () => {
    const ledger = buildLedger(
      'lrn_one',
      cells.slice(0, 6).map((cell) => mastered(cell.id)),
    );
    const attempts = [
      answeredOn(cells[0]!.id, '2024-10-21'),
      answeredOn(cells[1]!.id, '2024-10-23'),
    ];
    const progress = progressOn('2024-10-27', ledger, attempts);
    expect(progress.met).toBe(true);
    expect(unmetTargets(progress)).toEqual([]);
  });

  it('marks the assignment overdue after the deadline', () => {
    const progress = progressOn('2024-11-01');
    expect(progress.overdue).toBe(true);
    expect(unmetTargets(progress)).toEqual(['mastery', 'practice']);
  });

  it('reports a narrowing that selects nothing rather than nought progress', () => {
    const plural = deck(latinNoun, { key: 'all', scope: { cells: 'number=pl' } });
    const progress = progressOn('2024-10-21', emptyLedger('lrn_one'), [], student, plural);
    expect(progress.selectsNothing).toBe(true);
    expect(describeProgress(progress)).toBe('week-3 selects no cells at all');
  });
});

describe('masteryShare and describeProgress', () => {
  it('is the share of the selected cells that are mastered', () => {
    const ledger = buildLedger(
      'lrn_one',
      cells.slice(0, 3).map((cell) => mastered(cell.id)),
    );
    expect(masteryShare(progressOn('2024-10-21', ledger))).toBeCloseTo(0.2);
  });

  it('reads the state out', () => {
    expect(describeProgress(progressOn('2024-10-21'))).toBe(
      'week-3 is in progress, 0 of 15 cells mastered over 0 days',
    );
  });
});
