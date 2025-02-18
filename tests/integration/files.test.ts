import { mkdtemp, readFile, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { IntegrityError } from '../../src/domain/errors.js';
import {
  atomicWrite,
  dataLayout,
  ensureDirectory,
  fileJournal,
  isMissing,
  readIfPresent,
  removeIfPresent,
  snapshotPath,
  snapshotSeq,
} from '../../src/infra/files.js';
import { encodeJournal } from '../../src/infra/journal.js';

const AT = new Date('2025-03-03T09:00:00Z');
const roots: string[] = [];

const freshRoot = async () => {
  const root = await mkdtemp(join(tmpdir(), 'stemwright-'));
  roots.push(root);
  return root;
};

afterEach(async () => {
  while (roots.length > 0) {
    await removeIfPresent(roots.pop()!);
  }
});

describe('dataLayout', () => {
  it('puts the journal and the snapshots under the root', () => {
    const layout = dataLayout(join('a', 'b'));
    expect(layout.journalPath.endsWith('journal.ndjson')).toBe(true);
    expect(layout.snapshotDir.endsWith('snapshots')).toBe(true);
  });
});

describe('atomicWrite and readIfPresent', () => {
  it('writes and reads back', async () => {
    const root = await freshRoot();
    const path = join(root, 'nested', 'file.json');
    await atomicWrite(path, 'hello');
    expect(await readIfPresent(path)).toBe('hello');
  });

  it('creates the directory it needs', async () => {
    const root = await freshRoot();
    await atomicWrite(join(root, 'deep', 'deeper', 'file.json'), 'hello');
    expect(await readIfPresent(join(root, 'deep', 'deeper', 'file.json'))).toBe('hello');
  });

  it('replaces an existing file', async () => {
    const root = await freshRoot();
    const path = join(root, 'file.json');
    await atomicWrite(path, 'first');
    await atomicWrite(path, 'second');
    expect(await readFile(path, 'utf8')).toBe('second');
  });

  it('leaves no temporary file behind', async () => {
    const root = await freshRoot();
    const path = join(root, 'file.json');
    await atomicWrite(path, 'hello');
    expect(await readIfPresent(`${path}.tmp`)).toBeUndefined();
  });

  it('reads a missing file as undefined', async () => {
    const root = await freshRoot();
    expect(await readIfPresent(join(root, 'nowhere.json'))).toBeUndefined();
  });
});

describe('isMissing', () => {
  it('recognises the missing file code and nothing else', () => {
    expect(isMissing({ code: 'ENOENT' })).toBe(true);
    expect(isMissing({ code: 'EACCES' })).toBe(false);
    expect(isMissing(new Error('nope'))).toBe(false);
    expect(isMissing(null)).toBe(false);
  });
});

describe('removeIfPresent', () => {
  it('removes a file and tolerates one that is not there', async () => {
    const root = await freshRoot();
    const path = join(root, 'file.json');
    await atomicWrite(path, 'hello');
    await removeIfPresent(path);
    expect(await readIfPresent(path)).toBeUndefined();
    await expect(removeIfPresent(path)).resolves.toBeUndefined();
  });
});

describe('fileJournal', () => {
  it('starts empty in a fresh directory', async () => {
    const journal = await fileJournal(dataLayout(await freshRoot()));
    expect(await journal.size()).toBe(0);
    expect(await journal.lastSeq()).toBe(0);
  });

  it('appends and reads back', async () => {
    const layout = dataLayout(await freshRoot());
    const journal = await fileJournal(layout);
    await journal.append([{ kind: 'ledger.put', payload: { learnerId: 'lrn_one' } }], AT);
    expect(await journal.size()).toBe(1);
    expect((await journal.read())[0]?.kind).toBe('ledger.put');
  });

  it('survives a reopen', async () => {
    const layout = dataLayout(await freshRoot());
    const first = await fileJournal(layout);
    await first.append([{ kind: 'ledger.put', payload: {} }], AT);
    const second = await fileJournal(layout);
    expect(await second.size()).toBe(1);
    expect(await second.lastSeq()).toBe(1);
  });

  it('carries on numbering after a reopen', async () => {
    const layout = dataLayout(await freshRoot());
    const first = await fileJournal(layout);
    await first.append([{ kind: 'ledger.put', payload: {} }], AT);
    const second = await fileJournal(layout);
    const written = await second.append([{ kind: 'ledger.put', payload: {} }], AT);
    expect(written[0]?.seq).toBe(2);
  });

  it('appends rather than rewriting the file', async () => {
    const layout = dataLayout(await freshRoot());
    const journal = await fileJournal(layout);
    await journal.append([{ kind: 'ledger.put', payload: { a: 1 } }], AT);
    await journal.append([{ kind: 'ledger.put', payload: { a: 2 } }], AT);
    const text = await readFile(layout.journalPath, 'utf8');
    expect(text.trim().split('\n')).toHaveLength(2);
  });

  it('writes nothing for an empty append', async () => {
    const layout = dataLayout(await freshRoot());
    const journal = await fileJournal(layout);
    expect(await journal.append([], AT)).toEqual([]);
    expect(await readIfPresent(layout.journalPath)).toBeUndefined();
  });

  it('refuses to open a journal whose sequence numbers skip', async () => {
    const layout = dataLayout(await freshRoot());
    await ensureDirectory(layout.root);
    const entries = [
      { seq: 1, at: AT.toISOString(), kind: 'ledger.put' as const, payload: {} },
      { seq: 3, at: AT.toISOString(), kind: 'ledger.put' as const, payload: {} },
    ];
    await writeFile(layout.journalPath, encodeJournal(entries), 'utf8');
    await expect(fileJournal(layout)).rejects.toThrow(IntegrityError);
  });

  it('refuses a read below sequence one', async () => {
    const journal = await fileJournal(dataLayout(await freshRoot()));
    await expect(journal.read(0)).rejects.toThrow(IntegrityError);
  });
});

describe('snapshot paths', () => {
  it('pads the sequence so the files sort in order', () => {
    const layout = dataLayout('root');
    expect(snapshotPath(layout, 7).endsWith('000000000007.json')).toBe(true);
  });

  it('reads the sequence back off a name', () => {
    expect(snapshotSeq('000000000007.json')).toBe(7);
    expect(snapshotSeq('journal.ndjson')).toBeUndefined();
    expect(snapshotSeq('7.json')).toBeUndefined();
  });
});
