import { codePointLength } from '../domain/text/normalise.js';

/**
 * Terminal output. Everything is plain text and nothing is coloured, because
 * the output of this cli is read as often through a pipe and a log file as it
 * is on a terminal, and escape codes in a log are noise a human has to learn to
 * ignore.
 *
 * Widths are measured in code points rather than utf-16 units. A Greek word
 * with a breathing is fewer characters than its length suggests, and a column
 * padded by the wrong count is a table that stops lining up exactly where the
 * interesting data is.
 */
export type Align = 'left' | 'right';

export interface Column {
  readonly header: string;
  readonly align?: Align;
}

export function padTo(value: string, width: number, align: Align = 'left'): string {
  const gap = Math.max(0, width - codePointLength(value));
  return align === 'right' ? ' '.repeat(gap) + value : value + ' '.repeat(gap);
}

export function columnWidths(rows: readonly (readonly string[])[]): number[] {
  const widths: number[] = [];
  for (const row of rows) {
    for (const [index, cell] of row.entries()) {
      widths[index] = Math.max(widths[index] ?? 0, codePointLength(cell));
    }
  }
  return widths;
}

/**
 * A table with a rule under the header. Rows shorter than the header are padded
 * out rather than refused, because a report that grew a column should still
 * print rather than crash while somebody is looking at it.
 */
export function table(columns: readonly Column[], rows: readonly (readonly string[])[]): string {
  const headers = columns.map((column) => column.header);
  const filled = rows.map((row) => columns.map((_, index) => row[index] ?? ''));
  const widths = columnWidths([headers, ...filled]);

  const line = (cells: readonly string[]) =>
    cells
      .map((cell, index) => padTo(cell, widths[index] ?? 0, columns[index]?.align ?? 'left'))
      .join('  ')
      .trimEnd();

  const rule = widths.map((width) => '-'.repeat(width)).join('  ');
  return [line(headers), rule, ...filled.map(line)].join('\n');
}

export function keyValues(pairs: readonly (readonly [string, string])[]): string {
  const width = Math.max(0, ...pairs.map(([key]) => codePointLength(key)));
  return pairs.map(([key, value]) => `${padTo(key, width)}  ${value}`).join('\n');
}

/** A share as a bar, for a coverage report that is read at a glance. */
export function bar(share: number, width = 20, filledChar = '#', emptyChar = '.'): string {
  const clamped = Math.max(0, Math.min(1, Number.isFinite(share) ? share : 0));
  const filled = Math.round(clamped * width);
  return filledChar.repeat(filled) + emptyChar.repeat(Math.max(0, width - filled));
}

export function percent(share: number, places = 0): string {
  const clamped = Number.isFinite(share) ? share : 0;
  return `${(clamped * 100).toFixed(places)}%`;
}

export function pluralise(count: number, singular: string, plural = `${singular}s`): string {
  return `${count} ${count === 1 ? singular : plural}`;
}

export function indent(text: string, spaces = 2): string {
  const prefix = ' '.repeat(spaces);
  return text
    .split('\n')
    .map((line) => (line === '' ? line : prefix + line))
    .join('\n');
}

export function section(title: string, body: string): string {
  return body === '' ? `${title}\n  nothing to show` : `${title}\n${indent(body)}`;
}

export function truncate(value: string, width: number): string {
  if (codePointLength(value) <= width) {
    return value;
  }
  if (width <= 1) {
    return [...value].slice(0, Math.max(0, width)).join('');
  }
  return `${[...value].slice(0, width - 1).join('')}~`;
}
