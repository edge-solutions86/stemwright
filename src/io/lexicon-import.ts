import { ValidationError, isStemwrightError } from '../domain/errors.js';
import type { ErrorDetail } from '../domain/errors.js';
import type { Lexeme, LexemeContext } from '../domain/lexicon/lexeme.js';
import { lexeme } from '../domain/lexicon/lexeme.js';
import { stemSet } from '../domain/lexicon/stem.js';
import type { StemInput } from '../domain/lexicon/stem.js';
import { normaliseInput } from '../domain/text/normalise.js';
import type { CsvTable } from './csv.js';
import { readTable, requireColumns } from './csv.js';

/**
 * Importing a lexicon from a spreadsheet, which is how every one of these
 * arrives.
 *
 * The run never stops at the first bad row. A department sends a file of four
 * hundred nouns and wants the list of what is wrong with it, not the first
 * thing; an import that fails at row seven means seven rounds of editing. So
 * every row is tried, the failures are collected with their line numbers, and
 * the caller decides whether to keep what parsed.
 */
export const REQUIRED_COLUMNS = ['lemma', 'class', 'stem'] as const;

export interface ImportRow {
  readonly line: number;
  readonly lemma: string;
  readonly problems: readonly ErrorDetail[];
}

export interface ImportReport {
  readonly accepted: readonly Lexeme[];
  readonly rejected: readonly ImportRow[];
  readonly total: number;
}

function stemsFrom(row: Readonly<Record<string, string>>): StemInput[] {
  const stems: StemInput[] = [{ role: 'base', form: row['stem'] ?? '' }];
  for (const [column, value] of Object.entries(row)) {
    if (!column.startsWith('stem.') || normaliseInput(value) === '') {
      continue;
    }
    stems.push({ role: column.slice('stem.'.length), form: value });
  }
  return stems;
}

function listFrom(value: string | undefined): string[] {
  if (value === undefined || normaliseInput(value) === '') {
    return [];
  }
  return value
    .split(';')
    .map((part) => normaliseInput(part))
    .filter((part) => part !== '');
}

function inherentFrom(row: Readonly<Record<string, string>>): Record<string, string> {
  const inherent: Record<string, string> = {};
  for (const [column, value] of Object.entries(row)) {
    if (column.startsWith('inherent.') && normaliseInput(value) !== '') {
      inherent[column.slice('inherent.'.length)] = normaliseInput(value);
    }
  }
  return inherent;
}

function problemsOf(error: unknown): ErrorDetail[] {
  if (isStemwrightError(error) && error.details.length > 0) {
    return [...error.details];
  }
  return [{ path: 'row', message: error instanceof Error ? error.message : String(error) }];
}

export function importTable(context: LexemeContext, table: CsvTable): ImportReport {
  requireColumns(table, [...REQUIRED_COLUMNS]);

  const accepted: Lexeme[] = [];
  const rejected: ImportRow[] = [];

  for (const [index, row] of table.rows.entries()) {
    const line = index + 2;
    const lemma = row['lemma'] ?? '';
    try {
      accepted.push(
        lexeme(context, {
          lemma,
          classKey: row['class'] ?? '',
          stems: stemSet(stemsFrom(row)),
          inherent: inherentFrom(row),
          glosses: listFrom(row['gloss']),
          tags: listFrom(row['tags']),
          ...(row['homograph'] === undefined || row['homograph'] === ''
            ? {}
            : { homograph: Number(row['homograph']) }),
        }),
      );
    } catch (error) {
      rejected.push({ line, lemma, problems: problemsOf(error) });
    }
  }

  return { accepted, rejected, total: table.rows.length };
}

export function importCsv(context: LexemeContext, text: string): ImportReport {
  return importTable(context, readTable(text));
}

export function isClean(report: ImportReport): boolean {
  return report.rejected.length === 0;
}

/** Refuses unless every row parsed, for a caller that wants all or nothing. */
export function requireClean(report: ImportReport): readonly Lexeme[] {
  if (!isClean(report)) {
    throw new ValidationError(`${report.rejected.length} of ${report.total} rows were rejected`, {
      details: report.rejected.flatMap((row) =>
        row.problems.map((problem) => ({
          path: `line ${row.line}.${problem.path}`,
          message: problem.message,
        })),
      ),
    });
  }
  return report.accepted;
}

export function describeImport(report: ImportReport): string[] {
  const lines = [`${report.accepted.length} of ${report.total} rows are usable`];
  for (const row of report.rejected) {
    const summary = row.problems.map((problem) => `${problem.path} ${problem.message}`).join('; ');
    lines.push(`line ${row.line} ${row.lemma === '' ? '(no lemma)' : row.lemma}: ${summary}`);
  }
  return lines;
}

/** Rows whose lemma and homograph collide with something already stored. */
export function collisionsWith(report: ImportReport, existing: readonly Lexeme[]): string[] {
  const seen = new Set(existing.map((entry) => `${entry.lemma.toLowerCase()}/${entry.homograph}`));
  return report.accepted
    .filter((entry) => seen.has(`${entry.lemma.toLowerCase()}/${entry.homograph}`))
    .map((entry) => entry.lemma)
    .sort();
}
