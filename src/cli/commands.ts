import type { Application } from '../app/application.js';
import { requireLanguage } from '../app/loaders.js';
import { NotFoundError } from '../domain/errors.js';
import { citationForm } from '../domain/lexicon/lexeme.js';
import { requireLexeme } from '../domain/lexicon/lexicon.js';
import { STANDARD_LADDER } from '../domain/mastery/ladder.js';
import { emptyLedger, ledgerCounts } from '../domain/mastery/ledger.js';
import { profileFor } from '../domain/paradigm/book.js';
import { buildTable, tableToText } from '../domain/paradigm/table.js';
import { coverageByBundle, maturityOf, weakestRows } from '../domain/report/coverage.js';
import { parseCivilDate } from '../domain/time/civil.js';
import { routeTable } from '../http/api.js';
import { deckCommand, decksCommand } from './deck-command.js';
import { importCommand } from './import-command.js';
import { requireFlag, stringFlag } from './args.js';
import type { Command } from './program.js';
import { bar, keyValues, percent, table } from './render.js';

/**
 * The commands. Each one loads what it needs, asks the domain, and formats the
 * answer, and none of them holds a rule of its own.
 *
 * `paradigm` is the command this cli exists for. An instructor checking why a
 * learner was marked wrong wants the table the learner was graded against, on
 * the day they were graded, and that is one call once the revision resolution
 * lives in the layer below.
 *
 * Nothing here goes through the report service, because that service takes a
 * course scope so it can check who is asking. A local operator running this
 * binary has already been trusted by the file system, and inventing a fake
 * scope to satisfy a check that means nothing here would make the real check
 * look optional.
 */
const dayFlag = { kind: 'string' as const, describe: 'the civil day to resolve against' };
const languageFlag = { kind: 'string' as const, describe: 'the language key', required: true };
const deckFlag = { kind: 'string' as const, alias: 'd', describe: 'the deck key', required: true };
const learnerFlag = {
  kind: 'string' as const,
  alias: 'l',
  describe: 'the learner id',
  required: true,
};

function dayOr(value: string | undefined, fallback: string): string {
  return value ?? fallback;
}

export function queueCommand(app: Application): Command {
  return {
    name: 'queue',
    describe: 'show what a learner would be asked today',
    usage: 'stemwright queue --language latin --deck all --learner lrn_one',
    flags: { language: languageFlag, deck: deckFlag, learner: learnerFlag, on: dayFlag },
    async run(args) {
      const on = parseCivilDate(dayOr(stringFlag(args, 'on'), app.clock.today()), 'on');
      const queue = await app.drill.queue(
        requireFlag(args, 'learner'),
        requireFlag(args, 'language'),
        requireFlag(args, 'deck'),
        on,
      );
      return table(
        [{ header: 'bundle' }, { header: 'kind' }, { header: 'overdue', align: 'right' }],
        queue.entries.map((entry) => [entry.cell.bundle, entry.kind, String(entry.overdueDays)]),
      );
    },
  };
}

export function paradigmCommand(app: Application): Command {
  return {
    name: 'paradigm',
    describe: 'print the table a lexeme was graded against on a day',
    usage: 'stemwright paradigm --language latin --lexeme lex_x --rows case --columns number',
    flags: {
      language: languageFlag,
      lexeme: { kind: 'string', describe: 'the lexeme id', required: true },
      rows: { kind: 'string', describe: 'the dimension down the side', required: true },
      columns: { kind: 'string', describe: 'the dimension across the top', required: true },
      on: dayFlag,
    },
    async run(args) {
      const language = requireFlag(args, 'language');
      const on = parseCivilDate(dayOr(stringFlag(args, 'on'), app.clock.today()), 'on');
      const bundle = await requireLanguage(app.stores, language);
      const context = await app.drill.context(language);
      const entry = requireLexeme(context.lexicon, requireFlag(args, 'lexeme'));
      const resolved = profileFor(context.book, context.classes, entry.classKey, on);
      if (resolved === undefined) {
        throw new NotFoundError('profile', `${entry.classKey} on ${on}`);
      }

      const grid = buildTable(bundle.schema, bundle.rules, entry, resolved.revision.profile, {
        rowDimension: requireFlag(args, 'rows'),
        columnDimension: requireFlag(args, 'columns'),
      });
      const heading = `${citationForm(entry)} under ${resolved.revision.profileKey} revision ${resolved.revision.ordinal}`;
      return [heading, '', tableToText(grid)].join('\n');
    },
  };
}

export function coverageCommand(app: Application): Command {
  return {
    name: 'coverage',
    describe: 'show how much of a deck a learner holds',
    usage: 'stemwright coverage --language latin --deck all --learner lrn_one',
    flags: { language: languageFlag, deck: deckFlag, learner: learnerFlag, on: dayFlag },
    async run(args) {
      const learnerId = requireFlag(args, 'learner');
      const on = parseCivilDate(dayOr(stringFlag(args, 'on'), app.clock.today()), 'on');
      const membership = await app.drill.membership(
        requireFlag(args, 'language'),
        requireFlag(args, 'deck'),
        on,
      );
      const ledger = (await app.stores.ledgers.get(learnerId)) ?? emptyLedger(learnerId);
      const counts = ledgerCounts(
        ledger,
        membership.cells.map((cell) => cell.id),
        STANDARD_LADDER,
        on,
      );
      const rows = coverageByBundle(membership.cells, ledger, STANDARD_LADDER, on);

      const summary = keyValues([
        ['cells', String(counts.known)],
        ['introduced', String(counts.introduced)],
        ['mature', String(counts.mature)],
        ['due', String(counts.due)],
        ['suspended', String(counts.suspended)],
      ]);
      const weakest = table(
        [{ header: 'bundle' }, { header: 'maturity' }, { header: 'share', align: 'right' }],
        weakestRows(rows).map((row) => [
          row.key,
          bar(maturityOf(row), 12),
          percent(maturityOf(row)),
        ]),
      );
      return [summary, '', weakest].join('\n');
    },
  };
}

export function lexemesCommand(app: Application): Command {
  return {
    name: 'lexemes',
    describe: 'list or search the lexicon of a language',
    usage: 'stemwright lexemes --language latin --search girl',
    flags: {
      language: languageFlag,
      search: { kind: 'string', alias: 's', describe: 'match a lemma or a gloss' },
    },
    async run(args) {
      const language = requireFlag(args, 'language');
      // Checked before the listing, so a language nobody configured fails
      // rather than printing an empty table that reads like an empty lexicon.
      await requireLanguage(app.stores, language);
      const term = stringFlag(args, 'search');
      const entries =
        term === undefined
          ? [...(await app.lexicon.load(language)).byId.values()]
          : await app.lexicon.search(language, term);
      return table(
        [{ header: 'lemma' }, { header: 'class' }, { header: 'gloss' }],
        entries.map((entry) => [citationForm(entry), entry.classKey, entry.glosses[0] ?? '']),
      );
    },
  };
}

/**
 * Prints what the library exports and what the http layer serves, so an
 * operator can see the surface of a running build without reading its source.
 */
export function routesCommand(app: Application): Command {
  return {
    name: 'routes',
    describe: 'list the http routes this build serves',
    usage: 'stemwright routes',
    flags: {},
    run() {
      const directory = { resolve: () => Promise.resolve(undefined) };
      const lines = routeTable(app, { directory });
      return Promise.resolve(
        table(
          [{ header: 'method' }, { header: 'path' }],
          lines.map((line) => {
            const space = line.indexOf(' ');
            return [line.slice(0, space), line.slice(space + 1)];
          }),
        ),
      );
    },
  };
}

export function allCommands(app: Application): Command[] {
  return [
    queueCommand(app),
    paradigmCommand(app),
    coverageCommand(app),
    lexemesCommand(app),
    routesCommand(app),
    importCommand(app),
    deckCommand(app),
    decksCommand(app),
  ];
}
