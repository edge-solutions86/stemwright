import { describe, expect, it } from 'vitest';
import type { DomainEvent } from '../../src/app/events.js';
import { createEventBus } from '../../src/app/events.js';
import {
  auditFrom,
  createAuditLog,
  formatAudit,
  isAudited,
  requireActor,
  toAuditEntry,
} from '../../src/app/audit.js';
import { ValidationError } from '../../src/domain/errors.js';
import { attempt } from '../../src/domain/mastery/attempt.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { dayRange } from '../../src/domain/time/window.js';

const AT = new Date('2025-03-03T09:00:00Z');
const d = (value: string) => parseCivilDate(value);

const added: DomainEvent = {
  kind: 'lexeme.added',
  at: AT,
  actorId: 'lrn_teacher',
  lexemeId: 'lex_aaaaaa',
  language: 'latin',
};

const published: DomainEvent = {
  kind: 'profile.published',
  at: AT,
  actorId: 'lrn_teacher',
  profileKey: 'first-declension',
  revisionId: 'rev_aaaaaa',
  effectiveFrom: d('2025-04-01'),
};

const answered: DomainEvent = {
  kind: 'attempt.recorded',
  at: AT,
  actorId: 'lrn_one',
  attempt: attempt({
    learnerId: 'lrn_one',
    sessionId: 'sess_a',
    cellId: 'cell_a',
    deckKey: 'all',
    on: d('2025-03-03'),
    sequence: 1,
    given: 'x',
    expected: 'x',
    verdict: 'exact',
    toleranceLevel: 'standard',
    revisionId: 'rev_a',
  }),
};

describe('isAudited', () => {
  it('records editorial events and nothing else', () => {
    expect(isAudited(added)).toBe(true);
    expect(isAudited(published)).toBe(true);
    expect(isAudited(answered)).toBe(false);
  });
});

describe('toAuditEntry', () => {
  it('turns a lexeme addition into an entry', () => {
    expect(toAuditEntry(added)).toEqual({
      at: '2025-03-03T09:00:00.000Z',
      on: '2025-03-03',
      actorId: 'lrn_teacher',
      action: 'lexeme.added',
      subject: 'lex_aaaaaa',
      detail: 'added to latin',
    });
  });

  it('names the revision and the day it takes effect', () => {
    expect(toAuditEntry(published)?.detail).toBe('revision rev_aaaaaa takes effect 2025-04-01');
  });

  it('gives nothing for an event it does not audit', () => {
    expect(toAuditEntry(answered)).toBeUndefined();
  });
});

describe('the log', () => {
  it('records what it should and skips what it should not', () => {
    const log = createAuditLog();
    log.record(added);
    log.record(answered);
    expect(log.entries).toHaveLength(1);
  });

  it('hands back the entry it recorded', () => {
    expect(createAuditLog().record(added)?.subject).toBe('lex_aaaaaa');
    expect(createAuditLog().record(answered)).toBeUndefined();
  });

  it('filters by actor and by subject', () => {
    const log = createAuditLog();
    log.record(added);
    log.record(published);
    expect(log.byActor('lrn_teacher')).toHaveLength(2);
    expect(log.byActor('lrn_one')).toEqual([]);
    expect(log.bySubject('first-declension')).toHaveLength(1);
  });

  it('filters by day range', () => {
    const log = createAuditLog();
    log.record(added);
    expect(log.inRange(dayRange(d('2025-03-01'), d('2025-03-05')))).toHaveLength(1);
    expect(log.inRange(dayRange(d('2025-04-01'), d('2025-04-05')))).toEqual([]);
  });

  it('clears what it holds', () => {
    const log = createAuditLog();
    log.record(added);
    log.clear();
    expect(log.entries).toEqual([]);
  });

  it('starts from the entries it was seeded with', () => {
    expect(createAuditLog([toAuditEntry(added)!]).entries).toHaveLength(1);
  });
});

describe('auditFrom', () => {
  it('records events published on the bus', () => {
    const bus = createEventBus();
    const log = createAuditLog();
    auditFrom(bus, log);
    bus.publish(added);
    bus.publish(answered);
    expect(log.entries).toHaveLength(1);
  });

  it('stops recording once detached', () => {
    const bus = createEventBus();
    const log = createAuditLog();
    auditFrom(bus, log).unsubscribe();
    bus.publish(added);
    expect(log.entries).toEqual([]);
  });
});

describe('formatAudit and requireActor', () => {
  it('reads an entry out', () => {
    expect(formatAudit(toAuditEntry(added)!)).toBe(
      '2025-03-03T09:00:00.000Z lrn_teacher lexeme.added lex_aaaaaa: added to latin',
    );
  });

  it('refuses an entry with no actor', () => {
    const entry = { ...toAuditEntry(added)!, actorId: '' };
    expect(() => requireActor(entry)).toThrow(ValidationError);
  });
});
