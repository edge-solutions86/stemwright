import { describe, expect, it } from 'vitest';
import { VERSION, buildProgram, main } from '../../src/bin/stemwright.js';
import { EXIT_MISUSED, EXIT_OK } from '../../src/cli/program.js';

const capture = () => {
  const out: string[] = [];
  const err: string[] = [];
  return {
    out,
    err,
    streams: { out: (t: string) => out.push(t), err: (t: string) => err.push(t) },
  };
};

describe('buildProgram', () => {
  it('names itself and carries the version', () => {
    const program = buildProgram();
    expect(program.name).toBe('stemwright');
    expect(program.version).toBe(VERSION);
  });

  it('carries the four commands', () => {
    expect(buildProgram().commands).toHaveLength(4);
  });
});

describe('main', () => {
  it('prints help and exits misused with no arguments', async () => {
    const { out, err, streams } = capture();
    expect(await main([], streams)).toBe(EXIT_MISUSED);
    expect(err.join('\n')).toContain('Usage:');
    expect(out).toEqual([]);
  });

  it('prints help to stdout when it was asked for', async () => {
    const { out, streams } = capture();
    expect(await main(['help'], streams)).toBe(EXIT_OK);
    expect(out.join('\n')).toContain('Commands:');
  });

  it('prints the version', async () => {
    const { out, streams } = capture();
    await main(['--version'], streams);
    expect(out[0]).toBe(`stemwright ${VERSION}`);
  });

  it('sends a failure to stderr and nothing to stdout', async () => {
    const { out, err, streams } = capture();
    const code = await main(['lexemes', '--language', 'latin'], streams);
    expect(code).not.toBe(EXIT_OK);
    expect(out).toEqual([]);
    expect(err.join('\n')).toContain('latin');
  });

  it('sends a misuse to stderr', async () => {
    const { err, streams } = capture();
    expect(await main(['nowhere'], streams)).toBe(EXIT_MISUSED);
    expect(err.join('\n')).toContain('there is no command named nowhere');
  });
});
