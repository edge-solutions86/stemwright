import { describe, expect, it } from 'vitest';
import { ValidationError } from '../../src/domain/errors.js';
import type { FlagSpecs } from '../../src/cli/args.js';
import {
  booleanFlag,
  formatFlags,
  numberFlag,
  parseArgs,
  requireFlag,
  stringFlag,
} from '../../src/cli/args.js';

const specs: FlagSpecs = {
  deck: { kind: 'string', alias: 'd', describe: 'the deck to drill' },
  limit: { kind: 'number', describe: 'how many questions' },
  verbose: { kind: 'boolean', alias: 'v', describe: 'say more' },
  learner: { kind: 'string', describe: 'whose queue', required: true },
};

const parse = (argv: string[]) => parseArgs(argv, specs);

describe('positionals', () => {
  it('collects arguments that are not flags', () => {
    expect(parse(['drill', 'latin', '--learner', 'lrn_one']).positionals).toEqual([
      'drill',
      'latin',
    ]);
  });

  it('treats a bare dash as a positional', () => {
    expect(parse(['-', '--learner', 'lrn_one']).positionals).toEqual(['-']);
  });

  it('stops reading flags after a double dash', () => {
    const parsed = parse(['--learner', 'lrn_one', '--', '--deck', 'all']);
    expect(parsed.positionals).toEqual(['--deck', 'all']);
    expect(stringFlag(parsed, 'deck')).toBeUndefined();
  });
});

describe('flags', () => {
  it('reads a value after the flag', () => {
    expect(stringFlag(parse(['--learner', 'lrn_one', '--deck', 'all']), 'deck')).toBe('all');
  });

  it('reads a value joined by an equals sign', () => {
    expect(stringFlag(parse(['--learner=lrn_one', '--deck=all']), 'deck')).toBe('all');
  });

  it('reads an alias', () => {
    expect(stringFlag(parse(['--learner', 'lrn_one', '-d', 'all']), 'deck')).toBe('all');
  });

  it('reads a boolean flag with no value', () => {
    expect(booleanFlag(parse(['--learner', 'lrn_one', '--verbose']), 'verbose')).toBe(true);
    expect(booleanFlag(parse(['--learner', 'lrn_one']), 'verbose')).toBe(false);
  });

  it('reads an explicit boolean value', () => {
    expect(booleanFlag(parse(['--learner', 'lrn_one', '--verbose=false']), 'verbose')).toBe(false);
  });

  it('reads a number', () => {
    expect(numberFlag(parse(['--learner', 'lrn_one', '--limit', '12']), 'limit')).toBe(12);
  });

  it('lets a later flag win', () => {
    expect(stringFlag(parse(['--learner', 'a', '--learner', 'b']), 'learner')).toBe('b');
  });
});

describe('failures', () => {
  it('refuses a flag nobody declared', () => {
    expect(() => parse(['--learner', 'lrn_one', '--nonsense', 'x'])).toThrow(
      /is not an option of this command: nonsense/,
    );
  });

  it('refuses a value flag with nothing after it', () => {
    expect(() => parse(['--learner'])).toThrow(/learner needs a value/);
  });

  it('refuses a value flag followed by another flag', () => {
    expect(() => parse(['--learner', '--verbose'])).toThrow(/needs a value/);
  });

  it('refuses a number that is not one', () => {
    expect(() => parse(['--learner', 'lrn_one', '--limit', 'lots'])).toThrow(/is not a number/);
  });

  it('refuses a boolean given something other than true or false', () => {
    expect(() => parse(['--learner', 'lrn_one', '--verbose=yes'])).toThrow(/takes true or false/);
  });

  it('refuses a missing required flag', () => {
    expect(() => parse(['drill'])).toThrow(/learner is required/);
  });
});

describe('accessors', () => {
  it('reads a required flag or refuses', () => {
    const parsed = parse(['--learner', 'lrn_one']);
    expect(requireFlag(parsed, 'learner')).toBe('lrn_one');
    expect(() => requireFlag(parsed, 'deck')).toThrow(ValidationError);
  });

  it('gives nothing when a flag is of another kind', () => {
    const parsed = parse(['--learner', 'lrn_one', '--limit', '3']);
    expect(stringFlag(parsed, 'limit')).toBeUndefined();
    expect(numberFlag(parsed, 'learner')).toBeUndefined();
  });
});

describe('formatFlags', () => {
  it('lists the flags in name order with their aliases', () => {
    const lines = formatFlags(specs);
    expect(lines[0]).toBe('  --deck, -d  the deck to drill');
    expect(lines.some((line) => line.includes('(required)'))).toBe(true);
  });
});
