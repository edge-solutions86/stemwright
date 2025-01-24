import { NotFoundError } from '../../domain/errors.js';
import type { Deck } from '../../domain/drill/deck.js';
import type { DrillContext, Membership } from '../../domain/drill/membership.js';
import { resolveMembership } from '../../domain/drill/membership.js';
import type { DrillPolicy, DrillPolicyPatch } from '../../domain/drill/policy.js';
import { applyPolicyPatch, drillPolicy } from '../../domain/drill/policy.js';
import type { Queue } from '../../domain/drill/queue.js';
import { assembleQueue } from '../../domain/drill/queue.js';
import type { ReviewResult, ReviewSettings } from '../../domain/drill/review.js';
import { reviewAnswer } from '../../domain/drill/review.js';
import type { Session } from '../../domain/drill/session.js';
import {
  closeSession,
  nextSequence,
  openSession,
  recordAttempt,
  summarise,
} from '../../domain/drill/session.js';
import type { Ladder } from '../../domain/mastery/ladder.js';
import { STANDARD_LADDER } from '../../domain/mastery/ladder.js';
import type { LeechPolicy } from '../../domain/mastery/leech.js';
import { leechPolicy } from '../../domain/mastery/leech.js';
import { emptyLedger, strengthFor } from '../../domain/mastery/ledger.js';
import type { Cell } from '../../domain/paradigm/cell.js';
import type { CivilDate } from '../../domain/time/civil.js';
import type { EventBus } from '../events.js';
import { loadContext as loadDrillContext, requireDeck, requireLanguage } from '../loaders.js';
import type { Clock, Stores } from '../ports.js';

/**
 * The drill service is where a request meets the domain. It loads a context,
 * builds the queue, marks an answer and writes the three things an answer
 * changes: the attempt log, the open session and the ledger.
 *
 * Those three writes are not atomic here, and the order matters. The ledger
 * goes last, because an attempt recorded without a ledger update is a lost
 * schedule change that the next review will fix, while a ledger update without
 * the attempt is a mark nobody can account for.
 */
export interface DrillSettings {
  readonly policy?: DrillPolicyPatch;
  readonly ladder?: Ladder;
  readonly leech?: LeechPolicy;
}

export interface AnswerRequest {
  readonly learnerId: string;
  readonly language: string;
  readonly deckKey: string;
  readonly cellId: string;
  readonly given: string;
  readonly on?: CivilDate;
  readonly elapsedMs?: number;
}

export interface AnswerOutcome {
  readonly review: ReviewResult;
  readonly session: Session;
}

export interface DrillService {
  context(language: string): Promise<DrillContext>;
  deck(language: string, key: string): Promise<Deck>;
  membership(language: string, deckKey: string, on: CivilDate): Promise<Membership>;
  queue(
    learnerId: string,
    language: string,
    deckKey: string,
    on?: CivilDate,
    settings?: DrillSettings,
  ): Promise<Queue>;
  answer(request: AnswerRequest, settings?: DrillSettings): Promise<AnswerOutcome>;
  finish(learnerId: string, sessionId: string): Promise<Session>;
}

function resolvePolicy(settings: DrillSettings | undefined): DrillPolicy {
  return applyPolicyPatch(drillPolicy(), settings?.policy ?? {});
}

export function createDrillService(stores: Stores, bus: EventBus, clock: Clock): DrillService {
  const loadContext = (language: string): Promise<DrillContext> =>
    loadDrillContext(stores, language);
  const loadDeck = (language: string, key: string): Promise<Deck> =>
    requireDeck(stores, language, key);

  const loadMembership = async (language: string, deckKey: string, on: CivilDate) =>
    resolveMembership(await loadContext(language), await loadDeck(language, deckKey), on);

  const settingsFor = async (
    language: string,
    settings: DrillSettings | undefined,
  ): Promise<ReviewSettings> => {
    const bundle = await requireLanguage(stores, language);
    return {
      ortho: bundle.ortho,
      rules: bundle.rules,
      ladder: settings?.ladder ?? STANDARD_LADDER,
      leech: settings?.leech ?? leechPolicy(),
    };
  };

  const openFor = async (learnerId: string, deckKey: string, on: CivilDate): Promise<Session> => {
    const open = await stores.sessions.listOpen(learnerId);
    const found = open.find((session) => session.deckKey === deckKey && session.on === on);
    return found ?? openSession(learnerId, deckKey, on);
  };

  return {
    context: loadContext,
    deck: loadDeck,
    membership: loadMembership,

    async queue(learnerId, language, deckKey, on, settings) {
      const day = on ?? clock.today();
      const context = await loadContext(language);
      const membership = await loadMembership(language, deckKey, day);
      const ledger = (await stores.ledgers.get(learnerId)) ?? emptyLedger(learnerId);
      const session = await openFor(learnerId, deckKey, day);
      return assembleQueue(context, membership, resolvePolicy(settings), ledger, day, {
        buried: session.buried,
      });
    },

    async answer(request, settings) {
      const day = request.on ?? clock.today();
      const context = await loadContext(request.language);
      const membership = await loadMembership(request.language, request.deckKey, day);
      const cell: Cell | undefined = membership.cells.find((entry) => entry.id === request.cellId);
      if (cell === undefined) {
        throw new NotFoundError('cell', request.cellId);
      }

      const session = await openFor(request.learnerId, request.deckKey, day);
      const ledger =
        (await stores.ledgers.get(request.learnerId)) ?? emptyLedger(request.learnerId);
      const reviewSettings = await settingsFor(request.language, settings);

      const review = reviewAnswer(context, reviewSettings, ledger, session.buried, {
        cell,
        given: request.given,
        on: day,
        learnerId: request.learnerId,
        sessionId: session.id,
        sequence: nextSequence(session),
        deckKey: request.deckKey,
        ...(request.elapsedMs === undefined ? {} : { elapsedMs: request.elapsedMs }),
      });

      const grown = recordAttempt(session, review.attempt, review.buried);
      await stores.attempts.append([review.attempt]);
      await stores.sessions.put(grown);
      await stores.ledgers.put(review.ledger);

      bus.publish({
        kind: 'attempt.recorded',
        at: clock.now(),
        actorId: request.learnerId,
        attempt: review.attempt,
      });

      const after = strengthFor(review.ledger, cell.id);
      if (after.suspended) {
        bus.publish({
          kind: 'cell.suspended',
          at: clock.now(),
          actorId: request.learnerId,
          learnerId: request.learnerId,
          cellId: cell.id,
          lapses: after.lapses,
        });
      }

      return { review, session: grown };
    },

    async finish(learnerId, sessionId) {
      const session = await stores.sessions.get(sessionId);
      if (session === undefined || session.learnerId !== learnerId) {
        throw new NotFoundError('session', sessionId);
      }
      const closed = closeSession(session);
      await stores.sessions.put(closed);
      bus.publish({
        kind: 'session.closed',
        at: clock.now(),
        actorId: learnerId,
        sessionId,
        learnerId,
        answers: summarise(closed).tally.total,
      });
      return closed;
    },
  };
}
