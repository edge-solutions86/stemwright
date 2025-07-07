import { describe, expect, it } from 'vitest';
import {
  DEFAULT_CONFIG,
  describeConfig,
  fromEnvironment,
  parseConfigText,
  readConfig,
} from '../../src/app/config.js';
import { ValidationError } from '../../src/domain/errors.js';
import { problemMessages } from '../support/problems.js';

describe('readConfig', () => {
  it('takes the defaults for an empty object', () => {
    expect(readConfig({})).toEqual(DEFAULT_CONFIG);
  });

  it('reads every field it recognises', () => {
    const config = readConfig({
      dataDir: '/var/stemwright',
      port: 9000,
      version: '1.2.3',
      defaultLanguage: 'Greek',
      snapshotEvery: 100,
      bodyLimit: 2000,
    });
    expect(config.dataDir).toBe('/var/stemwright');
    expect(config.port).toBe(9000);
    expect(config.version).toBe('1.2.3');
    expect(config.defaultLanguage).toBe('greek');
    expect(config.snapshotEvery).toBe(100);
    expect(config.bodyLimit).toBe(2000);
  });

  it('refuses something that is not an object', () => {
    expect(() => readConfig('nope')).toThrow(/config is not an object/);
  });

  it('refuses a port that is not one', () => {
    expect(problemMessages(() => readConfig({ port: 70000 }))).toEqual([
      'port is not a port number: 70000',
    ]);
    expect(problemMessages(() => readConfig({ port: 1.5 }))).toHaveLength(1);
  });

  it('refuses a snapshot interval below one', () => {
    expect(problemMessages(() => readConfig({ snapshotEvery: 0 }))).toEqual([
      'snapshotEvery must be a whole number above zero: 0',
    ]);
  });

  it('collects several problems at once', () => {
    expect(problemMessages(() => readConfig({ port: -1, bodyLimit: 0 }))).toHaveLength(2);
  });
});

describe('the policy block', () => {
  it('reads a patch', () => {
    expect(readConfig({ policy: { newPerDay: 4, buryTwins: false } }).policy).toEqual({
      newPerDay: 4,
      buryTwins: false,
    });
  });

  it('reads an absent policy as an empty patch', () => {
    expect(readConfig({}).policy).toEqual({});
  });

  it('refuses a policy that is not an object', () => {
    expect(problemMessages(() => readConfig({ policy: 'nope' }))).toEqual([
      'policy is not an object',
    ]);
  });

  it('refuses a field of the wrong type', () => {
    expect(problemMessages(() => readConfig({ policy: { newPerDay: 'lots' } }))).toEqual([
      'policy.newPerDay is not a number',
    ]);
    expect(problemMessages(() => readConfig({ policy: { buryTwins: 'yes' } }))).toEqual([
      'policy.buryTwins is not a boolean',
    ]);
  });

  it('refuses a patch the policy rules reject', () => {
    expect(problemMessages(() => readConfig({ policy: { newPerDay: -1 } }))).toHaveLength(1);
  });

  it('ignores a field it does not recognise', () => {
    expect(readConfig({ policy: { nonsense: 4 } }).policy).toEqual({});
  });
});

describe('parseConfigText', () => {
  it('reads an empty file as the defaults', () => {
    expect(parseConfigText('')).toEqual(DEFAULT_CONFIG);
    expect(parseConfigText('   ')).toEqual(DEFAULT_CONFIG);
  });

  it('reads a json file', () => {
    expect(parseConfigText(JSON.stringify({ port: 9000 })).port).toBe(9000);
  });

  it('refuses text that is not json', () => {
    expect(() => parseConfigText('{')).toThrow(ValidationError);
    expect(() => parseConfigText('{')).toThrow(/not valid json/);
  });
});

describe('fromEnvironment', () => {
  it('takes the defaults for an empty environment', () => {
    expect(fromEnvironment({})).toEqual(DEFAULT_CONFIG);
  });

  it('reads the variables it recognises', () => {
    const config = fromEnvironment({
      STEMWRIGHT_DATA_DIR: '/data',
      STEMWRIGHT_PORT: '9000',
      STEMWRIGHT_VERSION: '2.0.0',
      STEMWRIGHT_LANGUAGE: 'greek',
    });
    expect(config).toMatchObject({
      dataDir: '/data',
      port: 9000,
      version: '2.0.0',
      defaultLanguage: 'greek',
    });
  });

  it('refuses a port that is not a number', () => {
    expect(() => fromEnvironment({ STEMWRIGHT_PORT: 'nope' })).toThrow(ValidationError);
  });

  it('ignores a variable it does not recognise', () => {
    expect(fromEnvironment({ STEMWRIGHT_NONSENSE: 'x' })).toEqual(DEFAULT_CONFIG);
  });
});

describe('describeConfig', () => {
  it('reads the settings out', () => {
    const lines = describeConfig(DEFAULT_CONFIG);
    expect(lines[0]).toBe('data directory .stemwright-data');
    expect(lines).toHaveLength(5);
  });
});
