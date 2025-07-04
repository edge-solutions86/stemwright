import { ValidationError } from '../domain/errors.js';
import type { CivilDate } from '../domain/time/civil.js';
import { today } from '../domain/time/civil.js';
import type { DayRange } from '../domain/time/window.js';
import { rangeContains } from '../domain/time/window.js';
import type { DomainEvent, EventBus, Subscription } from './events.js';

/**
 * Who changed what, kept separately from the journal.
 *
 * The journal answers "what is the state" and this answers "who is responsible
 * for it", and those are different questions with different readers and
 * different retention. An institution deletes learner answers at the end of a
 * degree and keeps the record of who edited a paradigm for as long as anybody
 * might ask why a mark was what it was.
 *
 * Only editorial events are recorded. An attempt is a learner doing what they
 * were asked, and putting it here would bury the four entries a term that
 * anybody actually reads.
 */
export interface AuditEntry {
  readonly at: string;
  readonly on: CivilDate;
  readonly actorId: string;
  readonly action: string;
  readonly subject: string;
  readonly detail: string;
}

const AUDITED = new Set(['lexeme.added', 'profile.published']);

export function isAudited(event: DomainEvent): boolean {
  return AUDITED.has(event.kind);
}

export function toAuditEntry(event: DomainEvent): AuditEntry | undefined {
  switch (event.kind) {
    case 'lexeme.added':
      return {
        at: event.at.toISOString(),
        on: today(event.at),
        actorId: event.actorId,
        action: 'lexeme.added',
        subject: event.lexemeId,
        detail: `added to ${event.language}`,
      };
    case 'profile.published':
      return {
        at: event.at.toISOString(),
        on: today(event.at),
        actorId: event.actorId,
        action: 'profile.published',
        subject: event.profileKey,
        detail: `revision ${event.revisionId} takes effect ${event.effectiveFrom}`,
      };
    default:
      return undefined;
  }
}

export interface AuditLog {
  readonly entries: readonly AuditEntry[];
  record(event: DomainEvent): AuditEntry | undefined;
  byActor(actorId: string): AuditEntry[];
  bySubject(subject: string): AuditEntry[];
  inRange(range: DayRange): AuditEntry[];
  clear(): void;
}

export function createAuditLog(seed: readonly AuditEntry[] = []): AuditLog {
  const entries: AuditEntry[] = [...seed];

  return {
    get entries() {
      return entries;
    },
    record(event) {
      const entry = toAuditEntry(event);
      if (entry !== undefined) {
        entries.push(entry);
      }
      return entry;
    },
    byActor: (actorId) => entries.filter((entry) => entry.actorId === actorId),
    bySubject: (subject) => entries.filter((entry) => entry.subject === subject),
    inRange: (range) => entries.filter((entry) => rangeContains(range, entry.on)),
    clear() {
      entries.length = 0;
    },
  };
}

/** Attaches the log to a bus and hands back the way to detach it. */
export function auditFrom(bus: EventBus, log: AuditLog): Subscription {
  return bus.onAny((event) => {
    log.record(event);
  });
}

export function formatAudit(entry: AuditEntry): string {
  return `${entry.at} ${entry.actorId} ${entry.action} ${entry.subject}: ${entry.detail}`;
}

export function requireActor(entry: AuditEntry): string {
  if (entry.actorId === '') {
    throw ValidationError.field('actorId', 'is blank on an audit entry');
  }
  return entry.actorId;
}
