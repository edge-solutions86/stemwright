import { ValidationError } from '../../domain/errors.js';
import { require as requirePermission } from '../../domain/course/authz.js';
import type { CourseScope } from '../../domain/course/authz.js';
import { queueCells } from '../../domain/drill/queue.js';
import { summarise } from '../../domain/drill/session.js';
import type { CivilDate } from '../../domain/time/civil.js';
import { parseCivilDate } from '../../domain/time/civil.js';
import type { Json } from '../../infra/codec.js';
import { readString, requireObject } from '../../infra/codec.js';
import type { AccountDirectory, AuthedRequest } from '../auth.js';
import { withActor } from '../auth.js';
import type { Route } from '../router.js';
import { json, route } from '../router.js';
import type { DrillService } from '../../app/services/drill-service.js';

/**
 * The drill routes. There are three, and between them they are the whole
 * learner facing api: what should I answer, here is my answer, I am done.
 *
 * Every one of them takes the day from the query string when it is given, which
 * looks like a testing affordance and is not. A learner in Auckland is on
 * tomorrow's date before a learner in Lisbon has had breakfast, and the client
 * knows which civil day the learner believes they are on.
 */
export interface DrillRouteDeps {
  readonly directory: AccountDirectory;
  readonly drill: DrillService;
  readonly scopeOf: (courseKey: string) => Promise<CourseScope>;
}

function dayFrom(query: Readonly<Record<string, string>>): CivilDate | undefined {
  const value = query['on'];
  return value === undefined ? undefined : parseCivilDate(value, 'on');
}

function courseKeyOf(query: Readonly<Record<string, string>>): string {
  const value = query['course'];
  if (value === undefined || value === '') {
    throw ValidationError.field('course', 'must be given so permissions can be checked');
  }
  return value;
}

export function drillRoutes(deps: DrillRouteDeps): Route[] {
  const queue = async (request: AuthedRequest) => {
    const learnerId = request.params['learnerId']!;
    const scope = await deps.scopeOf(courseKeyOf(request.query));
    requirePermission(request.actor, 'drill', scope);

    const built = await deps.drill.queue(
      learnerId,
      request.params['language']!,
      request.params['deckKey']!,
      dayFrom(request.query),
    );

    return json(200, {
      on: built.on,
      deckKey: built.deckKey,
      cells: queueCells(built).map((cell) => ({
        id: cell.id,
        lexemeId: cell.lexemeId,
        bundle: cell.bundle,
      })),
      excluded: built.excluded.size,
    });
  };

  const answer = async (request: AuthedRequest) => {
    const learnerId = request.params['learnerId']!;
    const scope = await deps.scopeOf(courseKeyOf(request.query));
    requirePermission(request.actor, 'drill', scope);

    const body = requireObject('body', request.body);
    const day = dayFrom(request.query);
    const outcome = await deps.drill.answer({
      learnerId,
      language: request.params['language']!,
      deckKey: request.params['deckKey']!,
      cellId: readString(body, 'body', 'cellId'),
      given: readString(body, 'body', 'given'),
      ...(day === undefined ? {} : { on: day }),
    });

    return json(200, {
      verdict: outcome.review.match.verdict,
      expected: outcome.review.expected,
      miss: outcome.review.match.miss ?? null,
      twinsCredited: outcome.review.twinsCredited,
      sessionId: outcome.session.id,
      sequence: outcome.review.attempt.sequence,
    } as Json);
  };

  const finish = async (request: AuthedRequest) => {
    const learnerId = request.params['learnerId']!;
    const scope = await deps.scopeOf(courseKeyOf(request.query));
    requirePermission(request.actor, 'drill', scope);

    const closed = await deps.drill.finish(learnerId, request.params['sessionId']!);
    const summary = summarise(closed);
    return json(200, {
      sessionId: summary.sessionId,
      answers: summary.tally.total,
      correct: summary.tally.correct,
      accuracy: summary.accuracy,
    });
  };

  return [
    route(
      'GET',
      '/learners/:learnerId/languages/:language/decks/:deckKey/queue',
      withActor(deps.directory, queue),
    ),
    route(
      'POST',
      '/learners/:learnerId/languages/:language/decks/:deckKey/answers',
      withActor(deps.directory, answer),
    ),
    route(
      'POST',
      '/learners/:learnerId/sessions/:sessionId/finish',
      withActor(deps.directory, finish),
    ),
  ];
}
