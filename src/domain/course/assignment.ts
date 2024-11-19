import { ProblemCollector } from '../errors.js';
import type { DrillPolicyPatch } from '../drill/policy.js';
import type { Selector } from '../features/selector.js';
import { EMPTY_SELECTOR, formatSelector, parseSelector } from '../features/selector.js';
import type { FeatureSchema } from '../features/schema.js';
import type { Id } from '../identifiers.js';
import { asSlug, derivedId } from '../identifiers.js';
import { requireText } from '../text/normalise.js';
import type { CivilDate } from '../time/civil.js';
import { compareCivil } from '../time/civil.js';
import type { DayRange } from '../time/window.js';
import { dayRange, rangeContains, rangesOverlap } from '../time/window.js';
import type { Course } from './course.js';
import { teaches } from './course.js';

/**
 * A piece of work with a deadline. An assignment narrows one of the course
 * decks and states what counts as done, and it is the only place in the model
 * where a target exists at all: the drill layer has no notion of finishing.
 *
 * Two targets are supported because departments ask for both. A mastery target
 * says how many cells must reach the top of the ladder, and an effort target
 * says how many days the learner must have practised. Neither implies the
 * other, and an assignment may set both.
 */
export interface AssignmentTarget {
  readonly masteredCells: number | undefined;
  readonly practiceDays: number | undefined;
}

export interface Assignment {
  readonly id: Id<'assignment'>;
  readonly courseKey: string;
  readonly key: string;
  readonly title: string;
  readonly deckKey: string;
  readonly narrowing: Selector;
  readonly window: DayRange;
  readonly target: AssignmentTarget;
  readonly toleranceLevel: string | undefined;
  readonly policyPatch: DrillPolicyPatch;
}

export interface AssignmentInput {
  readonly key: string;
  readonly title?: string;
  readonly deckKey: string;
  readonly narrowing?: string;
  readonly from: CivilDate;
  readonly to: CivilDate;
  readonly masteredCells?: number;
  readonly practiceDays?: number;
  readonly toleranceLevel?: string;
  readonly policyPatch?: DrillPolicyPatch;
}

export function assignmentIdFor(courseKey: string, key: string): Id<'assignment'> {
  return derivedId('assignment', courseKey, key);
}

export function assignment(
  schema: FeatureSchema,
  entry: Course,
  input: AssignmentInput,
): Assignment {
  const problems = new ProblemCollector();
  const key = asSlug('assignment.key', input.key);
  const deckKey = asSlug('assignment.deckKey', input.deckKey);

  if (!teaches(entry, deckKey)) {
    problems.add('deckKey', `is not a deck of ${entry.key}: ${deckKey}`);
  }

  const window = problems.absorbFlat('window', () => dayRange(input.from, input.to));
  if (window !== undefined && !rangesOverlap(window, entry.term)) {
    problems.add('window', `falls outside the term ${entry.term.from} to ${entry.term.to}`);
  }

  const narrowing =
    problems.absorbFlat('narrowing', () =>
      parseSelector(schema, input.narrowing ?? '', 'narrowing'),
    ) ?? EMPTY_SELECTOR;

  for (const [path, value] of [
    ['masteredCells', input.masteredCells],
    ['practiceDays', input.practiceDays],
  ] as const) {
    if (value === undefined) {
      continue;
    }
    if (!Number.isInteger(value) || value < 1) {
      problems.add(path, `must be a whole number above zero: ${String(value)}`);
    }
  }

  if (input.masteredCells === undefined && input.practiceDays === undefined) {
    problems.add('target', 'must set at least one of masteredCells and practiceDays');
  }

  problems.throwIfAny(`assignment ${key} is not usable`);

  return {
    id: assignmentIdFor(entry.key, key),
    courseKey: entry.key,
    key,
    title: input.title === undefined ? key : requireText('assignment.title', input.title, 160),
    deckKey,
    narrowing,
    window: window!,
    target: { masteredCells: input.masteredCells, practiceDays: input.practiceDays },
    toleranceLevel:
      input.toleranceLevel === undefined
        ? undefined
        : asSlug('assignment.toleranceLevel', input.toleranceLevel),
    policyPatch: input.policyPatch ?? {},
  };
}

export function isOpenOn(entry: Assignment, on: CivilDate): boolean {
  return rangeContains(entry.window, on);
}

export function isOverdueOn(entry: Assignment, on: CivilDate): boolean {
  return compareCivil(on, entry.window.to) > 0;
}

export function hasStartedOn(entry: Assignment, on: CivilDate): boolean {
  return compareCivil(on, entry.window.from) >= 0;
}

export function describeTarget(target: AssignmentTarget): string {
  const parts: string[] = [];
  if (target.masteredCells !== undefined) {
    parts.push(`${target.masteredCells} cells to the top of the ladder`);
  }
  if (target.practiceDays !== undefined) {
    parts.push(`${target.practiceDays} days of practice`);
  }
  return parts.join(' and ');
}

export function describeAssignment(entry: Assignment): string {
  const narrowed =
    entry.narrowing.clauses.length === 0
      ? entry.deckKey
      : `${entry.deckKey} narrowed to ${formatSelector(entry.narrowing)}`;
  return `${entry.title}: ${narrowed}, ${describeTarget(entry.target)}, due ${entry.window.to}`;
}
