import { describe, expect, it } from 'vitest';
import {
  binariesIn,
  changelogMentions,
  checkAgreement,
  checkRelease,
  versionFromSource,
} from '../../scripts/release.mjs';

const good = {
  version: '1.2.3',
  main: 'dist/index.js',
  types: 'dist/index.d.ts',
  bin: { stemwright: 'dist/bin/stemwright.js' },
};

describe('versionFromSource', () => {
  it('reads the exported constant', () => {
    expect(versionFromSource("export const VERSION = '1.2.3';")).toBe('1.2.3');
  });

  it('gives nothing when there is none', () => {
    expect(versionFromSource('const VERSION = 1;')).toBeUndefined();
  });
});

describe('changelogMentions', () => {
  it('accepts a section for the version', () => {
    expect(changelogMentions('# Changelog\n\n## 1.2.3\n', '1.2.3')).toBe(true);
  });

  it('accepts an unreleased section', () => {
    expect(changelogMentions('# Changelog\n\n## Unreleased\n', '1.2.3')).toBe(true);
  });

  it('refuses a changelog with neither', () => {
    expect(changelogMentions('# Changelog\n\n## 1.0.0\n', '1.2.3')).toBe(false);
  });
});

describe('binariesIn', () => {
  it('lists the declared binaries', () => {
    expect(binariesIn(good)).toEqual([{ name: 'stemwright', path: 'dist/bin/stemwright.js' }]);
  });

  it('lists nothing when there are none', () => {
    expect(binariesIn({})).toEqual([]);
  });
});

describe('checkAgreement', () => {
  const changelog = '## Unreleased\n';

  it('passes when everything agrees', () => {
    expect(checkAgreement(good, '1.2.3', changelog)).toEqual([]);
  });

  it('names a version mismatch', () => {
    expect(checkAgreement(good, '1.0.0', changelog)).toEqual([
      'package.json says 1.2.3 and the cli says 1.0.0',
    ]);
  });

  it('names a missing version constant', () => {
    expect(checkAgreement(good, undefined, changelog)[0]).toContain('VERSION constant');
  });

  it('names a changelog with no section', () => {
    expect(checkAgreement(good, '1.2.3', '# Changelog\n')[0]).toContain('no section for 1.2.3');
  });

  it('names a binary that does not point into dist', () => {
    const broken = { ...good, bin: { stemwright: 'src/bin/stemwright.ts' } };
    expect(checkAgreement(broken, '1.2.3', changelog)[0]).toContain('does not point into dist');
  });

  it('names main and types that do not point into dist', () => {
    const broken = { ...good, main: 'src/index.ts', types: 'src/index.ts' };
    expect(checkAgreement(broken, '1.2.3', changelog)).toHaveLength(2);
  });
});

describe('the checked in release metadata', () => {
  it('agrees everywhere', async () => {
    const result = await checkRelease();
    expect(result.problems).toEqual([]);
    expect(result.ok).toBe(true);
  });

  it('names a version that looks like one', async () => {
    expect((await checkRelease()).version).toMatch(/^\d+\.\d+\.\d+$/);
  });
});
