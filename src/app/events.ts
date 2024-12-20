import type { Attempt } from '../domain/mastery/attempt.js';
import type { CivilDate } from '../domain/time/civil.js';

/**
 * Domain events, published after a write has landed rather than instead of one.
 * Nothing in the domain depends on the bus: a subscriber that throws must not
 * be able to undo a review that was already recorded, so publication is best
 * effort and failures are collected rather than propagated.
 */
export interface EventBase {
  readonly at: Date;
  readonly actorId: string;
}

export interface LexemeAdded extends EventBase {
  readonly kind: 'lexeme.added';
  readonly lexemeId: string;
  readonly language: string;
}

export interface ProfilePublished extends EventBase {
  readonly kind: 'profile.published';
  readonly profileKey: string;
  readonly revisionId: string;
  readonly effectiveFrom: CivilDate;
}

export interface AttemptRecorded extends EventBase {
  readonly kind: 'attempt.recorded';
  readonly attempt: Attempt;
}

export interface CellSuspended extends EventBase {
  readonly kind: 'cell.suspended';
  readonly learnerId: string;
  readonly cellId: string;
  readonly lapses: number;
}

export interface SessionClosed extends EventBase {
  readonly kind: 'session.closed';
  readonly sessionId: string;
  readonly learnerId: string;
  readonly answers: number;
}

export interface AssignmentMet extends EventBase {
  readonly kind: 'assignment.met';
  readonly assignmentKey: string;
  readonly learnerId: string;
  readonly on: CivilDate;
}

export type DomainEvent =
  | LexemeAdded
  | ProfilePublished
  | AttemptRecorded
  | CellSuspended
  | SessionClosed
  | AssignmentMet;

export type EventKind = DomainEvent['kind'];

export type Handler<E extends DomainEvent = DomainEvent> = (event: E) => void;

export interface Subscription {
  unsubscribe(): void;
}

export interface EventBus {
  publish(event: DomainEvent): readonly Error[];
  on<K extends EventKind>(
    kind: K,
    handler: Handler<Extract<DomainEvent, { kind: K }>>,
  ): Subscription;
  onAny(handler: Handler): Subscription;
}

export function createEventBus(): EventBus {
  const byKind = new Map<EventKind, Set<Handler>>();
  const anyHandlers = new Set<Handler>();

  return {
    publish(event) {
      const failures: Error[] = [];
      const handlers = [...(byKind.get(event.kind) ?? []), ...anyHandlers];
      for (const handler of handlers) {
        try {
          handler(event);
        } catch (error) {
          failures.push(error instanceof Error ? error : new Error(String(error)));
        }
      }
      return failures;
    },
    on(kind, handler) {
      const existing = byKind.get(kind) ?? new Set<Handler>();
      existing.add(handler as Handler);
      byKind.set(kind, existing);
      return {
        unsubscribe() {
          existing.delete(handler as Handler);
        },
      };
    },
    onAny(handler) {
      anyHandlers.add(handler);
      return {
        unsubscribe() {
          anyHandlers.delete(handler);
        },
      };
    },
  };
}

export interface RecordingBus extends EventBus {
  readonly events: readonly DomainEvent[];
  ofKind<K extends EventKind>(kind: K): Extract<DomainEvent, { kind: K }>[];
  clear(): void;
}

/** A bus that keeps everything it saw, which is what the service tests assert on. */
export function recordingBus(): RecordingBus {
  const inner = createEventBus();
  const seen: DomainEvent[] = [];

  return {
    publish(event) {
      seen.push(event);
      return inner.publish(event);
    },
    on: inner.on.bind(inner),
    onAny: inner.onAny.bind(inner),
    get events() {
      return seen;
    },
    ofKind<K extends EventKind>(kind: K) {
      return seen.filter(
        (event): event is Extract<DomainEvent, { kind: K }> => event.kind === kind,
      );
    },
    clear() {
      seen.length = 0;
    },
  };
}

export function describeEvent(event: DomainEvent): string {
  switch (event.kind) {
    case 'lexeme.added':
      return `${event.actorId} added ${event.lexemeId} to ${event.language}`;
    case 'profile.published':
      return `${event.actorId} published ${event.profileKey} from ${event.effectiveFrom}`;
    case 'attempt.recorded':
      return `${event.attempt.learnerId} answered ${event.attempt.cellId} ${event.attempt.verdict}`;
    case 'cell.suspended':
      return `${event.cellId} was suspended for ${event.learnerId} after ${event.lapses} lapses`;
    case 'session.closed':
      return `${event.learnerId} closed a session of ${event.answers} answers`;
    case 'assignment.met':
      return `${event.learnerId} met ${event.assignmentKey} on ${event.on}`;
  }
}
