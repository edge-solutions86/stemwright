import { describe, expect, it } from 'vitest';
import { createApplication } from '../../src/app/application.js';
import { recordingBus } from '../../src/app/events.js';
import { fixedClock } from '../../src/app/ports.js';
import { deckCommand, decksCommand, spreadRows } from '../../src/cli/deck-command.js';
import { EXIT_FAILED, EXIT_MISUSED, EXIT_OK, run } from '../../src/cli/program.js';
import type { CommandContext, Program } from '../../src/cli/program.js';
import { deck } from '../../src/domain/drill/deck.js';
import { resolveMembership } from '../../src/domain/drill/membership.js';
import { deckStats } from '../../src/domain/report/deck-stats.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { seededStores } from '../support/app.js';
import { puella } from '../support/drill.js';
import { latinNoun } from '../support/profiles.js';

const DAY = parseCivilDate('2024-10-21');
const context: CommandContext = { write: () => undefined, now: new Date('2024-10-21T09:00:00Z') };

const build = async () => {
  const stores = await seededStores();
  await stores.decks.put(deck(latinNoun, { key: 'all' }));
  await stores.decks.put(deck(latinNoun, { key: 'singulars', scope: { cells: 'number=sg' } }));
  await stores.decks.put(deck(latinNoun, { key: 'one', scope: { lexemeIds: [puella.id] } }));
  const app = createApplication(stores, {
    bus: recordingBus(),
    clock: fixedClock(new Date('2024-10-21T09:00:00Z'), DAY),
  });
  const program: Program = {
    name: 'stemwright',
    version: '1.0.0',
    commands: [deckCommand(app), decksCommand(app)],
  };
  return { app, program };
};

const invoke = async (argv: readonly string[]) => {
  const { program } = await build();
  return run(program, argv, context);
};

describe('deck', () => {
  it('opens with what the deck holds', async () => {
    const result = await invoke(['deck', '--language', 'latin', '--deck', 'all']);
    expect(result.code).toBe(EXIT_OK);
    expect(result.output.split('\n')[0]).toMatch(/^all holds \d+ cells across \d+ lexemes/);
  });

  it('reads the scope of a deck that constrains nothing', async () => {
    const result = await invoke(['deck', '--language', 'latin', '--deck', 'all']);
    expect(result.output).toContain('everything in the language');
  });

  it('reads the scope of a deck that names cells', async () => {
    const result = await invoke(['deck', '--language', 'latin', '--deck', 'singulars']);
    expect(result.output).toContain('cells where number=sg');
  });

  it('reports the day it resolved against', async () => {
    const result = await invoke(['deck', '--language', 'latin', '--deck', 'all']);
    expect(result.output).toContain('2024-10-21');
  });

  it('takes the day it is given instead of the clock', async () => {
    const result = await invoke([
      'deck',
      '--language',
      'latin',
      '--deck',
      'all',
      '--on',
      '2019-01-01',
    ]);
    expect(result.output).toContain('2019-01-01');
    expect(result.output).toContain('the deck holds no cells at all');
  });

  it('lists the bundles at each end without naming one twice', async () => {
    const result = await invoke(['deck', '--language', 'latin', '--deck', 'all']);
    expect(result.output).toContain('Bundles at each end');
    expect(result.output).toContain('busiest');

    const listed = result.output
      .split('\n')
      .filter((line) => line.includes('busiest') || line.includes('thinnest'))
      .map((line) => line.trim().split(/ +/)[0]);
    expect(new Set(listed).size).toBe(listed.length);
  });

  it('honours the spread flag', async () => {
    const narrow = await invoke(['deck', '--language', 'latin', '--deck', 'all', '--spread', '1']);
    const wide = await invoke(['deck', '--language', 'latin', '--deck', 'all', '--spread', '4']);
    const count = (output: string) => output.split('\n').filter((line) => line.includes('busiest'));
    expect(count(narrow.output).length).toBe(1);
    expect(count(wide.output).length).toBeGreaterThan(count(narrow.output).length);
  });

  it('breaks the lexemes down by class', async () => {
    const result = await invoke(['deck', '--language', 'latin', '--deck', 'all']);
    expect(result.output).toContain('Lexemes by class');
    expect(result.output).toMatch(/first-declension +1/);
  });

  it('counts the lexemes it left out by reason', async () => {
    const result = await invoke(['deck', '--language', 'latin', '--deck', 'singulars']);
    expect(result.output).toContain('Lexemes left out');
  });

  it('counts what the scope excluded apart from what was broken', async () => {
    const result = await invoke(['deck', '--language', 'latin', '--deck', 'one']);
    const section = result.output.slice(result.output.indexOf('Lexemes left out'));
    expect(section).toMatch(/out-of-scope +3/);
    expect(section).not.toContain('unbound-class');
  });

  it('narrows without saving when cells are given', async () => {
    const { app, program } = await build();
    const result = await run(
      program,
      ['deck', '--language', 'latin', '--deck', 'all', '--cells', 'number=pl'],
      context,
    );
    expect(result.output).toContain('cells where number=pl');

    const stored = await app.decks.get('latin', 'all');
    expect(stored.scope.cells.clauses).toHaveLength(0);
  });

  it('shrinks the cell count when the narrowing bites', async () => {
    const whole = await invoke(['deck', '--language', 'latin', '--deck', 'all']);
    const half = await invoke([
      'deck',
      '--language',
      'latin',
      '--deck',
      'all',
      '--cells',
      'number=sg',
    ]);
    const cellsIn = (output: string) => Number(/holds (\d+) cells/.exec(output)?.[1] ?? '0');
    expect(cellsIn(half.output)).toBeGreaterThan(0);
    expect(cellsIn(half.output)).toBeLessThan(cellsIn(whole.output));
  });

  it('refuses a call with no deck', async () => {
    const result = await invoke(['deck', '--language', 'latin']);
    expect(result.code).toBe(EXIT_MISUSED);
    expect(result.output).toContain('deck is required');
  });

  it('refuses a selector that is not a selector', async () => {
    const result = await invoke([
      'deck',
      '--language',
      'latin',
      '--deck',
      'all',
      '--cells',
      'number',
    ]);
    expect(result.code).toBe(EXIT_FAILED);
  });

  it('fails for a deck nobody stored', async () => {
    const result = await invoke(['deck', '--language', 'latin', '--deck', 'nowhere']);
    expect(result.code).toBe(EXIT_FAILED);
    expect(result.output).toContain('was not found');
  });

  it('fails for a language nobody configured', async () => {
    const result = await invoke(['deck', '--language', 'greek', '--deck', 'all']);
    expect(result.code).toBe(EXIT_FAILED);
  });
});

describe('decks', () => {
  it('counts the decks it found', async () => {
    const result = await invoke(['decks', '--language', 'latin']);
    expect(result.code).toBe(EXIT_OK);
    expect(result.output).toContain('3 decks in latin on 2024-10-21');
  });

  it('lists them in key order with their scopes', async () => {
    const result = await invoke(['decks', '--language', 'latin']);
    const keys = result.output
      .split('\n')
      .map((line) => line.split(/ {2,}/)[0])
      .filter((key) => key === 'all' || key === 'one' || key === 'singulars');
    expect(keys).toEqual(['all', 'one', 'singulars']);
    expect(result.output).toContain('everything in the language');
  });

  it('gives each deck the size it resolves to', async () => {
    const { app } = await build();
    const drillContext = await app.drill.context('latin');
    const entry = await app.decks.get('latin', 'singulars');
    const expected = deckStats(entry, resolveMembership(drillContext, entry, DAY), []).cells;

    const result = await invoke(['decks', '--language', 'latin']);
    const row = result.output.split('\n').find((line) => line.startsWith('singulars'));
    expect(row).toContain(String(expected));
  });

  it('fails for a language nobody configured', async () => {
    const result = await invoke(['decks', '--language', 'greek']);
    expect(result.code).toBe(EXIT_FAILED);
  });
});

describe('spreadRows', () => {
  const statsOf = (perBundle: Record<string, number>) =>
    ({
      deckKey: 'k',
      lexemes: 1,
      cells: Object.values(perBundle).reduce((sum, count) => sum + count, 0),
      bundles: Object.keys(perBundle).length,
      cellsPerLexeme: 1,
      skipped: 0,
      classes: new Map<string, number>(),
      perBundle: new Map(Object.entries(perBundle)),
    }) as const;

  it('never lists a bundle at both ends', async () => {
    const rows = spreadRows(statsOf({ a: 3, b: 2, c: 1 }), 3);
    expect(rows.map((row) => row.bundle)).toEqual(['a', 'b', 'c']);
    await Promise.resolve();
  });

  it('puts the largest first and the smallest last', () => {
    const rows = spreadRows(statsOf({ a: 9, b: 5, c: 4, d: 3, e: 1 }), 2);
    expect(rows[0]).toEqual({ bundle: 'a', cells: 9, end: 'busiest' });
    expect(rows.at(-1)).toEqual({ bundle: 'e', cells: 1, end: 'thinnest' });
  });

  it('gives nothing back for a deck with no cells', () => {
    expect(spreadRows(statsOf({}), 3)).toEqual([]);
  });
});
