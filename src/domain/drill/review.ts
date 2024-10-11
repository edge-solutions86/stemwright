import { NotFoundError } from '../errors.js';
import { parseBundleKey } from '../features/bundle.js';
import { requireLexeme } from '../lexicon/lexicon.js';
import type { Attempt } from '../mastery/attempt.js';
import { attempt } from '../mastery/attempt.js';
import type { CreditRule } from '../mastery/credit.js';
import { DEFAULT_CREDIT, propagateCredit } from '../mastery/credit.js';
import type { Ladder } from '../mastery/ladder.js';
import type { LeechPolicy } from '../mastery/leech.js';
import { applyLeechPolicy } from '../mastery/leech.js';
import type { MasteryLedger } from '../mastery/ledger.js';
import { strengthFor, withStrength } from '../mastery/ledger.js';
import { recordCorrect, recordLapse, recordNearMiss } from '../mastery/strength.js';
import type { MatchResult } from '../ortho/compare.js';
import { compareForm, isCorrect } from '../ortho/compare.js';
import type { OrthoProfile } from '../ortho/tolerance.js';
import { resolveTolerance } from '../ortho/tolerance.js';
import { profileFor } from '../paradigm/book.js';
import type { Cell } from '../paradigm/cell.js';
import type { RuleSet } from '../paradigm/realise.js';
import { realise } from '../paradigm/realise.js';
import type { CivilDate } from '../time/civil.js';
import type { BurialSet } from './burial.js';
import { buryCell, twinCellsOf } from './burial.js';
import type { DrillContext } from './membership.js';

/**
 * One answer, all the way through. This is where the layers meet, and the order
 * they meet in is the whole behaviour of the desk:
 *
 * the revision in force decides the expected form and the twin set, the deck
 * and the cell decide the tolerance, the tolerance decides the verdict, the
 * verdict decides the schedule, the schedule and the twin set decide tomorrow's
 * queue, and the lapse count decides whether the cell survives at all.
 *
 * Nothing here reaches for the newest revision. Everything resolves on the day
 * of the answer.
 */
export interface ReviewRequest {
  readonly cell: Cell;
  readonly given: string;
  readonly on: CivilDate;
  readonly learnerId: string;
  readonly sessionId: string;
  readonly sequence: number;
  readonly deckKey: string;
  readonly deckTolerance?: string;
  readonly cellTolerance?: string;
  readonly assignmentTolerance?: string;
  readonly elapsedMs?: number;
}

export interface ReviewSettings {
  readonly ortho: OrthoProfile;
  readonly rules: RuleSet;
  readonly ladder: Ladder;
  readonly leech: LeechPolicy;
  readonly credit?: CreditRule;
  readonly buryTwins?: boolean;
}

export interface ReviewResult {
  readonly attempt: Attempt;
  readonly match: MatchResult;
  readonly ledger: MasteryLedger;
  readonly buried: BurialSet;
  readonly expected: string;
  readonly revisionId: string;
  readonly twinsCredited: number;
  readonly leechFired: boolean;
}

export function expectedFormFor(
  context: DrillContext,
  settings: ReviewSettings,
  cell: Cell,
  on: CivilDate,
): { readonly form: string; readonly revisionId: string } {
  const entry = requireLexeme(context.lexicon, cell.lexemeId);
  const resolved = profileFor(context.book, context.classes, entry.classKey, on);
  if (resolved === undefined) {
    throw new NotFoundError('profile', `${entry.classKey} on ${on}`);
  }
  const bundle = parseBundleKey(context.schema, cell.bundle);
  return {
    form: realise(settings.rules, entry, resolved.revision.profile, bundle).form,
    revisionId: resolved.revision.id,
  };
}

export function reviewAnswer(
  context: DrillContext,
  settings: ReviewSettings,
  ledger: MasteryLedger,
  buried: BurialSet,
  request: ReviewRequest,
): ReviewResult {
  const { form, revisionId } = expectedFormFor(context, settings, request.cell, request.on);

  const tolerance = resolveTolerance(settings.ortho, {
    ...(request.deckTolerance === undefined ? {} : { deckLevel: request.deckTolerance }),
    ...(request.cellTolerance === undefined ? {} : { cellLevel: request.cellTolerance }),
    ...(request.assignmentTolerance === undefined
      ? {}
      : { assignmentLevel: request.assignmentTolerance }),
  });

  const match = compareForm(form, request.given, tolerance.level);
  const before = strengthFor(ledger, request.cell.id);
  const after = isCorrect(match)
    ? recordCorrect(before, settings.ladder, request.on)
    : match.verdict === 'near'
      ? recordNearMiss(before, settings.ladder, request.on)
      : recordLapse(before, settings.ladder, request.on);

  let nextLedger = withStrength(ledger, after);

  const leech = applyLeechPolicy(after, settings.leech, settings.ladder, request.on);
  if (leech.outcome.fired && leech.strength !== after) {
    nextLedger = withStrength(nextLedger, leech.strength);
  }

  const twins = twinCellsOf(context, request.cell, request.on);
  let twinsCredited = 0;
  if (isCorrect(match) && twins.length > 0) {
    const credited = propagateCredit(
      nextLedger,
      settings.ladder,
      settings.credit ?? DEFAULT_CREDIT,
      twins,
      request.on,
    );
    nextLedger = credited.ledger;
    twinsCredited = credited.outcomes.filter((outcome) => outcome.moved).length;
  }

  const nextBuried =
    settings.buryTwins === false ? buried : buryCell(context, buried, request.cell, request.on);

  const recorded = attempt({
    learnerId: request.learnerId,
    sessionId: request.sessionId,
    cellId: request.cell.id,
    deckKey: request.deckKey,
    on: request.on,
    sequence: request.sequence,
    given: request.given,
    expected: form,
    verdict: match.verdict,
    toleranceLevel: tolerance.level.key,
    revisionId,
    ...(request.elapsedMs === undefined ? {} : { elapsedMs: request.elapsedMs }),
  });

  return {
    attempt: recorded,
    match,
    ledger: nextLedger,
    buried: nextBuried,
    expected: form,
    revisionId,
    twinsCredited,
    leechFired: leech.outcome.fired,
  };
}
