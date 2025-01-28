import { describe, expect, it, vi } from 'vitest';
import type { DomainEvent } from '../../src/app/events.js';
import { createEventBus, describeEvent, recordingBus } from '../../src/app/events.js';
import { attempt } from '../../src/domain/mastery/attempt.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';

const d = (value: string) => parseCivilDate(value);
const AT = new Date('2025-03-03T09:00:00Z');

const lexemeAdded: DomainEvent = {
  kind: 'lexeme.added',
  at: AT,
  actorId: 'lrn_teacher',
  lexemeId: 'lex_aaaaaa',
  language: 'latin',
};

const suspended: DomainEvent = {
  kind: 'cell.suspended',
  at: AT,
  actorId: 'system',
  learnerId: 'lrn_one',
  cellId: 'cell_aaaaaa',
  lapses: 6,
};

const recorded: DomainEvent = {
  kind: 'attempt.recorded',
  at: AT,
  actorId: 'lrn_one',
  attempt: attempt({
    learnerId: 'lrn_one',
    sessionId: 'sess_a',
    cellId: 'cell_aaaaaa',
    deckKey: 'all',
    on: d('2025-03-03'),
    sequence: 1,
    given: 'puellae',
    expected: 'puellae',
    verdict: 'exact',
    toleranceLevel: 'standard',
    revisionId: 'rev_a',
  }),
};

describe('createEventBus', () => {
  it('delivers to a handler of the right kind', () => {
    const bus = createEventBus();
    const handler = vi.fn();
    bus.on('lexeme.added', handler);
    bus.publish(lexemeAdded);
    expect(handler).toHaveBeenCalledWith(lexemeAdded);
  });

  it('does not deliver to a handler of another kind', () => {
    const bus = createEventBus();
    const handler = vi.fn();
    bus.on('cell.suspended', handler);
    bus.publish(lexemeAdded);
    expect(handler).not.toHaveBeenCalled();
  });

  it('delivers everything to an onAny handler', () => {
    const bus = createEventBus();
    const handler = vi.fn();
    bus.onAny(handler);
    bus.publish(lexemeAdded);
    bus.publish(suspended);
    expect(handler).toHaveBeenCalledTimes(2);
  });

  it('delivers to several handlers of one kind', () => {
    const bus = createEventBus();
    const first = vi.fn();
    const second = vi.fn();
    bus.on('lexeme.added', first);
    bus.on('lexeme.added', second);
    bus.publish(lexemeAdded);
    expect(first).toHaveBeenCalledOnce();
    expect(second).toHaveBeenCalledOnce();
  });

  it('stops delivering once unsubscribed', () => {
    const bus = createEventBus();
    const handler = vi.fn();
    const subscription = bus.on('lexeme.added', handler);
    subscription.unsubscribe();
    bus.publish(lexemeAdded);
    expect(handler).not.toHaveBeenCalled();
  });

  it('tolerates unsubscribing twice', () => {
    const bus = createEventBus();
    const subscription = bus.onAny(vi.fn());
    subscription.unsubscribe();
    expect(() => subscription.unsubscribe()).not.toThrow();
  });

  it('publishes to nobody without complaining', () => {
    expect(createEventBus().publish(lexemeAdded)).toEqual([]);
  });
});

describe('a handler that throws', () => {
  it('does not stop the other handlers', () => {
    const bus = createEventBus();
    const second = vi.fn();
    bus.on('lexeme.added', () => {
      throw new Error('subscriber is broken');
    });
    bus.on('lexeme.added', second);
    bus.publish(lexemeAdded);
    expect(second).toHaveBeenCalledOnce();
  });

  it('comes back as a collected failure rather than a throw', () => {
    const bus = createEventBus();
    bus.on('lexeme.added', () => {
      throw new Error('subscriber is broken');
    });
    const failures = bus.publish(lexemeAdded);
    expect(failures).toHaveLength(1);
    expect(failures[0]?.message).toBe('subscriber is broken');
  });

  it('wraps a thrown non error', () => {
    const bus = createEventBus();
    bus.onAny(() => {
      // eslint-disable-next-line @typescript-eslint/only-throw-error
      throw 'a string';
    });
    expect(bus.publish(lexemeAdded)[0]?.message).toBe('a string');
  });
});

describe('recordingBus', () => {
  it('keeps everything it published', () => {
    const bus = recordingBus();
    bus.publish(lexemeAdded);
    bus.publish(suspended);
    expect(bus.events).toHaveLength(2);
  });

  it('filters by kind', () => {
    const bus = recordingBus();
    bus.publish(lexemeAdded);
    bus.publish(suspended);
    expect(bus.ofKind('cell.suspended')).toHaveLength(1);
    expect(bus.ofKind('cell.suspended')[0]?.cellId).toBe('cell_aaaaaa');
    expect(bus.ofKind('session.closed')).toEqual([]);
  });

  it('still delivers to subscribers', () => {
    const bus = recordingBus();
    const handler = vi.fn();
    bus.on('lexeme.added', handler);
    bus.publish(lexemeAdded);
    expect(handler).toHaveBeenCalledOnce();
  });

  it('clears what it recorded', () => {
    const bus = recordingBus();
    bus.publish(lexemeAdded);
    bus.clear();
    expect(bus.events).toEqual([]);
  });
});

describe('describeEvent', () => {
  it('reads each kind out', () => {
    expect(describeEvent(lexemeAdded)).toBe('lrn_teacher added lex_aaaaaa to latin');
    expect(describeEvent(suspended)).toBe('cell_aaaaaa was suspended for lrn_one after 6 lapses');
    expect(describeEvent(recorded)).toBe('lrn_one answered cell_aaaaaa exact');
  });

  it('reads the remaining kinds out', () => {
    expect(
      describeEvent({
        kind: 'profile.published',
        at: AT,
        actorId: 'lrn_teacher',
        profileKey: 'first-declension',
        revisionId: 'rev_a',
        effectiveFrom: d('2025-01-15'),
      }),
    ).toBe('lrn_teacher published first-declension from 2025-01-15');
    expect(
      describeEvent({
        kind: 'session.closed',
        at: AT,
        actorId: 'lrn_one',
        sessionId: 'sess_a',
        learnerId: 'lrn_one',
        answers: 12,
      }),
    ).toBe('lrn_one closed a session of 12 answers');
    expect(
      describeEvent({
        kind: 'assignment.met',
        at: AT,
        actorId: 'lrn_one',
        assignmentKey: 'week-3',
        learnerId: 'lrn_one',
        on: d('2025-03-03'),
      }),
    ).toBe('lrn_one met week-3 on 2025-03-03');
  });
});
