import type { Application } from '../app/application.js';
import { requireLanguage } from '../app/loaders.js';
import { resolveMembership } from '../domain/drill/membership.js';
import { PolicyError } from '../domain/errors.js';
import type { Finding } from '../domain/report/health.js';
import { checkHealth, countLevels, describeHealth, isHealthy } from '../domain/report/health.js';
import { booleanFlag, requireFlag } from './args.js';
import { dayFlag, dayFrom, languageFlag } from './flags.js';
import type { Command } from './program.js';
import { keyValues, section, table } from './render.js';

/**
 * The sweep somebody runs before a term starts.
 *
 * It exits non zero when it found an error, so it can be a step in a deployment
 * rather than something a person has to read. `--strict` widens that to
 * warnings, which is what a repository of teaching material wants in its own
 * ci, where a shadowed binding is a mistake even though nothing is broken yet.
 */
function findingRows(findings: readonly Finding[]): string[][] {
  return findings.map((finding) => [finding.level, finding.area, finding.message]);
}

export function doctorCommand(app: Application): Command {
  return {
    name: 'doctor',
    describe: 'check a language for the holes that leave a learner with nothing to drill',
    usage: 'stemwright doctor --language latin --strict',
    flags: {
      language: languageFlag,
      on: dayFlag,
      strict: { kind: 'boolean', describe: 'treat a warning as a failure too' },
    },
    async run(args) {
      const language = requireFlag(args, 'language');
      await requireLanguage(app.stores, language);
      const on = dayFrom(app.clock, args);

      const context = await app.drill.context(language);
      const decks = (await app.decks.list(language)).map((deck) => ({
        deck,
        membership: resolveMembership(context, deck, on),
      }));

      const findings = checkHealth({ context, decks, on });
      const counts = countLevels(findings);
      const strict = booleanFlag(args, 'strict');

      const body =
        findings.length === 0
          ? section('Findings', '')
          : section(
              'Findings',
              table(
                [{ header: 'level' }, { header: 'area' }, { header: 'what was found' }],
                findingRows(findings),
              ),
            );

      const lines = [
        describeHealth(findings, language),
        '',
        keyValues([
          ['on', on],
          ['classes', String(context.classes.classes.size)],
          ['profiles', String(context.book.histories.size)],
          ['lexemes', String(context.lexicon.byId.size)],
          ['decks', String(decks.length)],
        ]),
        '',
        body,
      ];

      // The report is carried on the error rather than printed ahead of it, so
      // a caller reading only the failure stream still gets the whole of it.
      if (!isHealthy(findings)) {
        const tail = `${counts.error} errors stop a class drilling`;
        throw new PolicyError('doctor.errors', [...lines, '', tail].join('\n'));
      }
      if (strict && counts.warning > 0) {
        const tail = `${counts.warning} warnings, and strict was asked for`;
        throw new PolicyError('doctor.strict', [...lines, '', tail].join('\n'));
      }
      return lines.join('\n');
    },
  };
}
