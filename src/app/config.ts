import { ProblemCollector, ValidationError } from '../domain/errors.js';
import type { DrillPolicyPatch } from '../domain/drill/policy.js';
import { drillPolicy } from '../domain/drill/policy.js';
import { asSlug } from '../domain/identifiers.js';
import type { Json } from '../infra/codec.js';
import { isObject, readOptionalNumber, readOptionalString, requireObject } from '../infra/codec.js';

/**
 * Configuration, read from one object rather than from the environment
 * directly, so that a deployment can put it in a file, a secret store or the
 * environment and nothing downstream has to know which.
 *
 * Every field has a default and every default is a working one. A process that
 * refuses to start because a tuning parameter is absent is a process that will
 * be started with a wrong value instead.
 */
export interface Config {
  readonly dataDir: string;
  readonly port: number;
  readonly version: string;
  readonly defaultLanguage: string;
  readonly policy: DrillPolicyPatch;
  readonly snapshotEvery: number;
  readonly bodyLimit: number;
}

export const DEFAULT_CONFIG: Config = {
  dataDir: '.stemwright-data',
  port: 8080,
  version: 'dev',
  defaultLanguage: 'latin',
  policy: {},
  snapshotEvery: 500,
  bodyLimit: 1_000_000,
};

function readPort(problems: ProblemCollector, value: number | undefined): number {
  if (value === undefined) {
    return DEFAULT_CONFIG.port;
  }
  if (!Number.isInteger(value) || value < 0 || value > 65535) {
    problems.add('port', `is not a port number: ${String(value)}`);
    return DEFAULT_CONFIG.port;
  }
  return value;
}

function readPositive(
  problems: ProblemCollector,
  name: string,
  value: number | undefined,
  fallback: number,
): number {
  if (value === undefined) {
    return fallback;
  }
  if (!Number.isInteger(value) || value < 1) {
    problems.add(name, `must be a whole number above zero: ${String(value)}`);
    return fallback;
  }
  return value;
}

function readPolicy(problems: ProblemCollector, raw: Json | undefined): DrillPolicyPatch {
  if (raw === undefined || raw === null) {
    return {};
  }
  if (!isObject(raw)) {
    problems.add('policy', 'is not an object');
    return {};
  }
  const patch: DrillPolicyPatch = {};
  for (const name of ['newPerDay', 'reviewsPerDay', 'perLexemeCap', 'rowSpacing'] as const) {
    const value = raw[name];
    if (value === undefined) {
      continue;
    }
    if (typeof value !== 'number') {
      problems.add(`policy.${name}`, 'is not a number');
      continue;
    }
    patch[name] = value;
  }
  if (raw['buryTwins'] !== undefined) {
    if (typeof raw['buryTwins'] !== 'boolean') {
      problems.add('policy.buryTwins', 'is not a boolean');
    } else {
      patch.buryTwins = raw['buryTwins'];
    }
  }
  problems.absorbFlat('policy', () => drillPolicy(patch));
  return patch;
}

export function readConfig(raw: unknown): Config {
  const problems = new ProblemCollector();
  const row = requireObject('config', raw);

  const config: Config = {
    dataDir: readOptionalString(row, 'config', 'dataDir') ?? DEFAULT_CONFIG.dataDir,
    port: readPort(problems, readOptionalNumber(row, 'config', 'port')),
    version: readOptionalString(row, 'config', 'version') ?? DEFAULT_CONFIG.version,
    defaultLanguage:
      row['defaultLanguage'] === undefined
        ? DEFAULT_CONFIG.defaultLanguage
        : asSlug('defaultLanguage', readOptionalString(row, 'config', 'defaultLanguage') ?? ''),
    policy: readPolicy(problems, row['policy']),
    snapshotEvery: readPositive(
      problems,
      'snapshotEvery',
      readOptionalNumber(row, 'config', 'snapshotEvery'),
      DEFAULT_CONFIG.snapshotEvery,
    ),
    bodyLimit: readPositive(
      problems,
      'bodyLimit',
      readOptionalNumber(row, 'config', 'bodyLimit'),
      DEFAULT_CONFIG.bodyLimit,
    ),
  };

  problems.throwIfAny('the configuration is not usable');
  return config;
}

export function parseConfigText(text: string): Config {
  if (text.trim() === '') {
    return DEFAULT_CONFIG;
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new ValidationError('the configuration file is not valid json');
  }
  return readConfig(parsed);
}

/** Reads the few settings a container is likely to pass as variables. */
export function fromEnvironment(env: Readonly<Record<string, string | undefined>>): Config {
  const row: Record<string, Json> = {};
  if (env['STEMWRIGHT_DATA_DIR'] !== undefined) {
    row['dataDir'] = env['STEMWRIGHT_DATA_DIR'];
  }
  if (env['STEMWRIGHT_PORT'] !== undefined) {
    row['port'] = Number(env['STEMWRIGHT_PORT']);
  }
  if (env['STEMWRIGHT_VERSION'] !== undefined) {
    row['version'] = env['STEMWRIGHT_VERSION'];
  }
  if (env['STEMWRIGHT_LANGUAGE'] !== undefined) {
    row['defaultLanguage'] = env['STEMWRIGHT_LANGUAGE'];
  }
  return readConfig(row);
}

export function describeConfig(config: Config): string[] {
  return [
    `data directory ${config.dataDir}`,
    `port ${config.port}`,
    `version ${config.version}`,
    `default language ${config.defaultLanguage}`,
    `snapshot every ${config.snapshotEvery} journal entries`,
  ];
}
