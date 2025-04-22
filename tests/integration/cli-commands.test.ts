import { describe, expect, it } from 'vitest';
import { createApplication } from '../../src/app/application.js';
import { recordingBus } from '../../src/app/events.js';
import { fixedClock } from '../../src/app/ports.js';
import { allCommands } from '../../src/cli/commands.js';
import { EXIT_FAILED, EXIT_MISUSED, EXIT_OK, run } from '../../src/cli/program.js';
import type { CommandContext, Program } from '../../src/cli/program.js';
import { deck } from '../../src/domain/drill/deck.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { seededStores } from '../support/app.js';
import { puella } from '../support/drill.js';
import { latinNoun } from '../support/profiles.js';

const DAY = parseCivilDate('2024-10-21');
const context: CommandContext = {
  write: () => undefined,
  now: new Date('2024-10-21T09:00:00Z'),
};

const build = async () => {
  const stores = await seededStores();
  await stores.decks.put(deck(latinNoun, { key: 'all' }));
  const app = createApplication(stores, {
    bus: recordingBus(),
    clock: fixedClock(new Date('2024-10-21T09:00:00Z'), DAY),
  });
  const program: Program = {
    name: 'stemwright',
    version: '1.0.0',
    commands: allCommands(app),
  };
  return { app, program };
};

describe('the command table', () => {
  it('declares four commands', async () => {
    const { program } = await build();
    expect(program.commands.map((command) => command.name).sort()).toEqual([
      'coverage',
      'lexemes',
      'paradigm',
      'queue',
    ]);
  });

  it('gives every command a usage line', async () => {
    const { program } = await build();
    expect(program.commands.every((command) => command.usage.startsWith('stemwright '))).toBe(true);
  });
});

describe('queue', () => {
  it('prints the cells a learner would be asked', async () => {
    const { program } = await build();
    const result = await run(
      program,
      ['queue', '--language', 'latin', '--deck', 'all', '--learner', 'lrn_one'],
      context,
    );
    expect(result.code).toBe(EXIT_OK);
    expect(result.output).toContain('bundle');
    expect(result.output).toContain('new');
  });

  it('refuses a call with no learner', async () => {
    const { program } = await build();
    const result = await run(program, ['queue', '--language', 'latin', '--deck', 'all'], context);
    expect(result.code).toBe(EXIT_MISUSED);
    expect(result.output).toContain('learner is required');
  });

  it('fails when the deck is not there', async () => {
    const { program } = await build();
    const result = await run(
      program,
      ['queue', '--language', 'latin', '--deck', 'nowhere', '--learner', 'lrn_one'],
      context,
    );
    expect(result.code).toBe(EXIT_FAILED);
    expect(result.output).toContain('was not found');
  });
});

describe('paradigm', () => {
  it('prints the table a lexeme is graded against', async () => {
    const { program } = await build();
    const result = await run(
      program,
      [
        'paradigm',
        '--language',
        'latin',
        '--lexeme',
        puella.id,
        '--rows',
        'case',
        '--columns',
        'number',
      ],
      context,
    );
    expect(result.code).toBe(EXIT_OK);
    expect(result.output).toContain('puella under first-declension revision 1');
    expect(result.output).toContain('puellae');
  });

  it('fails for a lexeme nobody stored', async () => {
    const { program } = await build();
    const result = await run(
      program,
      [
        'paradigm',
        '--language',
        'latin',
        '--lexeme',
        'lex_zzzzzz',
        '--rows',
        'case',
        '--columns',
        'number',
      ],
      context,
    );
    expect(result.code).toBe(EXIT_FAILED);
  });

  it('fails for a day before the profile was published', async () => {
    const { program } = await build();
    const result = await run(
      program,
      [
        'paradigm',
        '--language',
        'latin',
        '--lexeme',
        puella.id,
        '--rows',
        'case',
        '--columns',
        'number',
        '--on',
        '2024-01-01',
      ],
      context,
    );
    expect(result.code).toBe(EXIT_FAILED);
    expect(result.output).toContain('profile');
  });
});

describe('coverage', () => {
  it('prints the counts and the weakest bundles', async () => {
    const { program } = await build();
    const result = await run(
      program,
      ['coverage', '--language', 'latin', '--deck', 'all', '--learner', 'lrn_one'],
      context,
    );
    expect(result.code).toBe(EXIT_OK);
    expect(result.output).toContain('cells');
    expect(result.output).toContain('maturity');
  });
});

describe('lexemes', () => {
  it('lists the lexicon', async () => {
    const { program } = await build();
    const result = await run(program, ['lexemes', '--language', 'latin'], context);
    expect(result.code).toBe(EXIT_OK);
    expect(result.output).toContain('puella');
    expect(result.output).toContain('bellum');
  });

  it('narrows to a search term', async () => {
    const { program } = await build();
    const result = await run(
      program,
      ['lexemes', '--language', 'latin', '--search', 'field'],
      context,
    );
    expect(result.output).toContain('ager');
    expect(result.output).not.toContain('puella');
  });

  it('fails for a language nobody configured', async () => {
    const { program } = await build();
    const result = await run(program, ['lexemes', '--language', 'greek'], context);
    expect(result.code).toBe(EXIT_FAILED);
  });
});
