import type { CourseScope } from '../../domain/course/authz.js';
import { maturityOf } from '../../domain/report/coverage.js';
import type { AccountDirectory, AuthedRequest } from '../auth.js';
import { withActor } from '../auth.js';
import { rangeFrom, requireCourseKey } from '../query.js';
import type { Route } from '../router.js';
import { json, route } from '../router.js';
import type { ReportService } from '../../app/services/report-service.js';

/**
 * Report routes. Both of them need a window, and the window has to be given
 * rather than defaulted: a report over "the last month" means something
 * different every day it is run, and a link to one that quietly changes what it
 * shows is worse than a link that refuses to open.
 */
export interface ReportRouteDeps {
  readonly directory: AccountDirectory;
  readonly reports: ReportService;
  readonly scopeOf: (courseKey: string) => Promise<CourseScope>;
}

export function reportRoutes(deps: ReportRouteDeps): Route[] {
  const coverage = async (request: AuthedRequest) => {
    const scope = await deps.scopeOf(requireCourseKey(request.query));
    const report = await deps.reports.learner(
      request.actor,
      scope,
      request.params['learnerId']!,
      request.params['language']!,
      request.params['deckKey']!,
      rangeFrom(request.query),
    );

    return json(200, {
      learnerId: report.learnerId,
      deckKey: report.deckKey,
      on: report.on,
      counts: { ...report.counts },
      weakest: report.weakest.map((row) => ({
        key: row.key,
        total: row.total,
        mature: row.mature,
        maturity: maturityOf(row),
      })),
      forecast: report.forecast.map((day) => ({ on: day.on, due: day.due })),
    });
  };

  const activity = async (request: AuthedRequest) => {
    const scope = await deps.scopeOf(requireCourseKey(request.query));
    const report = await deps.reports.activity(
      request.actor,
      scope,
      request.params['learnerId']!,
      rangeFrom(request.query),
    );

    return json(200, {
      learnerId: report.learnerId,
      from: report.range.from,
      to: report.range.to,
      days: report.days.map((day) => ({
        on: day.on,
        answers: day.tally.total,
        correct: day.tally.correct,
        accuracy: day.accuracy,
      })),
      hardest: report.hardest.map((row) => ({
        cellId: row.cellId,
        wrong: row.tally.wrong,
        accuracy: row.accuracy,
      })),
    });
  };

  return [
    route(
      'GET',
      '/learners/:learnerId/languages/:language/decks/:deckKey/coverage',
      withActor(deps.directory, coverage),
    ),
    route('GET', '/learners/:learnerId/activity', withActor(deps.directory, activity)),
  ];
}
