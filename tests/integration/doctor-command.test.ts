import { describe, expect, it } from 'vitest';
import { createApplication } from '../../src/app/application.js';
import { recordingBus } from '../../src/app/events.js';
import type { LanguageBundle } from '../../src/app/ports.js';
import { fixedClock } from '../../src/app/ports.js';
import { doctorCommand } from '../../src/cli/doctor-command.js';
import { EXIT_FAILED, EXIT_MISUSED, EXIT_OK, run } from '../../src/cli/program.js';
import type { CommandContext, Program } from '../../src/cli/program.js';
import { deck } from '../../src/domain/drill/deck.js';
import { classRegistry } from '../../src/domain/lexicon/inflection-class.js';
import { profileBook } from '../../src/domain/paradigm/book.js';
import { NO_RULES } from '../../src/domain/paradigm/realise.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { memoryStores } from '../../src/infra/memory.js';
import { seededStores } from '../support/app.js';
import { firstHistory } from '../support/book.js';
import { puella } from '../support/drill.js';
import { latinOrtho } from '../support/ortho.js';
import { latinNoun } from '../support/profiles.js';

const DAY = parseCivilDate('2024-10-21');
const context: CommandContext = { write: () => undefined, now: new Date('2024-10-21T09:00:00Z') };

const programOver = (stores: Awaited<ReturnType<typeof seededStores>>): Program => {
  const app = createApplication(stores, {
    bus: recordingBus(),
    clock: fixedClock(new Date('2024-10-21T09:00:00Z'), DAY),
  });
  return { name: 'stemwright', version: '1.0.0', commands: [doctorCommand(app)] };
};

/** The shipped fixture has classes nobody bound, which is the unhappy case. */
const messy = async () => {
  const stores = await seededStores();
  await stores.decks.put(deck(latinNoun, { key: 'all' }));
  return programOver(stores);
};

/** A language with one class, one binding, one lexeme and one deck. */
const tidy = async (bindings: Record<string, string> = {}) => {
  const stores = memoryStores();
  const bundle: LanguageBundle = {
    language: 'latin',
    schema: latinNoun,
    classes: classRegistry('latin', [
      { key: 'first-declension', label: 'First declension', partOfSpeech: 'noun' },
    ]),
    ortho: latinOrtho,
    rules: NO_RULES,
  };
  await stores.languages.put(bundle);
  await stores.books.put(
    profileBook({
      language: 'latin',
      histories: [firstHistory],
      bindings: { 'first-declension': 'first-declension', ...bindings },
    }),
  );
  await stores.lexemes.put(puella);
  await stores.decks.put(deck(latinNoun, { key: 'all' }));
  return programOver(stores);
};

describe('doctor on a language with holes in it', () => {
  it('exits non zero so it can be a step in a deployment', async () => {
    const result = await run(await messy(), ['doctor', '--language', 'latin'], context);
    expect(result.code).toBe(EXIT_FAILED);
  });

  it('still prints the whole report on the way out', async () => {
    const result = await run(await messy(), ['doctor', '--language', 'latin'], context);
    expect(result.output).toContain('Findings');
    expect(result.output).toContain('third-declension has no paradigm bound');
    expect(result.output).toContain('errors stop a class drilling');
  });

  it('counts what it looked at', async () => {
    const result = await run(await messy(), ['doctor', '--language', 'latin'], context);
    expect(result.output).toMatch(/classes {2,}9/);
    expect(result.output).toMatch(/profiles {2,}2/);
    expect(result.output).toMatch(/lexemes {2,}4/);
    expect(result.output).toMatch(/decks {2,}1/);
  });

  it('leads with the count of each level', async () => {
    const result = await run(await messy(), ['doctor', '--language', 'latin'], context);
    expect(result.output.split('\n')[0]).toMatch(/^latin has \d+ errors, \d+ warnings/);
  });
});

describe('doctor on a language with nothing wrong', () => {
  it('passes', async () => {
    const result = await run(await tidy(), ['doctor', '--language', 'latin'], context);
    expect(result.code).toBe(EXIT_OK);
  });

  it('still says what it noted', async () => {
    const result = await run(await tidy(), ['doctor', '--language', 'latin'], context);
    expect(result.output).toContain('all holds only 1 lexemes');
  });

  it('passes a warning by default and refuses it under strict', async () => {
    const stray = { 'fourth-declension': 'first-declension' };

    const relaxed = await run(await tidy(stray), ['doctor', '--language', 'latin'], context);
    expect(relaxed.code).toBe(EXIT_OK);
    expect(relaxed.output).toContain('the binding on fourth-declension is shadowed');

    const strict = await run(
      await tidy(stray),
      ['doctor', '--language', 'latin', '--strict'],
      context,
    );
    expect(strict.code).toBe(EXIT_FAILED);
    expect(strict.output).toContain('1 warnings, and strict was asked for');
  });

  it('reports a day on which nothing had been published yet', async () => {
    const result = await run(
      await tidy(),
      ['doctor', '--language', 'latin', '--on', '2024-01-01'],
      context,
    );
    expect(result.code).toBe(EXIT_FAILED);
    expect(result.output).toContain('nothing in force on 2024-01-01');
    expect(result.output).toContain('all selects nothing on 2024-01-01');
  });

  it('does not fail under strict when only notes were found', async () => {
    const result = await run(await tidy(), ['doctor', '--language', 'latin', '--strict'], context);
    expect(result.code).toBe(EXIT_OK);
  });
});

describe('doctor as a command', () => {
  it('refuses a call with no language', async () => {
    const result = await run(await tidy(), ['doctor'], context);
    expect(result.code).toBe(EXIT_MISUSED);
  });

  it('refuses a flag it does not declare', async () => {
    const result = await run(await tidy(), ['doctor', '--language', 'latin', '--deep'], context);
    expect(result.code).toBe(EXIT_MISUSED);
  });

  it('fails for a language nobody configured', async () => {
    const result = await run(await tidy(), ['doctor', '--language', 'greek'], context);
    expect(result.code).toBe(EXIT_FAILED);
    expect(result.output).toContain('was not found');
  });

  it('documents itself', async () => {
    const result = await run(await tidy(), ['help', 'doctor'], context);
    expect(result.output).toContain('stemwright doctor --language latin --strict');
    expect(result.output).toContain('--strict');
  });
});
