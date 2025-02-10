import { describe, expect, it } from 'vitest';
import { ConflictError, IntegrityError, ValidationError } from '../../src/domain/errors.js';
import {
  decodeEntry,
  decodeJournal,
  encodeEntry,
  encodeJournal,
  entriesOfKind,
  isJournalKind,
  memoryJournal,
  payloadObject,
} from '../../src/infra/journal.js';

const AT = new Date('2025-03-03T09:00:00Z');

const seeded = async () => {
  const journal = memoryJournal();
  await journal.append(
    [
      { kind: 'lexeme.put', payload: { id: 'lex_a' } },
      { kind: 'attempt.appended', payload: { id: 'att_a' } },
    ],
    AT,
  );
  return journal;
};

describe('isJournalKind', () => {
  it('accepts a declared kind and nothing else', () => {
    expect(isJournalKind('lexeme.put')).toBe(true);
    expect(isJournalKind('lexeme.exploded')).toBe(false);
  });
});

describe('append', () => {
  it('assigns sequence numbers from one', async () => {
    const journal = await seeded();
    expect((await journal.read()).map((entry) => entry.seq)).toEqual([1, 2]);
  });

  it('carries on numbering across calls', async () => {
    const journal = await seeded();
    const written = await journal.append([{ kind: 'ledger.put', payload: {} }], AT);
    expect(written[0]?.seq).toBe(3);
    expect(await journal.size()).toBe(3);
    expect(await journal.lastSeq()).toBe(3);
  });

  it('stamps every entry of one call with the same instant', async () => {
    const journal = await seeded();
    const entries = await journal.read();
    expect(entries[0]?.at).toBe('2025-03-03T09:00:00.000Z');
    expect(entries[1]?.at).toBe(entries[0]?.at);
  });

  it('writes nothing for an empty append', async () => {
    const journal = memoryJournal();
    expect(await journal.append([], AT)).toEqual([]);
    expect(await journal.size()).toBe(0);
    expect(await journal.lastSeq()).toBe(0);
  });
});

describe('read', () => {
  it('reads from a sequence onwards', async () => {
    const journal = await seeded();
    expect((await journal.read(2)).map((entry) => entry.seq)).toEqual([2]);
    expect(await journal.read(9)).toEqual([]);
  });

  it('refuses a sequence below one', async () => {
    const journal = await seeded();
    await expect(journal.read(0)).rejects.toThrow(ConflictError);
  });
});

describe('encode and decode', () => {
  it('round trips one entry', () => {
    const entry = { seq: 1, at: AT.toISOString(), kind: 'lexeme.put' as const, payload: { a: 1 } };
    expect(decodeEntry(encodeEntry(entry), 1)).toEqual(entry);
  });

  it('round trips a whole journal', async () => {
    const journal = await seeded();
    const entries = await journal.read();
    expect(decodeJournal(encodeJournal(entries))).toEqual(entries);
  });

  it('encodes an empty journal as an empty string', () => {
    expect(encodeJournal([])).toBe('');
    expect(decodeJournal('')).toEqual([]);
  });

  it('ends the encoded journal with a newline so an append starts clean', async () => {
    const journal = await seeded();
    expect(encodeJournal(await journal.read()).endsWith('\n')).toBe(true);
  });

  it('ignores blank lines when decoding', () => {
    const entry = { seq: 1, at: AT.toISOString(), kind: 'lexeme.put' as const, payload: {} };
    expect(decodeJournal(`${encodeEntry(entry)}\n\n`)).toHaveLength(1);
  });
});

describe('a damaged journal', () => {
  it('names the line that is not valid json', () => {
    expect(() => decodeEntry('{"seq":1,', 7)).toThrow(IntegrityError);
    expect(() => decodeEntry('{"seq":1,', 7)).toThrow(/journal line 7 is not valid json/);
  });

  it('refuses a line that parses to something other than an object', () => {
    expect(() => decodeEntry('[1,2]', 3)).toThrow(/journal line 3 is not an object/);
  });

  it('refuses an unknown kind', () => {
    const line = JSON.stringify({ seq: 1, at: AT.toISOString(), kind: 'nope', payload: {} });
    expect(() => decodeEntry(line, 1)).toThrow(/unknown kind: nope/);
  });

  it('refuses an entry with no payload', () => {
    const line = JSON.stringify({ seq: 1, at: AT.toISOString(), kind: 'lexeme.put' });
    expect(() => decodeEntry(line, 1)).toThrow(ValidationError);
  });

  it('refuses a journal whose sequence numbers skip', () => {
    const first = { seq: 1, at: AT.toISOString(), kind: 'lexeme.put' as const, payload: {} };
    const third = { seq: 3, at: AT.toISOString(), kind: 'lexeme.put' as const, payload: {} };
    expect(() => decodeJournal(encodeJournal([first, third]))).toThrow(
      /out of order at line 2, expected sequence 2 and found 3/,
    );
  });

  it('refuses a journal that starts at the wrong sequence', () => {
    const entry = { seq: 5, at: AT.toISOString(), kind: 'lexeme.put' as const, payload: {} };
    expect(() => decodeJournal(encodeJournal([entry]))).toThrow(IntegrityError);
  });
});

describe('entriesOfKind and payloadObject', () => {
  it('picks out one kind', async () => {
    const journal = await seeded();
    const entries = await journal.read();
    expect(entriesOfKind(entries, 'lexeme.put')).toHaveLength(1);
    expect(entriesOfKind(entries, 'deck.put')).toEqual([]);
  });

  it('reads a payload as an object and refuses one that is not', async () => {
    const journal = await seeded();
    const entries = await journal.read();
    expect(payloadObject(entries[0]!)).toEqual({ id: 'lex_a' });
    const broken = { ...entries[0]!, payload: 'nope' };
    expect(() => payloadObject(broken)).toThrow(/journal.1.payload is not an object/);
  });
});

describe('a seeded journal', () => {
  it('carries on numbering after the entries it was handed', async () => {
    const first = { seq: 1, at: AT.toISOString(), kind: 'lexeme.put' as const, payload: {} };
    const journal = memoryJournal([first]);
    const written = await journal.append([{ kind: 'ledger.put', payload: {} }], AT);
    expect(written[0]?.seq).toBe(2);
  });
});
