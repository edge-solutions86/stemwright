import type { Clock } from '../app/ports.js';
import type { Actor } from '../domain/course/authz.js';
import { ValidationError } from '../domain/errors.js';
import type { CivilDate } from '../domain/time/civil.js';
import { parseCivilDate } from '../domain/time/civil.js';
import type { FlagSpec, ParsedArgs } from './args.js';
import { numberFlag, stringFlag } from './args.js';

/**
 * The flags more than one command declares, and the readers that turn them into
 * the values the layer below wants.
 *
 * Every command used to write its own `--on` handling, and they had drifted:
 * one of them fell back to the clock through a helper, another through a nullish
 * coalesce, and the two would not have stayed the same for long. A day is the
 * argument this whole tool is organised around, so there is exactly one place
 * that decides what happens when it is left off.
 */
export const languageFlag: FlagSpec = {
  kind: 'string',
  describe: 'the language key',
  required: true,
};

export const deckFlag: FlagSpec = {
  kind: 'string',
  alias: 'd',
  describe: 'the deck key',
  required: true,
};

export const learnerFlag: FlagSpec = {
  kind: 'string',
  alias: 'l',
  describe: 'the learner id',
  required: true,
};

export const dayFlag: FlagSpec = {
  kind: 'string',
  describe: 'the civil day to resolve against',
};

export const actorFlag: FlagSpec = {
  kind: 'string',
  describe: 'the instructor id doing the work',
};

/**
 * The day the command should resolve against. Left off, it is today according
 * to the clock the application was built with, which is the clock every service
 * below also reads. A command that reached for its own idea of today would be a
 * second clock in one process, and a queue built on one and marked against the
 * other is a bug nobody would find from the symptom.
 */
export function dayFrom(clock: Clock, args: ParsedArgs, name = 'on'): CivilDate {
  return parseCivilDate(stringFlag(args, name) ?? clock.today(), name);
}

/**
 * A local operator has already been trusted by the file system, so the cli
 * names an instructor rather than authenticating one. The id is worth taking
 * from a flag anyway, because it is what lands in the journal and `cli` in
 * every entry tells a later reader nothing.
 */
export function actorFrom(args: ParsedArgs, name = 'actor'): Actor {
  return { id: stringFlag(args, name) ?? 'cli', role: 'instructor' };
}

/** A count flag that has to be a whole number above zero if it is given. */
export function countFrom(args: ParsedArgs, name: string, fallback: number): number {
  const value = numberFlag(args, name);
  if (value === undefined) {
    return fallback;
  }
  if (!Number.isInteger(value) || value < 1) {
    throw ValidationError.field(name, `must be a whole number above zero, not ${value}`);
  }
  return value;
}
