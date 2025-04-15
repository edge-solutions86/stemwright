import { ValidationError } from '../domain/errors.js';

/**
 * Argument parsing, written rather than installed. The grammar is small and
 * fixed, and every argument parser worth depending on brings a help formatter
 * and a colour library with it, neither of which this cli wants.
 *
 * Two rules keep it predictable. A flag needs a declaration, so a typo is an
 * error rather than a silently ignored option. And `--` ends the flags, so a
 * lemma that begins with a dash can still be typed.
 */
export type FlagKind = 'string' | 'boolean' | 'number';

export interface FlagSpec {
  readonly kind: FlagKind;
  readonly alias?: string;
  readonly describe: string;
  readonly required?: boolean;
}

export type FlagSpecs = Readonly<Record<string, FlagSpec>>;

export interface ParsedArgs {
  readonly positionals: readonly string[];
  readonly flags: Readonly<Record<string, string | number | boolean>>;
}

function resolveName(specs: FlagSpecs, token: string): string {
  if (token in specs) {
    return token;
  }
  for (const [name, spec] of Object.entries(specs)) {
    if (spec.alias === token) {
      return name;
    }
  }
  throw ValidationError.field('flag', `is not an option of this command: ${token}`);
}

function coerce(name: string, spec: FlagSpec, raw: string): string | number | boolean {
  if (spec.kind === 'string') {
    return raw;
  }
  if (spec.kind === 'number') {
    const value = Number(raw);
    if (!Number.isFinite(value)) {
      throw ValidationError.field(name, `is not a number: ${raw}`);
    }
    return value;
  }
  if (raw === 'true' || raw === 'false') {
    return raw === 'true';
  }
  throw ValidationError.field(name, `is a flag and takes true or false, not ${raw}`);
}

export function parseArgs(argv: readonly string[], specs: FlagSpecs): ParsedArgs {
  const positionals: string[] = [];
  const flags: Record<string, string | number | boolean> = {};
  let literal = false;

  for (let index = 0; index < argv.length; index += 1) {
    const token = argv[index]!;

    if (literal || !token.startsWith('-') || token === '-') {
      positionals.push(token);
      continue;
    }
    if (token === '--') {
      literal = true;
      continue;
    }

    const trimmed = token.replace(/^--?/, '');
    const equals = trimmed.indexOf('=');
    const rawName = equals < 0 ? trimmed : trimmed.slice(0, equals);
    const name = resolveName(specs, rawName);
    const spec = specs[name]!;

    if (equals >= 0) {
      flags[name] = coerce(name, spec, trimmed.slice(equals + 1));
      continue;
    }
    if (spec.kind === 'boolean') {
      flags[name] = true;
      continue;
    }

    const next = argv[index + 1];
    if (next === undefined || (next.startsWith('-') && next !== '-')) {
      throw ValidationError.field(name, 'needs a value');
    }
    flags[name] = coerce(name, spec, next);
    index += 1;
  }

  for (const [name, spec] of Object.entries(specs)) {
    if (spec.required === true && !(name in flags)) {
      throw ValidationError.field(name, 'is required');
    }
  }

  return { positionals, flags };
}

export function stringFlag(parsed: ParsedArgs, name: string): string | undefined {
  const value = parsed.flags[name];
  return typeof value === 'string' ? value : undefined;
}

export function numberFlag(parsed: ParsedArgs, name: string): number | undefined {
  const value = parsed.flags[name];
  return typeof value === 'number' ? value : undefined;
}

export function booleanFlag(parsed: ParsedArgs, name: string): boolean {
  return parsed.flags[name] === true;
}

export function requireFlag(parsed: ParsedArgs, name: string): string {
  const value = stringFlag(parsed, name);
  if (value === undefined) {
    throw ValidationError.field(name, 'is required');
  }
  return value;
}

export function formatFlags(specs: FlagSpecs): string[] {
  return Object.entries(specs)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([name, spec]) => {
      const alias = spec.alias === undefined ? '' : `, -${spec.alias}`;
      const required = spec.required === true ? ' (required)' : '';
      return `  --${name}${alias}  ${spec.describe}${required}`;
    });
}
