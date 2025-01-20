import type { Actor, CourseScope } from '../../domain/course/authz.js';
import { requireReadLearner } from '../../domain/course/authz.js';
import type { DrillPolicyPatch } from '../../domain/drill/policy.js';
import { applyPolicyPatch, drillPolicy } from '../../domain/drill/policy.js';
import type { Ladder } from '../../domain/mastery/ladder.js';
import { STANDARD_LADDER } from '../../domain/mastery/ladder.js';
import { emptyLedger, ledgerCounts } from '../../domain/mastery/ledger.js';
import type { LedgerCounts } from '../../domain/mastery/ledger.js';
import type { CohortReport } from '../../domain/report/cohort.js';
import { cohortReport } from '../../domain/report/cohort.js';
import type { CoverageRow } from '../../domain/report/coverage.js';
import { coverageByBundle, coverageByLexeme, weakestRows } from '../../domain/report/coverage.js';
import type { ForecastDay } from '../../domain/report/forecast.js';
import { forecast } from '../../domain/report/forecast.js';
import type { CellRollup, DayRollup } from '../../domain/report/rollup.js';
import { cellRollup, dailyRollup, hardestCells } from '../../domain/report/rollup.js';
import type { AssignmentProgress } from '../../domain/course/progress.js';
import type { CivilDate } from '../../domain/time/civil.js';
import type { DayRange } from '../../domain/time/window.js';
import type { Stores, Clock } from '../ports.js';
import type { DrillService } from './drill-service.js';

/**
 * Reporting, gathered in one place so that no route has to know which of the
 * four report modules answers which question. Every method that names a learner
 * checks first, and the check needs a scope, so a report on somebody else is
 * impossible to build by forgetting an argument.
 */
export interface LearnerReport {
  readonly learnerId: string;
  readonly deckKey: string;
  readonly on: CivilDate;
  readonly counts: LedgerCounts;
  readonly byBundle: readonly CoverageRow[];
  readonly byLexeme: readonly CoverageRow[];
  readonly weakest: readonly CoverageRow[];
  readonly forecast: readonly ForecastDay[];
}

export interface ActivityReport {
  readonly learnerId: string;
  readonly range: DayRange;
  readonly days: readonly DayRollup[];
  readonly hardest: readonly CellRollup[];
}

export interface ReportService {
  learner(
    actor: Actor,
    scope: CourseScope,
    learnerId: string,
    language: string,
    deckKey: string,
    range: DayRange,
    ladder?: Ladder,
    policy?: DrillPolicyPatch,
  ): Promise<LearnerReport>;
  activity(
    actor: Actor,
    scope: CourseScope,
    learnerId: string,
    range: DayRange,
  ): Promise<ActivityReport>;
  cohort(
    actor: Actor,
    scope: CourseScope,
    rows: readonly AssignmentProgress[],
    assignmentKey: string,
    on?: CivilDate,
  ): Promise<CohortReport>;
}

export function createReportService(
  stores: Stores,
  clock: Clock,
  drill: DrillService,
): ReportService {
  return {
    async learner(actor, scope, learnerId, language, deckKey, range, ladder, policy) {
      requireReadLearner(actor, learnerId, scope);
      const membership = await drill.membership(language, deckKey, range.from);
      const ledger = (await stores.ledgers.get(learnerId)) ?? emptyLedger(learnerId);
      const chosen = ladder ?? STANDARD_LADDER;
      const cellIds = membership.cells.map((cell) => cell.id);
      const byBundle = coverageByBundle(membership.cells, ledger, chosen, range.from);

      return {
        learnerId,
        deckKey,
        on: range.from,
        counts: ledgerCounts(ledger, cellIds, chosen, range.from),
        byBundle,
        byLexeme: coverageByLexeme(membership.cells, ledger, chosen, range.from),
        weakest: weakestRows(byBundle),
        forecast: forecast(
          membership.cells,
          ledger,
          range,
          applyPolicyPatch(drillPolicy(), policy ?? {}),
        ),
      };
    },

    async activity(actor, scope, learnerId, range) {
      requireReadLearner(actor, learnerId, scope);
      const attempts = await stores.attempts.listByLearner(learnerId, range);
      return {
        learnerId,
        range,
        days: dailyRollup(attempts, range),
        hardest: hardestCells(cellRollup(attempts)),
      };
    },

    async cohort(actor, scope, rows, assignmentKey, on) {
      return Promise.resolve(cohortReport(actor, scope, rows, assignmentKey, on ?? clock.today()));
    },
  };
}
