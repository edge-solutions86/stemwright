import { createApplication } from '../app/application.js';
import { allCommands } from '../cli/commands.js';
import type { Program } from '../cli/program.js';
import { EXIT_OK, run } from '../cli/program.js';
import { memoryStores } from '../infra/memory.js';

/**
 * The cli entry point. It builds the program, runs one invocation and prints
 * the result, and that is the whole file.
 *
 * Output goes to stdout on success and to stderr on failure, so a pipeline can
 * take the table without taking the error message with it. The exit code is
 * whatever the dispatcher decided, and nothing here throws.
 */
export const VERSION = '0.6.0';

export function buildProgram(): Program {
  return {
    name: 'stemwright',
    version: VERSION,
    commands: allCommands(createApplication(memoryStores())),
  };
}

export interface Streams {
  readonly out: (text: string) => void;
  readonly err: (text: string) => void;
}

export async function main(argv: readonly string[], streams: Streams): Promise<number> {
  const result = await run(buildProgram(), argv, { write: streams.out, now: new Date() });
  if (result.output !== '') {
    (result.code === EXIT_OK ? streams.out : streams.err)(result.output);
  }
  return result.code;
}

if (process.argv[1]?.endsWith('stemwright.js') === true) {
  void main(process.argv.slice(2), {
    out: (text) => process.stdout.write(`${text}\n`),
    err: (text) => process.stderr.write(`${text}\n`),
  }).then((code) => {
    process.exitCode = code;
  });
}
