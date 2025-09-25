import { describe, expect, it } from 'vitest';
import { fixedClock } from '../../src/app/ports.js';
import { parseArgs } from '../../src/cli/args.js';
import type { FlagSpecs, ParsedArgs } from '../../src/cli/args.js';
import {
  actorFlag,
  actorFrom,
  countFrom,
  dayFlag,
  dayFrom,
  deckFlag,
  languageFlag,
  learnerFlag,
} from '../../src/cli/flags.js';
import { ValidationError } from '../../src/domain/errors.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';

const TODAY = parseCivilDate('2024-10-21');
const clock = fixedClock(new Date('2024-10-21T09:00:00Z'), TODAY);

const specs: FlagSpecs = {
  on: dayFlag,
  actor: actorFlag,
  spread: { kind: 'number', describe: 'how many to list' },
  since: dayFlag,
};

const parse = (argv: readonly string[]): ParsedArgs => parseArgs(argv, specs);

describe('the shared flag specs', () => {
  it('marks the three flags a command cannot run without', () => {
    for (const spec of [languageFlag, deckFlag, learnerFlag]) {
      expect(spec.required).toBe(true);
      expect(spec.kind).toBe('string');
    }
  });

  it('leaves the day and the actor optional', () => {
    expect(dayFlag.required).toBeUndefined();
    expect(actorFlag.required).toBeUndefined();
  });

  it('gives the two most typed flags a short alias and keeps them apart', () => {
    expect(deckFlag.alias).toBe('d');
    expect(learnerFlag.alias).toBe('l');
    expect(languageFlag.alias).toBeUndefined();
  });

  it('describes every one of them', () => {
    for (const spec of [languageFlag, deckFlag, learnerFlag, dayFlag, actorFlag]) {
      expect(spec.describe.length).toBeGreaterThan(4);
    }
  });
});

describe('dayFrom', () => {
  it('reads the day off the flag', () => {
    expect(dayFrom(clock, parse(['--on', '2025-03-04']))).toBe('2025-03-04');
  });

  it('falls back to the clock the application was built with', () => {
    expect(dayFrom(clock, parse([]))).toBe(TODAY);
  });

  it('reads a flag under another name', () => {
    expect(dayFrom(clock, parse(['--since', '2023-12-31']), 'since')).toBe('2023-12-31');
  });

  it('refuses a day that is not a day', () => {
    expect(() => dayFrom(clock, parse(['--on', 'yesterday']))).toThrow(ValidationError);
  });

  it('names the flag it refused rather than the one it reads by default', () => {
    try {
      dayFrom(clock, parse(['--since', '2024-13-40']), 'since');
      expect.unreachable('dayFrom should have refused');
    } catch (error) {
      expect((error as ValidationError).details[0]?.path).toBe('since');
    }
  });
});

describe('actorFrom', () => {
  it('takes the id off the flag', () => {
    expect(actorFrom(parse(['--actor', 'lrn_teacher']))).toEqual({
      id: 'lrn_teacher',
      role: 'instructor',
    });
  });

  it('falls back to a name that says where the write came from', () => {
    expect(actorFrom(parse([])).id).toBe('cli');
  });

  it('always claims the instructor role', () => {
    expect(actorFrom(parse(['--actor', 'lrn_one'])).role).toBe('instructor');
  });
});

describe('countFrom', () => {
  it('takes the number off the flag', () => {
    expect(countFrom(parse(['--spread', '7']), 'spread', 3)).toBe(7);
  });

  it('falls back when the flag was left off', () => {
    expect(countFrom(parse([]), 'spread', 3)).toBe(3);
  });

  it('refuses a count of zero or below', () => {
    expect(() => countFrom(parse(['--spread', '0']), 'spread', 3)).toThrow(
      /must be a whole number above zero/,
    );
    expect(() => countFrom(parse(['--spread', '-2']), 'spread', 3)).toThrow(ValidationError);
  });

  it('refuses a count that is not whole', () => {
    expect(() => countFrom(parse(['--spread', '2.5']), 'spread', 3)).toThrow(/not 2.5/);
  });
});
