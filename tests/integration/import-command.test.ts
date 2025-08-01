import { mkdtemp, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { createApplication } from '../../src/app/application.js';
import { recordingBus } from '../../src/app/events.js';
import { fixedClock } from '../../src/app/ports.js';
import { importCommand } from '../../src/cli/import-command.js';
import { EXIT_FAILED, EXIT_MISUSED, EXIT_OK, run } from '../../src/cli/program.js';
import type { CommandContext, Program } from '../../src/cli/program.js';
import { removeIfPresent } from '../../src/infra/files.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { seededStores } from '../support/app.js';

const DAY = parseCivilDate('2024-10-21');
const context: CommandContext = { write: () => undefined, now: new Date() };

const roots: string[] = [];
const freshFile = async (name: string, contents: string) => {
  const root = await mkdtemp(join(tmpdir(), 'stemwright-import-'));
  roots.push(root);
  const path = join(root, name);
  await writeFile(path, contents, 'utf8');
  return path;
};

afterEach(async () => {
  while (roots.length > 0) {
    await removeIfPresent(roots.pop()!);
  }
});

const header = 'lemma,class,stem,gloss,tags';
const good = `${header}\nnauta,first-declension,naut,sailor,core\nvia,first-declension,vi,road,core\n`;
const mixed = `${header}\nnauta,first-declension,naut,sailor,core\nrex,fourth-declension,reg,king,core\n`;

const build = async () => {
  const stores = await seededStores();
  const app = createApplication(stores, {
    bus: recordingBus(),
    clock: fixedClock(new Date('2024-10-21T09:00:00Z'), DAY),
  });
  const program: Program = {
    name: 'stemwright',
    version: '1.0.0',
    commands: [importCommand(app)],
  };
  return { app, stores, program };
};

describe('a dry run', () => {
  it('reports what it would add and writes nothing', async () => {
    const { program, stores } = await build();
    const file = await freshFile('words.csv', good);
    const result = await run(program, ['import', '--language', 'latin', '--file', file], context);

    expect(result.code).toBe(EXIT_OK);
    expect(result.output).toContain('2 of 2 rows are usable');
    expect(result.output).toContain('This was a dry run');
    expect(await stores.lexemes.listByLanguage('latin')).toHaveLength(4);
  });

  it('names the rows it would reject', async () => {
    const { program } = await build();
    const file = await freshFile('words.csv', mixed);
    const result = await run(program, ['import', '--language', 'latin', '--file', file], context);
    expect(result.output).toContain('1 of 2 rows are usable');
    expect(result.output).toContain('line 3 rex:');
  });

  it('names a row that is already in the lexicon', async () => {
    const { program } = await build();
    const file = await freshFile(
      'words.csv',
      `${header}\npuella,first-declension,puell,girl,core\n`,
    );
    const result = await run(program, ['import', '--language', 'latin', '--file', file], context);
    expect(result.output).toContain('Already in the lexicon');
    expect(result.output).toContain('puella');
  });
});

describe('applying', () => {
  it('writes the accepted rows', async () => {
    const { program, stores } = await build();
    const file = await freshFile('words.csv', good);
    const result = await run(
      program,
      ['import', '--language', 'latin', '--file', file, '--apply'],
      context,
    );

    expect(result.code).toBe(EXIT_OK);
    expect(result.output).toContain('Wrote 2 lexemes to latin');
    expect(await stores.lexemes.listByLanguage('latin')).toHaveLength(6);
  });

  it('writes only the rows that parsed', async () => {
    const { program, stores } = await build();
    const file = await freshFile('words.csv', mixed);
    await run(program, ['import', '--language', 'latin', '--file', file, '--apply'], context);
    expect(await stores.lexemes.listByLanguage('latin')).toHaveLength(5);
  });

  it('is idempotent when run twice', async () => {
    const { program, stores } = await build();
    const file = await freshFile('words.csv', good);
    await run(program, ['import', '--language', 'latin', '--file', file, '--apply'], context);
    await run(program, ['import', '--language', 'latin', '--file', file, '--apply'], context);
    expect(await stores.lexemes.listByLanguage('latin')).toHaveLength(6);
  });
});

describe('failures', () => {
  it('refuses a call with no file', async () => {
    const { program } = await build();
    const result = await run(program, ['import', '--language', 'latin'], context);
    expect(result.code).toBe(EXIT_MISUSED);
    expect(result.output).toContain('file is required');
  });

  it('fails for a language nobody configured', async () => {
    const { program } = await build();
    const file = await freshFile('words.csv', good);
    const result = await run(program, ['import', '--language', 'greek', '--file', file], context);
    expect(result.code).toBe(EXIT_FAILED);
  });

  it('fails for a file that is not there', async () => {
    const { program } = await build();
    const result = await run(
      program,
      ['import', '--language', 'latin', '--file', 'nowhere.csv'],
      context,
    );
    expect(result.code).toBe(EXIT_FAILED);
  });

  it('fails for a file missing a required column', async () => {
    const { program } = await build();
    const file = await freshFile('words.csv', 'lemma,gloss\npuella,girl\n');
    const result = await run(program, ['import', '--language', 'latin', '--file', file], context);
    expect(result.code).toBe(EXIT_FAILED);
    expect(result.output).toContain('missing the columns');
  });
});
