import { describe, expect, it } from 'vitest';
import { NotFoundError, ValidationError } from '../../src/domain/errors.js';
import type { Command, CommandContext, Program } from '../../src/cli/program.js';
import {
  EXIT_FAILED,
  EXIT_MISUSED,
  EXIT_OK,
  commandHelp,
  commandNamed,
  helpText,
  messageOf,
  run,
} from '../../src/cli/program.js';

const context: CommandContext = {
  write: () => undefined,
  now: new Date('2025-03-03T09:00:00Z'),
};

const echo: Command = {
  name: 'echo',
  describe: 'print what it was given',
  usage: 'stemwright echo --text <text>',
  flags: { text: { kind: 'string', describe: 'what to print', required: true } },
  run: (args) => Promise.resolve(String(args.flags['text'])),
};

const explode: Command = {
  name: 'explode',
  describe: 'always fails',
  usage: 'stemwright explode',
  flags: {},
  run: () => Promise.reject(new NotFoundError('deck', 'all')),
};

const program: Program = { name: 'stemwright', version: '1.2.3', commands: [echo, explode] };

describe('commandNamed', () => {
  it('finds a command and reports a miss', () => {
    expect(commandNamed(program, 'echo')).toBe(echo);
    expect(commandNamed(program, 'nowhere')).toBeUndefined();
  });
});

describe('help', () => {
  it('lists every command', () => {
    const text = helpText(program);
    expect(text).toContain('stemwright 1.2.3');
    expect(text).toContain('echo');
    expect(text).toContain('explode');
  });

  it('prints the options of one command', () => {
    const text = commandHelp(program, echo);
    expect(text).toContain('stemwright echo --text <text>');
    expect(text).toContain('--text');
    expect(text).toContain('(required)');
  });

  it('leaves the options block out when a command has none', () => {
    expect(commandHelp(program, explode)).not.toContain('Options:');
  });
});

describe('run', () => {
  it('runs a command and returns its output', async () => {
    const result = await run(program, ['echo', '--text', 'hello'], context);
    expect(result).toEqual({ code: EXIT_OK, output: 'hello' });
  });

  it('prints help for no arguments and exits misused', async () => {
    const result = await run(program, [], context);
    expect(result.code).toBe(EXIT_MISUSED);
    expect(result.output).toContain('Usage:');
  });

  it('prints help on request and exits cleanly', async () => {
    expect((await run(program, ['help'], context)).code).toBe(EXIT_OK);
    expect((await run(program, ['--help'], context)).code).toBe(EXIT_OK);
  });

  it('prints help for one command', async () => {
    const result = await run(program, ['help', 'echo'], context);
    expect(result.code).toBe(EXIT_OK);
    expect(result.output).toContain('--text');
  });

  it('refuses help for a command nobody declared', async () => {
    const result = await run(program, ['help', 'nowhere'], context);
    expect(result.code).toBe(EXIT_MISUSED);
    expect(result.output).toContain('there is no command named nowhere');
  });

  it('prints the version', async () => {
    expect((await run(program, ['--version'], context)).output).toBe('stemwright 1.2.3');
    expect((await run(program, ['version'], context)).output).toBe('stemwright 1.2.3');
  });

  it('exits misused for an unknown command', async () => {
    expect((await run(program, ['nowhere'], context)).code).toBe(EXIT_MISUSED);
  });

  it('exits misused when the arguments do not parse', async () => {
    const result = await run(program, ['echo'], context);
    expect(result.code).toBe(EXIT_MISUSED);
    expect(result.output).toContain('text is required');
  });

  it('exits failed when the command itself throws', async () => {
    const result = await run(program, ['explode'], context);
    expect(result.code).toBe(EXIT_FAILED);
    expect(result.output).toContain('deck all was not found');
  });

  it('keeps the two failure codes apart', async () => {
    const misused = await run(program, ['echo'], context);
    const failed = await run(program, ['explode'], context);
    expect(misused.code).not.toBe(failed.code);
  });
});

describe('messageOf', () => {
  it('lists the details of a validation failure', () => {
    const error = new ValidationError('two problems', {
      details: [
        { path: 'a', message: 'is wrong' },
        { path: 'b', message: 'is wrong' },
      ],
    });
    expect(messageOf(error)).toBe('two problems\n  a is wrong\n  b is wrong');
  });

  it('takes the message of an ordinary error', () => {
    expect(messageOf(new TypeError('boom'))).toBe('boom');
  });

  it('stringifies anything else', () => {
    expect(messageOf('a string')).toBe('a string');
  });
});
