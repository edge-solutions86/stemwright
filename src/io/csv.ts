import { ValidationError } from '../domain/errors.js';

/**
 * Comma separated values, written out rather than installed, because the shape
 * this project needs is small and every csv library disagrees about the same
 * three things: what a quote inside a quoted field means, whether a trailing
 * newline makes an empty last row, and what to do with a row of the wrong
 * width.
 *
 * The answers here are the ones a spreadsheet gives. A doubled quote inside a
 * quoted field is one quote, a trailing newline is not a row, and a row of the
 * wrong width is an error naming the line, because a lexicon import that
 * silently drops a column produces a lexeme with the wrong gloss and nobody
 * finds out until a learner is marked wrong.
 */
export function formatField(value: string): string {
  if (/[",\r\n]/.test(value)) {
    return `"${value.replace(/"/g, '""')}"`;
  }
  return value;
}

export function formatRow(cells: readonly string[]): string {
  return cells.map(formatField).join(',');
}

export function formatCsv(rows: readonly (readonly string[])[]): string {
  return rows.length === 0 ? '' : `${rows.map(formatRow).join('\n')}\n`;
}

interface ParseState {
  readonly rows: string[][];
  row: string[];
  field: string;
  inQuotes: boolean;
  sawQuote: boolean;
}

export function parseCsv(text: string): string[][] {
  const state: ParseState = { rows: [], row: [], field: '', inQuotes: false, sawQuote: false };
  const normalised = text.replace(/\r\n/g, '\n').replace(/\r/g, '\n');

  for (let index = 0; index < normalised.length; index += 1) {
    const character = normalised[index]!;

    if (state.inQuotes) {
      if (character === '"') {
        if (normalised[index + 1] === '"') {
          state.field += '"';
          index += 1;
        } else {
          state.inQuotes = false;
        }
        continue;
      }
      state.field += character;
      continue;
    }

    if (character === '"') {
      if (state.field !== '') {
        throw new ValidationError(
          `a quote opens in the middle of a field on line ${state.rows.length + 1}`,
        );
      }
      state.inQuotes = true;
      state.sawQuote = true;
      continue;
    }
    if (character === ',') {
      state.row.push(state.field);
      state.field = '';
      state.sawQuote = false;
      continue;
    }
    if (character === '\n') {
      state.row.push(state.field);
      state.rows.push(state.row);
      state.row = [];
      state.field = '';
      state.sawQuote = false;
      continue;
    }
    state.field += character;
  }

  if (state.inQuotes) {
    throw new ValidationError('the file ends inside a quoted field');
  }
  if (state.field !== '' || state.row.length > 0 || state.sawQuote) {
    state.row.push(state.field);
    state.rows.push(state.row);
  }
  return state.rows;
}

export interface CsvTable {
  readonly headers: readonly string[];
  readonly rows: readonly Readonly<Record<string, string>>[];
}

/**
 * Reads a file with a header row into named records. Every row has to be the
 * width of the header, and a row that is not names its line number.
 */
export function readTable(text: string): CsvTable {
  const grid = parseCsv(text);
  const headers = grid[0];
  if (headers === undefined) {
    throw new ValidationError('the file has no header row');
  }
  if (new Set(headers).size !== headers.length) {
    throw new ValidationError('the header row names a column twice');
  }

  const rows = grid.slice(1).map((cells, index) => {
    if (cells.length !== headers.length) {
      throw new ValidationError(
        `line ${index + 2} has ${cells.length} fields where the header has ${headers.length}`,
      );
    }
    const row: Record<string, string> = {};
    for (const [column, header] of headers.entries()) {
      row[header] = cells[column]!;
    }
    return row;
  });

  return { headers, rows };
}

export function writeTable(
  headers: readonly string[],
  rows: readonly Readonly<Record<string, string>>[],
): string {
  return formatCsv([headers, ...rows.map((row) => headers.map((header) => row[header] ?? ''))]);
}

export function requireColumns(table: CsvTable, wanted: readonly string[]): void {
  const missing = wanted.filter((column) => !table.headers.includes(column));
  if (missing.length > 0) {
    throw new ValidationError(`the file is missing the columns ${missing.join(', ')}`);
  }
}
