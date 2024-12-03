import type { Actor, CourseScope } from '../course/authz.js';
import { canReadLearner } from '../course/authz.js';
import type { AssignmentProgress } from '../course/progress.js';
import { masteryShare } from '../course/progress.js';
import type { CivilDate } from '../time/civil.js';

/**
 * A cohort report over one assignment. It is built from progress rows that were
 * computed elsewhere, so this module never touches a ledger and never has to be
 * told which day it is.
 *
 * What it does own is the redaction. A viewer sees the rows they are allowed to
 * see and a count of the rows they are not, rather than a shorter list that
 * quietly reads as a smaller class. A report that hides its own gaps is worse
 * than one that refuses to run.
 */
export interface CohortRow {
  readonly learnerId: string;
  readonly mastered: number;
  readonly cells: number;
  readonly practiceDays: number;
  readonly met: boolean;
  readonly share: number;
}

export interface CohortReport {
  readonly assignmentKey: string;
  readonly on: CivilDate;
  readonly rows: readonly CohortRow[];
  readonly hidden: number;
  readonly metCount: number;
  readonly medianShare: number;
}

function toRow(progress: AssignmentProgress): CohortRow {
  return {
    learnerId: progress.learnerId,
    mastered: progress.mastered,
    cells: progress.cells,
    practiceDays: progress.practiceDays,
    met: progress.met,
    share: masteryShare(progress),
  };
}

export function median(values: readonly number[]): number {
  if (values.length === 0) {
    return 0;
  }
  const sorted = [...values].sort((a, b) => a - b);
  const middle = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 1 ? sorted[middle]! : (sorted[middle - 1]! + sorted[middle]!) / 2;
}

export function cohortReport(
  actor: Actor,
  scope: CourseScope,
  progresses: readonly AssignmentProgress[],
  assignmentKey: string,
  on: CivilDate,
): CohortReport {
  const visible: CohortRow[] = [];
  let hidden = 0;

  for (const progress of progresses) {
    if (canReadLearner(actor, progress.learnerId, scope)) {
      visible.push(toRow(progress));
    } else {
      hidden += 1;
    }
  }

  visible.sort((a, b) => b.share - a.share || a.learnerId.localeCompare(b.learnerId));

  return {
    assignmentKey,
    on,
    rows: visible,
    hidden,
    metCount: visible.filter((row) => row.met).length,
    medianShare: median(visible.map((row) => row.share)),
  };
}

export function isRedacted(report: CohortReport): boolean {
  return report.hidden > 0;
}

export function metShare(report: CohortReport): number {
  return report.rows.length === 0 ? 0 : report.metCount / report.rows.length;
}

/** The learners furthest from the target, which is who a teacher chases. */
export function furthestBehind(report: CohortReport, limit = 5): CohortRow[] {
  return [...report.rows]
    .filter((row) => !row.met)
    .sort((a, b) => a.share - b.share || a.learnerId.localeCompare(b.learnerId))
    .slice(0, limit);
}

export function describeCohort(report: CohortReport): string {
  if (report.rows.length === 0) {
    return report.hidden === 0
      ? `nobody is working on ${report.assignmentKey}`
      : `every row of ${report.assignmentKey} is hidden from you`;
  }
  const percent = Math.round(metShare(report) * 100);
  const tail = report.hidden === 0 ? '' : `, ${report.hidden} rows hidden from you`;
  return `${report.metCount} of ${report.rows.length} have met ${report.assignmentKey}, ${percent} percent${tail}`;
}
