import type { Application } from '../app/application.js';
import { requireLanguage } from '../app/loaders.js';
import { countBy } from '../domain/collections.js';
import type { Deck } from '../domain/drill/deck.js';
import { describeScope, withScope } from '../domain/drill/deck.js';
import { resolveMembership } from '../domain/drill/membership.js';
import { allLexemes } from '../domain/lexicon/lexicon.js';
import type { DeckStats } from '../domain/report/deck-stats.js';
import {
  bundleImbalance,
  busiestBundles,
  deckStats,
  deckWarnings,
  describeDeck,
  thinnestBundles,
} from '../domain/report/deck-stats.js';
import { parseCivilDate } from '../domain/time/civil.js';
import { numberFlag, requireFlag, stringFlag } from './args.js';
import type { Command } from './program.js';
import { keyValues, section, table } from './render.js';

/**
 * Looking at a deck before a class is given it.
 *
 * The number worth reading is the imbalance. A deck definition that reads
 * perfectly well can still come out ninety percent nominative once the class
 * tree it names has been expanded, and nobody notices until a term of learners
 * has been drilled on one row of the table. The stored definition tells you
 * nothing about that, because the cells only exist after a day has been picked
 * and the profiles in force on it have been resolved.
 *
 * `--cells` narrows the stored deck without saving, so an editor can ask what a
 * selector would do before committing to it.
 */
const dayFlag = { kind: 'string' as const, describe: 'the civil day to resolve against' };
const languageFlag = { kind: 'string' as const, describe: 'the language key', required: true };

const SPREAD_DEFAULT = 3;

interface SpreadRow {
  readonly bundle: string;
  readonly cells: number;
  readonly end: string;
}

/**
 * The busiest and the thinnest bundles, with the overlap removed. A deck with
 * four bundles asked for three at each end would otherwise list most of them
 * twice, once under each heading, which reads as though the deck were larger
 * than it is.
 */
export function spreadRows(stats: DeckStats, limit: number): SpreadRow[] {
  const busiest = busiestBundles(stats, limit);
  const taken = new Set(busiest.map(([bundle]) => bundle));
  const thinnest = thinnestBundles(stats, limit).filter(([bundle]) => !taken.has(bundle));

  return [
    ...busiest.map(([bundle, cells]) => ({ bundle, cells, end: 'busiest' })),
    ...thinnest.reverse().map(([bundle, cells]) => ({ bundle, cells, end: 'thinnest' })),
  ];
}

function classRows(stats: DeckStats): string[][] {
  return [...stats.classes]
    .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
    .map(([classKey, count]) => [classKey, String(count)]);
}

/**
 * Why lexemes fell out, counted by reason. `out-of-scope` is left in here even
 * though the warning list drops it, because an editor checking a narrow deck
 * wants to see that the selector did the excluding rather than a missing
 * profile.
 */
function skipRows(membership: ReturnType<typeof resolveMembership>): string[][] {
  return [...countBy(membership.skipped, (entry) => entry.reason)]
    .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
    .map(([reason, count]) => [reason, String(count)]);
}

export function deckCommand(app: Application): Command {
  return {
    name: 'deck',
    describe: 'show what a deck holds and where it is unbalanced',
    usage: 'stemwright deck --language latin --deck all',
    flags: {
      language: languageFlag,
      deck: { kind: 'string', alias: 'd', describe: 'the deck key', required: true },
      on: dayFlag,
      cells: { kind: 'string', describe: 'narrow to a cell selector without saving it' },
      spread: { kind: 'number', describe: 'how many bundles to list at each end' },
    },
    async run(args) {
      const language = requireFlag(args, 'language');
      const bundle = await requireLanguage(app.stores, language);
      const on = parseCivilDate(stringFlag(args, 'on') ?? app.clock.today(), 'on');

      const stored = await app.decks.get(language, requireFlag(args, 'deck'));
      const narrow = stringFlag(args, 'cells');
      const entry: Deck =
        narrow === undefined ? stored : withScope(bundle.schema, stored, { cells: narrow });

      const context = await app.drill.context(language);
      const membership = resolveMembership(context, entry, on);
      const stats = deckStats(entry, membership, allLexemes(context.lexicon));
      const limit = Math.max(1, numberFlag(args, 'spread') ?? SPREAD_DEFAULT);

      const summary = keyValues([
        ['scope', describeScope(entry.scope)],
        ['on', on],
        ['lexemes', String(stats.lexemes)],
        ['cells', String(stats.cells)],
        ['bundles', String(stats.bundles)],
        ['cells per lexeme', stats.cellsPerLexeme.toFixed(1)],
        ['imbalance', bundleImbalance(stats).toFixed(2)],
      ]);

      const spread = table(
        [{ header: 'bundle' }, { header: 'cells', align: 'right' }, { header: 'end' }],
        spreadRows(stats, limit).map((row) => [row.bundle, String(row.cells), row.end]),
      );

      return [
        describeDeck(stats),
        '',
        summary,
        '',
        section('Bundles at each end', spread),
        '',
        section(
          'Lexemes by class',
          table([{ header: 'class' }, { header: 'lexemes', align: 'right' }], classRows(stats)),
        ),
        '',
        section(
          'Lexemes left out',
          skipRows(membership).length === 0
            ? ''
            : table(
                [{ header: 'reason' }, { header: 'lexemes', align: 'right' }],
                skipRows(membership),
              ),
        ),
        '',
        section('Warnings', deckWarnings(stats, membership).join('\n')),
      ].join('\n');
    },
  };
}

/**
 * Every deck of a language with the size it resolves to today. This is the
 * listing an operator reaches for first, and it costs a membership resolution
 * per deck, which is why it prints the day it used at the top rather than
 * leaving the reader to assume.
 */
export function decksCommand(app: Application): Command {
  return {
    name: 'decks',
    describe: 'list the decks of a language with the size each one resolves to',
    usage: 'stemwright decks --language latin',
    flags: { language: languageFlag, on: dayFlag },
    async run(args) {
      const language = requireFlag(args, 'language');
      await requireLanguage(app.stores, language);
      const on = parseCivilDate(stringFlag(args, 'on') ?? app.clock.today(), 'on');

      const context = await app.drill.context(language);
      const lexemes = allLexemes(context.lexicon);
      const decks = await app.decks.list(language);

      const rows = decks
        .map((entry) => {
          const stats = deckStats(entry, resolveMembership(context, entry, on), lexemes);
          return [
            entry.key,
            String(stats.lexemes),
            String(stats.cells),
            bundleImbalance(stats).toFixed(2),
            describeScope(entry.scope),
          ];
        })
        .sort((a, b) => (a[0] ?? '').localeCompare(b[0] ?? ''));

      return [
        `${decks.length} decks in ${language} on ${on}`,
        '',
        table(
          [
            { header: 'deck' },
            { header: 'lexemes', align: 'right' },
            { header: 'cells', align: 'right' },
            { header: 'imbalance', align: 'right' },
            { header: 'scope' },
          ],
          rows,
        ),
      ].join('\n');
    },
  };
}
