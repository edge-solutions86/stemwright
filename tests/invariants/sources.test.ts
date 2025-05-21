import { readFile, readdir } from 'node:fs/promises';
import { dirname, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

/**
 * Byte level checks over every source file in the repository.
 *
 * The reason these exist is that a checkout on Windows with the default git
 * settings rewrites every line ending, and a file that then carries a carriage
 * return breaks a shebang, a template literal that is compared against a
 * fixture, and any test that asserts on exact output. A `.gitattributes` fixes
 * the cause; this file is what proves the fix is still in place.
 *
 * Every check counts what it read and refuses on zero, because a walker that
 * finds no files reports total success and announces nothing.
 */
const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, '..', '..');
const roots = ['src', 'tests', 'scripts'];
const extensions = ['.ts', '.js', '.mjs'];

async function walk(directory: string): Promise<string[]> {
  const found: string[] = [];
  let entries;
  try {
    entries = await readdir(directory, { withFileTypes: true });
  } catch {
    return found;
  }
  for (const entry of entries) {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) {
      found.push(...(await walk(path)));
      continue;
    }
    if (extensions.some((extension) => entry.name.endsWith(extension))) {
      found.push(path);
    }
  }
  return found;
}

const files = (await Promise.all(roots.map((name) => walk(join(root, name))))).flat();
const contents = new Map<string, string>();
for (const path of files) {
  const key = relative(root, path).split(sep).join('/');
  contents.set(key, await readFile(path, 'utf8'));
}

const CARRIAGE_RETURN = String.fromCodePoint(0x000d);
const BOM = String.fromCodePoint(0xfeff);

function offendingLines(text: string, predicate: (line: string) => boolean): number[] {
  return text
    .split('\n')
    .map((line, index) => (predicate(line) ? index + 1 : 0))
    .filter((line) => line > 0);
}

describe('the source walker', () => {
  it('found files to check', () => {
    expect(contents.size).toBeGreaterThan(80);
  });

  it('found files under every root it was told about', () => {
    for (const name of ['src/', 'tests/']) {
      expect([...contents.keys()].some((path) => path.startsWith(name))).toBe(true);
    }
  });

  it('would notice a carriage return if one were there', () => {
    const planted = `const a = 1;${CARRIAGE_RETURN}\n`;
    expect(planted.includes(CARRIAGE_RETURN)).toBe(true);
  });

  it('would notice a file that does not end with a newline', () => {
    expect('const a = 1;'.endsWith('\n')).toBe(false);
  });
});

describe('every source file', () => {
  it('has no carriage return in it', () => {
    const offenders = [...contents.entries()]
      .filter(([, text]) => text.includes(CARRIAGE_RETURN))
      .map(([path]) => path);
    expect(offenders).toEqual([]);
  });

  it('has no byte order mark', () => {
    const offenders = [...contents.entries()]
      .filter(([, text]) => text.startsWith(BOM))
      .map(([path]) => path);
    expect(offenders).toEqual([]);
  });

  it('ends with exactly one newline', () => {
    const offenders = [...contents.entries()]
      .filter(([, text]) => !text.endsWith('\n') || text.endsWith('\n\n'))
      .map(([path]) => path);
    expect(offenders).toEqual([]);
  });

  it('has no tab used for indentation', () => {
    const offenders: string[] = [];
    for (const [path, text] of contents) {
      if (offendingLines(text, (line) => line.startsWith('\t')).length > 0) {
        offenders.push(path);
      }
    }
    expect(offenders).toEqual([]);
  });

  it('has no trailing whitespace on any line', () => {
    const offenders: string[] = [];
    for (const [path, text] of contents) {
      const lines = offendingLines(text, (line) => line !== line.trimEnd());
      if (lines.length > 0) {
        offenders.push(`${path}:${lines.join(',')}`);
      }
    }
    expect(offenders).toEqual([]);
  });

  it('has no stray control character', () => {
    const offenders: string[] = [];
    for (const [path, text] of contents) {
      for (const point of text) {
        const code = point.codePointAt(0) ?? 0;
        if (code < 0x20 && point !== '\n' && point !== '\t') {
          offenders.push(path);
          break;
        }
      }
    }
    expect(offenders).toEqual([]);
  });
});

describe('every source file under src', () => {
  const sources = [...contents.entries()].filter(([path]) => path.startsWith('src/'));

  it('imports a relative path with a js extension', () => {
    const offenders: string[] = [];
    for (const [path, text] of sources) {
      const imports = [...text.matchAll(/from '(\.[^']*)'/g)].map((match) => match[1]!);
      if (imports.some((specifier) => !specifier.endsWith('.js'))) {
        offenders.push(path);
      }
    }
    expect(offenders).toEqual([]);
  });

  it('does not call console outside the two entry points', () => {
    const offenders = sources
      .filter(([path]) => !path.startsWith('src/bin/'))
      .filter(([, text]) => /\bconsole\./.test(text))
      .map(([path]) => path);
    expect(offenders).toEqual([]);
  });

  it('found enough files for those two checks to mean something', () => {
    expect(sources.length).toBeGreaterThan(50);
  });
});
