import { groupBy } from '../collections.js';
import type { Attempt, AttemptTally } from '../mastery/attempt.js';
import { accuracyOf, tally, wasCorrect } from '../mastery/attempt.js';
import type { CivilDate } from '../time/civil.js';
import type { DayRange } from '../time/window.js';
import { eachDay, rangeContains } from '../time/window.js';

/**
 * Attempts rolled up the three ways anybody ever asks for. Down the days for a
 * progress chart, down the cells for a weak spot list, and down the decks for a
 * term report.
 *
 * All three read the attempt log rather than the mastery ledger, because the
 * ledger holds where the learner is now and the log holds how they got there.
 * A cell answered wrong four times and then right is strong in the ledger and
 * still worth a teacher's attention in the log.
 */
export interface DayRollup {
  readonly on: CivilDate;
  readonly tally: AttemptTally;
  readonly accuracy: number;
  readonly distinctCells: number;
}

export function dailyRollup(attempts: readonly Attempt[], range: DayRange): DayRollup[] {
  const byDay = groupBy(
    attempts.filter((entry) => rangeContains(range, entry.on)),
    (entry) => entry.on,
  );

  const rollups: DayRollup[] = [];
  for (const day of eachDay(range)) {
    const forDay = byDay.get(day) ?? [];
    const tallied = tally(forDay);
    rollups.push({
      on: day,
      tally: tallied,
      accuracy: accuracyOf(tallied),
      distinctCells: new Set(forDay.map((entry) => entry.cellId)).size,
    });
  }
  return rollups;
}

export interface CellRollup {
  readonly cellId: string;
  readonly tally: AttemptTally;
  readonly accuracy: number;
  readonly lastVerdict: string;
  readonly lastSeenOn: CivilDate;
}

export function cellRollup(attempts: readonly Attempt[]): CellRollup[] {
  const byCell = groupBy(attempts, (entry) => entry.cellId);

  return [...byCell.entries()]
    .map(([cellId, forCell]) => {
      const ordered = [...forCell].sort(
        (a, b) => a.on.localeCompare(b.on) || a.sequence - b.sequence,
      );
      const last = ordered[ordered.length - 1]!;
      const tallied = tally(ordered);
      return {
        cellId,
        tally: tallied,
        accuracy: accuracyOf(tallied),
        lastVerdict: last.verdict,
        lastSeenOn: last.on,
      };
    })
    .sort((a, b) => a.cellId.localeCompare(b.cellId));
}

/** Cells worth showing a teacher: most wrong answers first, ties broken by rate. */
export function hardestCells(rollups: readonly CellRollup[], limit = 10): CellRollup[] {
  return [...rollups]
    .filter((row) => row.tally.wrong + row.tally.near > 0)
    .sort((a, b) => {
      const byWrong = b.tally.wrong - a.tally.wrong;
      if (byWrong !== 0) {
        return byWrong;
      }
      const byAccuracy = a.accuracy - b.accuracy;
      return Math.abs(byAccuracy) > 1e-9 ? byAccuracy : a.cellId.localeCompare(b.cellId);
    })
    .slice(0, limit);
}

export interface DeckRollup {
  readonly deckKey: string;
  readonly tally: AttemptTally;
  readonly accuracy: number;
  readonly days: number;
}

export function deckRollup(attempts: readonly Attempt[]): DeckRollup[] {
  const byDeck = groupBy(attempts, (entry) => entry.deckKey);

  return [...byDeck.entries()]
    .map(([deckKey, forDeck]) => {
      const tallied = tally(forDeck);
      return {
        deckKey,
        tally: tallied,
        accuracy: accuracyOf(tallied),
        days: new Set(forDeck.map((entry) => entry.on)).size,
      };
    })
    .sort((a, b) => a.deckKey.localeCompare(b.deckKey));
}

/** Days the learner answered anything, which is what a streak is counted over. */
export function activeDays(attempts: readonly Attempt[]): Set<string> {
  return new Set(attempts.map((entry) => entry.on));
}

export function firstCorrectOn(
  attempts: readonly Attempt[],
  cellId: string,
): CivilDate | undefined {
  return [...attempts]
    .filter((entry) => entry.cellId === cellId && wasCorrect(entry))
    .sort((a, b) => a.on.localeCompare(b.on))[0]?.on;
}

export function attemptsInRange(attempts: readonly Attempt[], range: DayRange): Attempt[] {
  return attempts.filter((entry) => rangeContains(range, entry.on));
}
