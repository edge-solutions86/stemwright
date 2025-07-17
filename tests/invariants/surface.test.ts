import { readFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { checkSurface, collectSurface, exportsIn, renderSurface } from '../../scripts/surface.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');

describe('exportsIn', () => {
  it('finds every kind of export', () => {
    const text = [
      'export function first(): void {}',
      'export const second = 1;',
      'export class Third {}',
      'export interface Fourth {}',
      'export type Fifth = string;',
    ].join('\n');
    expect(exportsIn(text).map((entry) => entry.name)).toEqual([
      'Fifth',
      'Fourth',
      'Third',
      'first',
      'second',
    ]);
  });

  it('sorts by code point so the file is the same on every machine', () => {
    const lines = ['export const beta = 1;', 'export const Alpha = 2;'];
    expect(exportsIn(lines.join(String.fromCodePoint(0x0a))).map((entry) => entry.name)).toEqual([
      'Alpha',
      'beta',
    ]);
  });

  it('ignores a line that only mentions the word export', () => {
    expect(exportsIn('// this module does not export anything')).toEqual([]);
    expect(exportsIn('const exported = 1;')).toEqual([]);
  });

  it('ignores a re export of another module', () => {
    expect(exportsIn("export * from './other.js';")).toEqual([]);
  });

  it('reads the kind alongside the name', () => {
    expect(exportsIn('export function build(): void {}')).toEqual([
      { kind: 'function', name: 'build' },
    ]);
  });
});

describe('collectSurface', () => {
  it('finds a surface worth checking', async () => {
    const modules = await collectSurface();
    expect(modules.length).toBeGreaterThan(40);
    expect(modules.every((entry) => entry.names.length > 0)).toBe(true);
  });

  it('leaves the barrel and the entry points out', async () => {
    const modules = await collectSurface();
    const names = modules.map((entry) => entry.module);
    expect(names).not.toContain('src/index.ts');
    expect(names.some((name) => name.startsWith('src/bin/'))).toBe(false);
  });

  it('names modules by a forward slashed path whatever the platform', async () => {
    const modules = await collectSurface();
    const backslash = String.fromCodePoint(0x5c);
    expect(modules.every((entry) => !entry.module.includes(backslash))).toBe(true);
  });
});

describe('renderSurface', () => {
  it('counts the modules and the names', () => {
    const text = renderSurface([
      { module: 'src/a.ts', names: [{ kind: 'function', name: 'one' }] },
      { module: 'src/b.ts', names: [{ kind: 'const', name: 'two' }] },
    ]);
    expect(text).toContain('2 modules export 2 names.');
    expect(text).toContain('## src/a.ts');
    expect(text).toContain('- `function one`');
  });

  it('ends with exactly one newline', () => {
    const text = renderSurface([{ module: 'src/a.ts', names: [{ kind: 'const', name: 'x' }] }]);
    expect(text.endsWith('\n')).toBe(true);
    expect(text.endsWith('\n\n')).toBe(false);
  });
});

describe('the checked in surface', () => {
  it('matches what the source exports', async () => {
    const result = await checkSurface();
    expect(result.reason).toBe('the surface file matches the source');
    expect(result.ok).toBe(true);
  });

  it('is a file somebody can read', async () => {
    const text = await readFile(join(root, 'docs', 'surface.md'), 'utf8');
    expect(text.startsWith('# Public surface')).toBe(true);
    expect(text).toContain('Do not edit by hand.');
  });
});
