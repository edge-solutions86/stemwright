import { readFile } from 'node:fs/promises';
import type { Application } from '../app/application.js';
import { requireLanguage } from '../app/loaders.js';
import type { Actor } from '../domain/course/authz.js';
import { collisionsWith, describeImport, importCsv } from '../io/lexicon-import.js';
import { booleanFlag, requireFlag, stringFlag } from './args.js';
import type { Command } from './program.js';
import { section } from './render.js';

/**
 * Importing a word list from the command line.
 *
 * It is a dry run unless told otherwise. An import is the one operation here
 * that can put four hundred wrong records into a lexicon in one go, and the
 * shape of the mistake is nearly always in the file rather than in the tool, so
 * the default is to say what would happen and stop.
 */
export function importCommand(app: Application): Command {
  return {
    name: 'import',
    describe: 'read a csv word list and report what it would add',
    usage: 'stemwright import --language latin --file words.csv --apply',
    flags: {
      language: { kind: 'string', describe: 'the language key', required: true },
      file: { kind: 'string', alias: 'f', describe: 'the csv file to read', required: true },
      apply: { kind: 'boolean', describe: 'actually write the accepted rows' },
      actor: { kind: 'string', describe: 'the instructor id doing the import' },
    },
    async run(args) {
      const language = requireFlag(args, 'language');
      const bundle = await requireLanguage(app.stores, language);
      const text = await readFile(requireFlag(args, 'file'), 'utf8');

      const report = importCsv({ schema: bundle.schema, classes: bundle.classes }, text);
      const existing = await app.stores.lexemes.listByLanguage(language);
      const collisions = collisionsWith(report, existing);

      const lines = [section('Import', describeImport(report).join('\n'))];
      if (collisions.length > 0) {
        lines.push('', section('Already in the lexicon', collisions.join('\n')));
      }

      if (!booleanFlag(args, 'apply')) {
        lines.push('', 'This was a dry run. Pass --apply to write the accepted rows.');
        return lines.join('\n');
      }

      const actor: Actor = {
        id: stringFlag(args, 'actor') ?? 'cli',
        role: 'instructor',
      };
      let written = 0;
      for (const entry of report.accepted) {
        await app.lexicon
          .replace(actor, language, {
            id: entry.id,
            lemma: entry.lemma,
            classKey: entry.classKey,
            stems: entry.stems,
            inherent: Object.fromEntries(
              entry.inherent.pairs.map((pair) => [pair.dimension, pair.value]),
            ),
            glosses: entry.glosses,
            tags: entry.tags,
          })
          .catch(async () => {
            await app.lexicon.add(actor, language, {
              lemma: entry.lemma,
              classKey: entry.classKey,
              stems: entry.stems,
              inherent: Object.fromEntries(
                entry.inherent.pairs.map((pair) => [pair.dimension, pair.value]),
              ),
              glosses: entry.glosses,
              tags: entry.tags,
            });
          });
        written += 1;
      }

      lines.push('', `Wrote ${written} lexemes to ${language}.`);
      return lines.join('\n');
    },
  };
}
