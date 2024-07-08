import { ValidationError } from '../errors.js';
import type { FeatureBundle } from '../features/bundle.js';
import { bundleKey, enumerateBundles, valueOf } from '../features/bundle.js';
import type { FeatureSchema } from '../features/schema.js';
import { requireDimension } from '../features/schema.js';
import type { Lexeme } from '../lexicon/lexeme.js';
import { contradictsInherent } from '../lexicon/lexeme.js';
import type { ParadigmProfile } from './profile.js';
import type { RuleSet } from './realise.js';
import { realiseSlot } from './realise.js';

/**
 * A paradigm is learnt as a grid, not as a list. The grid is chosen by picking
 * one dimension for the rows and one for the columns, which is what a textbook
 * does when it prints case down the side and number across the top.
 *
 * Any dimension the grid does not use has to be fixed to one value, otherwise a
 * cell of the grid would hold several forms and the table would be lying about
 * which one it is showing.
 */
export interface TableCell {
  readonly bundle: string;
  readonly form: string | undefined;
  readonly blocked: boolean;
}

export interface ParadigmTable {
  readonly rowDimension: string;
  readonly columnDimension: string;
  readonly rowValues: readonly string[];
  readonly columnValues: readonly string[];
  readonly fixed: FeatureBundle;
  readonly cells: readonly (readonly TableCell[])[];
}

export interface TableRequest {
  readonly rowDimension: string;
  readonly columnDimension: string;
  readonly fixed?: FeatureBundle;
}

function pairsOf(bundle: FeatureBundle | undefined): Record<string, string> {
  const out: Record<string, string> = {};
  for (const pair of bundle?.pairs ?? []) {
    out[pair.dimension] = pair.value;
  }
  return out;
}

export function buildTable(
  schema: FeatureSchema,
  rules: RuleSet,
  entry: Lexeme,
  profile: ParadigmProfile,
  request: TableRequest,
): ParadigmTable {
  if (request.rowDimension === request.columnDimension) {
    throw ValidationError.field('table', 'cannot use one dimension for both rows and columns');
  }
  const rows = requireDimension(schema, request.rowDimension);
  const columns = requireDimension(schema, request.columnDimension);
  const fixed = pairsOf(request.fixed);

  for (const dimension of schema.dimensions) {
    if (dimension.key === rows.key || dimension.key === columns.key || dimension.optional) {
      continue;
    }
    if (!(dimension.key in fixed)) {
      throw ValidationError.field(
        'table',
        `leaves ${dimension.key} open, which would put several forms in one cell`,
      );
    }
  }

  const cells: TableCell[][] = [];
  for (const rowValue of rows.values) {
    const row: TableCell[] = [];
    for (const columnValue of columns.values) {
      const wanted = { ...fixed, [rows.key]: rowValue.key, [columns.key]: columnValue.key };
      const bundle = enumerateBundles(schema).find((candidate) =>
        Object.entries(wanted).every(
          ([dimension, value]) => valueOf(candidate, dimension) === value,
        ),
      );
      if (bundle === undefined) {
        row.push({ bundle: '', form: undefined, blocked: true });
        continue;
      }
      const key = bundleKey(bundle);
      const slot = profile.slots.get(key);
      const blocked = contradictsInherent(entry, bundle);
      row.push({
        bundle: key,
        form: slot === undefined || blocked ? undefined : realiseSlot(rules, entry, slot).form,
        blocked,
      });
    }
    cells.push(row);
  }

  return {
    rowDimension: rows.key,
    columnDimension: columns.key,
    rowValues: rows.values.map((value) => value.key),
    columnValues: columns.values.map((value) => value.key),
    fixed: request.fixed ?? { pairs: [] },
    cells,
  };
}

export function tableCell(
  table: ParadigmTable,
  rowValue: string,
  columnValue: string,
): TableCell | undefined {
  const rowIndex = table.rowValues.indexOf(rowValue);
  const columnIndex = table.columnValues.indexOf(columnValue);
  if (rowIndex < 0 || columnIndex < 0) {
    return undefined;
  }
  return table.cells[rowIndex]?.[columnIndex];
}

export function filledCount(table: ParadigmTable): number {
  return table.cells.flat().filter((cell) => cell.form !== undefined).length;
}

export function blockedCount(table: ParadigmTable): number {
  return table.cells.flat().filter((cell) => cell.blocked).length;
}

export function holeCount(table: ParadigmTable): number {
  return table.cells.flat().filter((cell) => cell.form === undefined && !cell.blocked).length;
}

/** Rows whose every filled cell holds the same form, which is worth flagging. */
export function flatRows(table: ParadigmTable): string[] {
  const flat: string[] = [];
  for (const [index, row] of table.cells.entries()) {
    const forms = row.map((cell) => cell.form).filter((form): form is string => form !== undefined);
    if (forms.length > 1 && new Set(forms).size === 1) {
      flat.push(table.rowValues[index]!);
    }
  }
  return flat;
}

export function renderTable(table: ParadigmTable, blank = '-'): string[][] {
  const header = ['', ...table.columnValues];
  const body = table.cells.map((row, index) => [
    table.rowValues[index]!,
    ...row.map((cell) => cell.form ?? blank),
  ]);
  return [header, ...body];
}

export function tableToText(table: ParadigmTable, blank = '-'): string {
  const grid = renderTable(table, blank);
  const widths = grid[0]!.map((_, column) =>
    Math.max(...grid.map((row) => (row[column] ?? '').length)),
  );
  return grid
    .map((row) =>
      row
        .map((value, column) => value.padEnd(widths[column]!))
        .join('  ')
        .trimEnd(),
    )
    .join('\n');
}
