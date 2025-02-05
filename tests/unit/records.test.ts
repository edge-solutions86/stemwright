import { describe, expect, it } from 'vitest';
import { attempt } from '../../src/domain/mastery/attempt.js';
import type { AttemptInput } from '../../src/domain/mastery/attempt.js';
import { ladder } from '../../src/domain/mastery/ladder.js';
import { buildLedger, strengthFor } from '../../src/domain/mastery/ledger.js';
import {
  newStrength,
  recordCorrect,
  recordLapse,
  suspend,
} from '../../src/domain/mastery/strength.js';
import { ValidationError } from '../../src/domain/errors.js';
import {
  decodeAttempt,
  decodeLedger,
  decodeStrength,
  encodeAttempt,
  encodeLedger,
  encodeStrength,
} from '../../src/infra/records.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';

const d = (value: string) => parseCivilDate(value);
const DAY = d('2025-03-03');
const flat = ladder({ key: 'flat', rungs: [1, 3, 7], spread: 0 });

const base: AttemptInput = {
  learnerId: 'lrn_one',
  sessionId: 'sess_aaaaaa',
  cellId: 'cell_aaaaaa',
  deckKey: 'first-year',
  on: DAY,
  sequence: 1,
  given: 'puellae',
  expected: 'puellae',
  verdict: 'exact',
  toleranceLevel: 'standard',
  revisionId: 'rev_aaaaaa',
};

describe('attempt codec', () => {
  it('round trips every field', () => {
    const entry = attempt({ ...base, elapsedMs: 900 });
    expect(decodeAttempt(encodeAttempt(entry))).toEqual(entry);
  });

  it('leaves the elapsed time out when there is none', () => {
    const encoded = encodeAttempt(attempt(base)) as Record<string, unknown>;
    expect('elapsedMs' in encoded).toBe(false);
    expect(decodeAttempt(encoded).elapsedMs).toBeUndefined();
  });

  it('refuses a row that is not an object', () => {
    expect(() => decodeAttempt('nope')).toThrow(/attempt is not an object/);
  });

  it('refuses a verdict that is not one of the four', () => {
    const encoded = { ...(encodeAttempt(attempt(base)) as object), verdict: 'partial' };
    expect(() => decodeAttempt(encoded)).toThrow(/is not a verdict: partial/);
  });

  it('refuses a row missing a field', () => {
    const encoded = { ...(encodeAttempt(attempt(base)) as Record<string, unknown>) };
    delete encoded['cellId'];
    expect(() => decodeAttempt(encoded)).toThrow(/attempt.cellId is not a string/);
  });

  it('puts the row through the domain constructor', () => {
    const encoded = { ...(encodeAttempt(attempt(base)) as object), sequence: 0 };
    expect(() => decodeAttempt(encoded)).toThrow(ValidationError);
  });

  it('names the path it was given', () => {
    expect(() => decodeAttempt('nope', 'journal.7')).toThrow(/journal.7 is not an object/);
  });
});

describe('strength codec', () => {
  const seen = recordCorrect(recordCorrect(newStrength('cell_a'), flat, DAY), flat, DAY);

  it('round trips a seen cell', () => {
    expect(decodeStrength(encodeStrength(seen))).toEqual(seen);
  });

  it('round trips a cell nobody has answered', () => {
    const fresh = newStrength('cell_a');
    expect(decodeStrength(encodeStrength(fresh))).toEqual(fresh);
  });

  it('round trips a suspended cell', () => {
    const leech = suspend(recordLapse(seen, flat, DAY));
    expect(decodeStrength(encodeStrength(leech))).toEqual(leech);
  });

  it('leaves the dates out when they are absent', () => {
    const encoded = encodeStrength(newStrength('cell_a')) as Record<string, unknown>;
    expect('dueOn' in encoded).toBe(false);
    expect('lastSeenOn' in encoded).toBe(false);
  });

  it('refuses a negative count', () => {
    const encoded = { ...(encodeStrength(seen) as object), lapses: -1 };
    expect(() => decodeStrength(encoded)).toThrow(/lapses must be a whole number/);
  });

  it('refuses more correct answers than sightings', () => {
    const encoded = { ...(encodeStrength(seen) as object), correct: 9 };
    expect(() => decodeStrength(encoded)).toThrow(/is larger than the number of sightings/);
  });

  it('refuses a fractional rung', () => {
    const encoded = { ...(encodeStrength(seen) as object), rung: 1.5 };
    expect(() => decodeStrength(encoded)).toThrow(ValidationError);
  });
});

describe('ledger codec', () => {
  const ledger = buildLedger('lrn_one', [
    recordCorrect(newStrength('cell_b'), flat, DAY),
    newStrength('cell_a'),
  ]);

  it('round trips every cell', () => {
    const decoded = decodeLedger(encodeLedger(ledger));
    expect(decoded.learnerId).toBe('lrn_one');
    expect(decoded.byCell.size).toBe(2);
    expect(strengthFor(decoded, 'cell_b').seen).toBe(1);
  });

  it('writes the cells in key order so two saves compare equal', () => {
    const encoded = encodeLedger(ledger) as { cells: { cellId: string }[] };
    expect(encoded.cells.map((row) => row.cellId)).toEqual(['cell_a', 'cell_b']);
  });

  it('round trips an empty ledger', () => {
    const empty = buildLedger('lrn_one', []);
    expect(decodeLedger(encodeLedger(empty)).byCell.size).toBe(0);
  });

  it('refuses a cells field that is not an array', () => {
    expect(() => decodeLedger({ learnerId: 'lrn_one', cells: 'nope' })).toThrow(
      /ledger.cells is not an array/,
    );
  });

  it('names the index of the bad cell', () => {
    expect(() => decodeLedger({ learnerId: 'lrn_one', cells: [{}] })).toThrow(
      /ledger.cells.0.seen is not a finite number/,
    );
  });
});
