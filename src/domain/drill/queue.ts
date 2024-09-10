import type { FeatureSchema } from '../features/schema.js';
import type { Cell } from '../paradigm/cell.js';
import type { MasteryLedger } from '../mastery/ledger.js';
import { dueCells, newCells, strengthFor } from '../mastery/ledger.js';
import { daysOverdue } from '../mastery/strength.js';
import type { CivilDate } from '../time/civil.js';
import { weekday } from '../time/civil.js';
import type { BurialSet } from './burial.js';
import { NOTHING_BURIED, isBuried, respectsSpacing } from './burial.js';
import type { Membership } from './membership.js';
import type { DrillContext } from './membership.js';
import type { DrillPolicy } from './policy.js';
import { policyAllowsDay } from './policy.js';

/**
 * Building the day's questions. Everything before this module decided what
 * could be asked; this one decides what is asked and in what order, and it has
 * to say why for each cell it left out, because "the queue is short today" is a
 * question every learner asks and a system that cannot answer it gets blamed
 * for losing their work.
 */
export type ExclusionReason =
  | 'rest-day'
  | 'buried'
  | 'suspended'
  | 'lexeme-cap'
  | 'row-spacing'
  | 'review-budget'
  | 'new-budget'
  | 'not-due';

export interface QueueEntry {
  readonly cell: Cell;
  readonly kind: 'review' | 'new';
  readonly overdueDays: number;
}

export interface Queue {
  readonly on: CivilDate;
  readonly deckKey: string;
  readonly entries: readonly QueueEntry[];
  readonly excluded: ReadonlyMap<string, ExclusionReason>;
}

export interface QueueOptions {
  /** Which dimension the spacing rule treats as running across a row. */
  readonly rowAcross?: string;
  readonly buried?: BurialSet;
}

function rowDimensionOf(schema: FeatureSchema, options: QueueOptions): string {
  if (options.rowAcross !== undefined) {
    return options.rowAcross;
  }
  const last = schema.dimensions[schema.dimensions.length - 1];
  return last?.key ?? '';
}

interface Attempt {
  readonly cell: Cell;
  readonly kind: 'review' | 'new';
  readonly overdueDays: number;
}

/**
 * Reviews are placed before new cells, and within reviews the most overdue go
 * first. New cells are only introduced once the review budget has had its
 * chance, which is the whole point of having two budgets: a learner who has
 * fallen behind should not be handed more to fall behind on.
 */
export function assembleQueue(
  context: DrillContext,
  membership: Membership,
  policy: DrillPolicy,
  ledger: MasteryLedger,
  on: CivilDate,
  options: QueueOptions = {},
): Queue {
  const excluded = new Map<string, ExclusionReason>();
  const byId = new Map<string, Cell>(membership.cells.map((cell) => [cell.id, cell]));

  if (!policyAllowsDay(policy, weekday(on))) {
    for (const cell of membership.cells) {
      excluded.set(cell.id, 'rest-day');
    }
    return { on, deckKey: membership.deckKey, entries: [], excluded };
  }

  const cellIds = membership.cells.map((cell) => cell.id);
  const reviews: Attempt[] = dueCells(ledger, cellIds, on).map((strength) => ({
    cell: byId.get(strength.cellId)!,
    kind: 'review' as const,
    overdueDays: daysOverdue(strength, on),
  }));
  const fresh: Attempt[] = newCells(ledger, cellIds).map((strength) => ({
    cell: byId.get(strength.cellId)!,
    kind: 'new' as const,
    overdueDays: 0,
  }));

  for (const cell of membership.cells) {
    const strength = strengthFor(ledger, cell.id);
    if (strength.suspended) {
      excluded.set(cell.id, 'suspended');
    }
  }

  const rowAcross = rowDimensionOf(context.schema, options);
  const buried = options.buried ?? NOTHING_BURIED;
  const entries: QueueEntry[] = [];
  const asked: Cell[] = [];
  const perLexeme = new Map<string, number>();
  let reviewsTaken = 0;
  let newTaken = 0;

  const consider = (attempt: Attempt): void => {
    const { cell, kind } = attempt;
    if (isBuried(buried, cell.id)) {
      excluded.set(cell.id, 'buried');
      return;
    }
    if (kind === 'review' && reviewsTaken >= policy.reviewsPerDay) {
      excluded.set(cell.id, 'review-budget');
      return;
    }
    if (kind === 'new' && newTaken >= policy.newPerDay) {
      excluded.set(cell.id, 'new-budget');
      return;
    }
    if ((perLexeme.get(cell.lexemeId) ?? 0) >= policy.perLexemeCap) {
      excluded.set(cell.id, 'lexeme-cap');
      return;
    }
    if (!respectsSpacing(context.schema, asked, cell, rowAcross, policy.rowSpacing)) {
      excluded.set(cell.id, 'row-spacing');
      return;
    }

    entries.push({ cell, kind, overdueDays: attempt.overdueDays });
    asked.push(cell);
    perLexeme.set(cell.lexemeId, (perLexeme.get(cell.lexemeId) ?? 0) + 1);
    if (kind === 'review') {
      reviewsTaken += 1;
    } else {
      newTaken += 1;
    }
  };

  for (const attempt of [...reviews, ...fresh]) {
    if (excluded.has(attempt.cell.id)) {
      continue;
    }
    consider(attempt);
  }

  for (const cell of membership.cells) {
    if (!excluded.has(cell.id) && !entries.some((entry) => entry.cell.id === cell.id)) {
      excluded.set(cell.id, 'not-due');
    }
  }

  return { on, deckKey: membership.deckKey, entries, excluded };
}

export function queueLength(queue: Queue): number {
  return queue.entries.length;
}

export function reviewCount(queue: Queue): number {
  return queue.entries.filter((entry) => entry.kind === 'review').length;
}

export function newCount(queue: Queue): number {
  return queue.entries.filter((entry) => entry.kind === 'new').length;
}

export function excludedFor(queue: Queue, reason: ExclusionReason): string[] {
  return [...queue.excluded.entries()]
    .filter(([, value]) => value === reason)
    .map(([cellId]) => cellId)
    .sort();
}

export function whyExcluded(queue: Queue, cellId: string): ExclusionReason | undefined {
  return queue.excluded.get(cellId);
}

export function queueCells(queue: Queue): Cell[] {
  return queue.entries.map((entry) => entry.cell);
}
