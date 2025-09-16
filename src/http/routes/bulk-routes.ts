import { ValidationError } from '../../domain/errors.js';
import type { CourseScope } from '../../domain/course/authz.js';
import { require as requirePermission } from '../../domain/course/authz.js';
import type { Json } from '../../infra/codec.js';
import { readArray, readString, requireObject } from '../../infra/codec.js';
import type { AccountDirectory, AuthedRequest } from '../auth.js';
import { withActor } from '../auth.js';
import { dayFrom, requireCourseKey } from '../query.js';
import type { Route } from '../router.js';
import { json, route } from '../router.js';
import type { DrillService } from '../../app/services/drill-service.js';

/**
 * Answering several cells in one request, for a client that went offline.
 *
 * The answers are applied in order and the run does not stop at the first
 * failure, because a batch from a phone that lost signal is exactly the case
 * where one bad row should not lose the other nineteen. Every answer gets a
 * result, and the response says how many of each there were so a client can
 * decide whether to retry.
 *
 * The batch is capped. An uncapped bulk endpoint is a way to hold a request
 * open for a minute, and there is no client that needs more than a sitting.
 */
export const MAX_BATCH = 100;

export interface BulkRouteDeps {
  readonly directory: AccountDirectory;
  readonly drill: DrillService;
  readonly scopeOf: (courseKey: string) => Promise<CourseScope>;
}

export interface BulkAnswer {
  readonly cellId: string;
  readonly given: string;
}

export function readBatch(body: Json | undefined): BulkAnswer[] {
  const row = requireObject('body', body);
  const rows = readArray(row, 'body', 'answers');
  if (rows.length === 0) {
    throw ValidationError.field('body.answers', 'must hold at least one answer');
  }
  if (rows.length > MAX_BATCH) {
    throw ValidationError.field('body.answers', `must hold at most ${MAX_BATCH} answers`);
  }
  return rows.map((entry, index) => {
    const path = `body.answers.${index}`;
    const answer = requireObject(path, entry);
    return {
      cellId: readString(answer, path, 'cellId'),
      given: readString(answer, path, 'given'),
    };
  });
}

export function bulkRoutes(deps: BulkRouteDeps): Route[] {
  const answerAll = async (request: AuthedRequest) => {
    const learnerId = request.params['learnerId']!;
    const scope = await deps.scopeOf(requireCourseKey(request.query));
    requirePermission(request.actor, 'drill', scope);

    const batch = readBatch(request.body);
    const day = dayFrom(request.query);
    const results: Json[] = [];
    let accepted = 0;
    let refused = 0;

    for (const answer of batch) {
      try {
        const outcome = await deps.drill.answer({
          learnerId,
          language: request.params['language']!,
          deckKey: request.params['deckKey']!,
          cellId: answer.cellId,
          given: answer.given,
          ...(day === undefined ? {} : { on: day }),
        });
        accepted += 1;
        results.push({
          cellId: answer.cellId,
          ok: true,
          verdict: outcome.review.match.verdict,
          expected: outcome.review.expected,
        });
      } catch (error) {
        refused += 1;
        results.push({
          cellId: answer.cellId,
          ok: false,
          message: error instanceof Error ? error.message : String(error),
        });
      }
    }

    return json(refused === 0 ? 200 : 207, { accepted, refused, results });
  };

  return [
    route(
      'POST',
      '/learners/:learnerId/languages/:language/decks/:deckKey/answers/bulk',
      withActor(deps.directory, answerAll),
    ),
  ];
}
