import { readFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { argv, exit, stdout } from 'node:process';

/**
 * Checks that the three places a version is written agree with one another.
 *
 * They drift, always in the same way: the package version is bumped as part of
 * a release and the constant the cli prints is not, so a support conversation
 * starts with a version number that names a build nobody shipped.
 */
const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, '..');

export function versionFromSource(text) {
  const match = /export const VERSION = '([^']+)';/.exec(text);
  return match === null ? undefined : match[1];
}

export function changelogMentions(text, version) {
  return text.includes(`## ${version}`) || text.includes('## Unreleased');
}

export function binariesIn(pkg) {
  return Object.entries(pkg.bin ?? {}).map(([name, path]) => ({ name, path }));
}

export function checkAgreement(pkg, sourceVersion, changelog) {
  const problems = [];

  if (typeof pkg.version !== 'string' || pkg.version === '') {
    problems.push('package.json has no version');
  }
  if (sourceVersion === undefined) {
    problems.push('src/bin/stemwright.ts does not export a VERSION constant');
  } else if (sourceVersion !== pkg.version) {
    problems.push(`package.json says ${pkg.version} and the cli says ${sourceVersion}`);
  }
  if (!changelogMentions(changelog, pkg.version)) {
    problems.push(`CHANGELOG.md has no section for ${pkg.version} and none for Unreleased`);
  }
  for (const binary of binariesIn(pkg)) {
    if (!binary.path.startsWith('dist/')) {
      problems.push(`the ${binary.name} binary does not point into dist`);
    }
  }
  for (const field of ['main', 'types']) {
    if (typeof pkg[field] !== 'string' || !pkg[field].startsWith('dist/')) {
      problems.push(`${field} does not point into dist`);
    }
  }

  return problems;
}

export async function checkRelease() {
  const pkg = JSON.parse(await readFile(join(root, 'package.json'), 'utf8'));
  const source = await readFile(join(root, 'src', 'bin', 'stemwright.ts'), 'utf8');
  const changelog = await readFile(join(root, 'CHANGELOG.md'), 'utf8');
  const problems = checkAgreement(pkg, versionFromSource(source), changelog);
  return { ok: problems.length === 0, problems, version: pkg.version };
}

if (argv[1]?.endsWith('release.mjs') === true) {
  const result = await checkRelease();
  if (result.ok) {
    stdout.write(`version ${result.version} agrees everywhere\n`);
    exit(0);
  }
  for (const problem of result.problems) {
    stdout.write(`${problem}\n`);
  }
  exit(1);
}
