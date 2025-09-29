import { describe, expect, it } from 'vitest';
import type { DeckScopeInput } from '../../src/domain/drill/deck.js';
import { deck } from '../../src/domain/drill/deck.js';
import type { DrillContext } from '../../src/domain/drill/membership.js';
import { resolveMembership } from '../../src/domain/drill/membership.js';
import { classRegistry } from '../../src/domain/lexicon/inflection-class.js';
import { buildLexicon } from '../../src/domain/lexicon/lexicon.js';
import { profileBook } from '../../src/domain/paradigm/book.js';
import type { Finding } from '../../src/domain/report/health.js';
import {
  checkHealth,
  countLevels,
  describeHealth,
  isHealthy,
} from '../../src/domain/report/health.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { latinBook, firstHistory } from '../support/book.js';
import { latinClasses } from '../support/classes.js';
import { ager, bellum, puella, rex } from '../support/drill.js';
import { latinNoun } from '../support/profiles.js';

const DAY = parseCivilDate('2024-10-21');

const contextOf = (overrides: Partial<DrillContext> = {}): DrillContext => ({
  schema: latinNoun,
  classes: latinClasses,
  book: latinBook,
  lexicon: buildLexicon('latin', [puella, bellum, ager, rex]),
  ...overrides,
});

const withDeck = (context: DrillContext, scope: DeckScopeInput = {}) => {
  const entry = deck(latinNoun, { key: 'all', scope });
  return [{ deck: entry, membership: resolveMembership(context, entry, DAY) }];
};

const messagesFor = (findings: readonly Finding[], area: string) =>
  findings.filter((finding) => finding.area === area).map((finding) => finding.message);

describe('checkHealth over classes', () => {
  it('reports every class with no paradigm anywhere above it', () => {
    const findings = checkHealth({ context: contextOf(), decks: [], on: DAY });
    const classes = messagesFor(findings, 'classes');
    expect(classes.some((message) => message.startsWith('third-declension has no paradigm'))).toBe(
      true,
    );
    expect(classes.some((message) => message.startsWith('first-conjugation has no paradigm'))).toBe(
      true,
    );
  });

  it('says nothing about a class that inherits a binding from its parent', () => {
    const findings = checkHealth({ context: contextOf(), decks: [], on: DAY });
    expect(messagesFor(findings, 'classes').join('\n')).not.toContain('second-declension.neuter');
  });

  it('stays quiet when every class is bound', () => {
    const classes = classRegistry('latin', [
      { key: 'first-declension', label: 'First', partOfSpeech: 'noun' },
    ]);
    const book = profileBook({
      language: 'latin',
      histories: [firstHistory],
      bindings: { 'first-declension': 'first-declension' },
    });
    const context = contextOf({
      classes,
      book,
      lexicon: buildLexicon('latin', [puella]),
    });
    expect(messagesFor(checkHealth({ context, decks: [], on: DAY }), 'classes')).toEqual([]);
  });
});

describe('checkHealth over profiles', () => {
  it('warns about a profile with nothing in force yet and names the start', () => {
    const findings = checkHealth({
      context: contextOf(),
      decks: [],
      on: parseCivilDate('2024-01-01'),
    });
    expect(messagesFor(findings, 'profiles')).toContain(
      'first-declension has nothing in force on 2024-01-01, the first starts 2024-09-01',
    );
  });

  it('stays quiet once every profile has been published', () => {
    const findings = checkHealth({ context: contextOf(), decks: [], on: DAY });
    expect(messagesFor(findings, 'profiles')).toEqual([]);
  });
});

describe('checkHealth over the lexicon', () => {
  it('warns that a lexeme whose class is unbound cannot be drilled', () => {
    const findings = checkHealth({ context: contextOf(), decks: [], on: DAY });
    expect(messagesFor(findings, 'lexicon')).toContain(
      '1 lexemes cannot be drilled because their class has no paradigm',
    );
  });

  it('calls a class the registry does not hold an error', () => {
    const stray = { ...puella, classKey: 'fourth-declension' };
    const context = contextOf({ lexicon: buildLexicon('latin', [stray]) });
    const findings = checkHealth({ context, decks: [], on: DAY });
    const lexicon = findings.filter((finding) => finding.area === 'lexicon');
    expect(lexicon[0]?.level).toBe('error');
    expect(lexicon[0]?.message).toContain('fourth-declension');
  });

  it('notes an empty lexicon rather than passing it in silence', () => {
    const context = contextOf({ lexicon: buildLexicon('latin', []) });
    const findings = checkHealth({ context, decks: [], on: DAY });
    expect(messagesFor(findings, 'lexicon')).toContain('the lexicon is empty');
  });
});

describe('checkHealth over decks', () => {
  it('calls a deck that selects nothing an error', () => {
    const context = contextOf();
    const decks = withDeck(context, { classKeys: ['third-declension'] });
    const findings = checkHealth({ context, decks, on: DAY });
    expect(
      findings.some((finding) => finding.message === 'all selects nothing on 2024-10-21'),
    ).toBe(true);
  });

  it('notes a deck holding only a handful of lexemes', () => {
    const context = contextOf();
    const findings = checkHealth({ context, decks: withDeck(context), on: DAY });
    expect(messagesFor(findings, 'decks')).toContain('all holds only 3 lexemes');
  });

  it('notes when nothing has been defined at all', () => {
    const findings = checkHealth({ context: contextOf(), decks: [], on: DAY });
    expect(messagesFor(findings, 'decks')).toEqual(['no decks are defined']);
  });
});

describe('the order and the summary', () => {
  it('puts errors first and notes last', () => {
    const context = contextOf();
    const findings = checkHealth({ context, decks: withDeck(context), on: DAY });
    const levels = findings.map((finding) => finding.level);
    expect(levels).toEqual([...levels].sort((a, b) => levels.indexOf(a) - levels.indexOf(b)));
    expect(levels[0]).toBe('error');
    expect(levels.at(-1)).toBe('note');
  });

  it('keeps the order the checks ran in within one level', () => {
    const context = contextOf();
    const errors = checkHealth({ context, decks: withDeck(context), on: DAY }).filter(
      (finding) => finding.level === 'error',
    );
    expect(errors.every((finding) => finding.area === 'classes')).toBe(true);
  });

  it('counts each level, including the ones nothing reached', () => {
    expect(countLevels([])).toEqual({ error: 0, warning: 0, note: 0 });
    expect(
      countLevels([
        { level: 'error', area: 'a', message: 'one' },
        { level: 'error', area: 'a', message: 'two' },
        { level: 'note', area: 'b', message: 'three' },
      ]),
    ).toEqual({ error: 2, warning: 0, note: 1 });
  });

  it('calls a language healthy when only warnings and notes were found', () => {
    expect(isHealthy([{ level: 'warning', area: 'a', message: 'one' }])).toBe(true);
    expect(isHealthy([{ level: 'error', area: 'a', message: 'one' }])).toBe(false);
    expect(isHealthy([])).toBe(true);
  });

  it('describes what it found in one line', () => {
    expect(describeHealth([], 'latin')).toBe('latin has nothing to report');
    expect(describeHealth([{ level: 'warning', area: 'a', message: 'one' }], 'latin')).toBe(
      'latin has 0 errors, 1 warnings and 0 notes',
    );
  });
});
