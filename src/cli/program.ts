import { isStemwrightError } from '../domain/errors.js';
import type { FlagSpecs, ParsedArgs } from './args.js';
import { formatFlags, parseArgs } from './args.js';

/**
 * The command table and the dispatcher.
 *
 * A command returns its output rather than printing it, and the exit code comes
 * from whether it threw. That is what makes every command testable without
 * capturing a stream, and it is why nothing below this line writes to a stream
 * of its own.
 */
export interface CommandContext {
  readonly write: (line: string) => void;
  readonly now: Date;
}

export interface Command {
  readonly name: string;
  readonly describe: string;
  readonly flags: FlagSpecs;
  readonly usage: string;
  run(args: ParsedArgs, context: CommandContext): Promise<string>;
}

export interface Program {
  readonly name: string;
  readonly version: string;
  readonly commands: readonly Command[];
}

export const EXIT_OK = 0;
export const EXIT_FAILED = 1;
export const EXIT_MISUSED = 2;

export function commandNamed(program: Program, name: string): Command | undefined {
  return program.commands.find((command) => command.name === name);
}

export function helpText(program: Program): string {
  const width = Math.max(...program.commands.map((command) => command.name.length));
  const lines = program.commands
    .map((command) => `  ${command.name.padEnd(width)}  ${command.describe}`)
    .sort();
  return [
    `${program.name} ${program.version}`,
    '',
    'Usage:',
    `  ${program.name} <command> [options]`,
    '',
    'Commands:',
    ...lines,
    '',
    `Run ${program.name} help <command> for the options of one command.`,
  ].join('\n');
}

export function commandHelp(program: Program, command: Command): string {
  const flags = formatFlags(command.flags);
  return [
    `${program.name} ${command.name}`,
    '',
    command.describe,
    '',
    'Usage:',
    `  ${command.usage}`,
    ...(flags.length === 0 ? [] : ['', 'Options:', ...flags]),
  ].join('\n');
}

export interface RunResult {
  readonly code: number;
  readonly output: string;
}

/**
 * Runs one invocation. Every failure becomes an exit code and a line on the
 * error stream, and the two codes are kept apart: a command that was called
 * wrongly exits 2, and a command that ran and failed exits 1. A caller that
 * cannot tell those apart cannot retry safely.
 */
export async function run(
  program: Program,
  argv: readonly string[],
  context: CommandContext,
): Promise<RunResult> {
  const [name, ...rest] = argv;

  if (name === undefined || name === 'help' || name === '--help' || name === '-h') {
    const target = rest[0];
    if (target !== undefined) {
      const command = commandNamed(program, target);
      if (command === undefined) {
        return { code: EXIT_MISUSED, output: `there is no command named ${target}` };
      }
      return { code: EXIT_OK, output: commandHelp(program, command) };
    }
    return { code: name === undefined ? EXIT_MISUSED : EXIT_OK, output: helpText(program) };
  }

  if (name === '--version' || name === 'version') {
    return { code: EXIT_OK, output: `${program.name} ${program.version}` };
  }

  const command = commandNamed(program, name);
  if (command === undefined) {
    return { code: EXIT_MISUSED, output: `there is no command named ${name}` };
  }

  let parsed: ParsedArgs;
  try {
    parsed = parseArgs(rest, command.flags);
  } catch (error) {
    return { code: EXIT_MISUSED, output: messageOf(error) };
  }

  try {
    return { code: EXIT_OK, output: await command.run(parsed, context) };
  } catch (error) {
    return { code: EXIT_FAILED, output: messageOf(error) };
  }
}

export function messageOf(error: unknown): string {
  if (isStemwrightError(error)) {
    const details = error.details.map((detail) => `  ${detail.path} ${detail.message}`);
    return [error.message, ...details].join('\n');
  }
  return error instanceof Error ? error.message : String(error);
}
