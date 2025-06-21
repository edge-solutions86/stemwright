import { indexBy } from '../collections.js';
import { bundleKey } from '../features/bundle.js';
import type { Lexeme } from './lexeme.js';
import { compareLexemes } from './lexeme.js';
import { stemFor, stemRoles } from './stem.js';

/**
 * Comparing two versions of a lexicon, which is what an editor wants before
 * accepting an import and what a reviewer wants before approving one.
 *
 * A change is reported at the field that changed rather than as a whole record,
 * because the interesting question is never "did this lexeme change" but "did
 * the class change", which moves every learner's schedule for it, or "did a
 * gloss change", which moves nothing.
 */
export type ChangeKind = 'added' | 'removed' | 'changed';

export interface FieldChange {
  readonly field: string;
  readonly before: string;
  readonly after: string;
}

export interface LexemeChange {
  readonly kind: ChangeKind;
  readonly lexemeId: string;
  readonly lemma: string;
  readonly fields: readonly FieldChange[];
}

function fieldsOf(entry: Lexeme): Map<string, string> {
  const fields = new Map<string, string>([
    ['lemma', entry.lemma],
    ['class', entry.classKey],
    ['inherent', bundleKey(entry.inherent)],
    ['glosses', entry.glosses.join('; ')],
    ['tags', [...entry.tags].sort().join('; ')],
    ['note', entry.note ?? ''],
  ]);
  for (const role of stemRoles(entry.stems)) {
    fields.set(`stem.${role}`, stemFor(entry.stems, role)?.form ?? '');
  }
  return fields;
}

function changesBetween(before: Lexeme, after: Lexeme): FieldChange[] {
  const left = fieldsOf(before);
  const right = fieldsOf(after);
  const names = [...new Set([...left.keys(), ...right.keys()])].sort();

  return names
    .filter((field) => (left.get(field) ?? '') !== (right.get(field) ?? ''))
    .map((field) => ({
      field,
      before: left.get(field) ?? '',
      after: right.get(field) ?? '',
    }));
}

export function diffLexicons(before: readonly Lexeme[], after: readonly Lexeme[]): LexemeChange[] {
  const left = indexBy(before, (entry) => entry.id);
  const right = indexBy(after, (entry) => entry.id);
  const changes: LexemeChange[] = [];

  for (const entry of [...after].sort(compareLexemes)) {
    const previous = left.get(entry.id);
    if (previous === undefined) {
      changes.push({ kind: 'added', lexemeId: entry.id, lemma: entry.lemma, fields: [] });
      continue;
    }
    const fields = changesBetween(previous, entry);
    if (fields.length > 0) {
      changes.push({ kind: 'changed', lexemeId: entry.id, lemma: entry.lemma, fields });
    }
  }

  for (const entry of [...before].sort(compareLexemes)) {
    if (!right.has(entry.id)) {
      changes.push({ kind: 'removed', lexemeId: entry.id, lemma: entry.lemma, fields: [] });
    }
  }

  return changes;
}

export function changesOfKind(changes: readonly LexemeChange[], kind: ChangeKind): LexemeChange[] {
  return changes.filter((change) => change.kind === kind);
}

/** Changes that move a learner's schedule rather than only their reading. */
export const SCHEDULE_FIELDS = ['class', 'inherent'] as const;

export function schedulingChanges(changes: readonly LexemeChange[]): LexemeChange[] {
  return changes.filter(
    (change) =>
      change.kind !== 'changed' ||
      change.fields.some((field) => (SCHEDULE_FIELDS as readonly string[]).includes(field.field)),
  );
}

export function describeChange(change: LexemeChange): string {
  if (change.kind === 'added') {
    return `added ${change.lemma}`;
  }
  if (change.kind === 'removed') {
    return `removed ${change.lemma}`;
  }
  const fields = change.fields
    .map(
      (field) => `${field.field} from ${field.before || 'nothing'} to ${field.after || 'nothing'}`,
    )
    .join(', ');
  return `changed ${change.lemma}: ${fields}`;
}

export function summariseDiff(changes: readonly LexemeChange[]): string {
  const added = changesOfKind(changes, 'added').length;
  const removed = changesOfKind(changes, 'removed').length;
  const changed = changesOfKind(changes, 'changed').length;
  if (added + removed + changed === 0) {
    return 'nothing changed';
  }
  return `${added} added, ${removed} removed, ${changed} changed, ${schedulingChanges(changes).length} of which move a schedule`;
}
