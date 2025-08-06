import { bundleKey } from '../domain/features/bundle.js';
import type { Lexeme } from '../domain/lexicon/lexeme.js';
import { compareLexemes } from '../domain/lexicon/lexeme.js';
import { stemFor, stemRoles } from '../domain/lexicon/stem.js';
import { writeTable } from './csv.js';

/**
 * Writing a lexicon back out as the file it came in as.
 *
 * The columns are derived from what the data actually uses rather than fixed,
 * so a language with four stem roles gets four stem columns and one with a
 * single stem gets one. A fixed header would either lose the extra stems or
 * pad every export with empty columns for roles no language in the file has.
 *
 * Round tripping is the property that matters and it is tested: an export read
 * back by the importer produces the same lexemes.
 */
export const BASE_COLUMNS = ['lemma', 'class', 'stem', 'gloss', 'tags', 'homograph'] as const;

export function stemColumnsFor(entries: readonly Lexeme[]): string[] {
  const roles = new Set<string>();
  for (const entry of entries) {
    for (const role of stemRoles(entry.stems)) {
      if (role !== 'base') {
        roles.add(role);
      }
    }
  }
  return [...roles].sort().map((role) => `stem.${role}`);
}

export function inherentColumnsFor(entries: readonly Lexeme[]): string[] {
  const dimensions = new Set<string>();
  for (const entry of entries) {
    for (const pair of entry.inherent.pairs) {
      dimensions.add(pair.dimension);
    }
  }
  return [...dimensions].sort().map((dimension) => `inherent.${dimension}`);
}

export function columnsFor(entries: readonly Lexeme[]): string[] {
  return [...BASE_COLUMNS, ...stemColumnsFor(entries), ...inherentColumnsFor(entries)];
}

export function rowFor(entry: Lexeme, columns: readonly string[]): Record<string, string> {
  const row: Record<string, string> = {
    lemma: entry.lemma,
    class: entry.classKey,
    stem: stemFor(entry.stems, 'base')?.form ?? '',
    gloss: entry.glosses.join('; '),
    tags: [...entry.tags].join('; '),
    homograph: String(entry.homograph),
  };

  for (const column of columns) {
    if (column.startsWith('stem.')) {
      row[column] = stemFor(entry.stems, column.slice('stem.'.length))?.form ?? '';
      continue;
    }
    if (column.startsWith('inherent.')) {
      const dimension = column.slice('inherent.'.length);
      row[column] = entry.inherent.pairs.find((pair) => pair.dimension === dimension)?.value ?? '';
    }
  }
  return row;
}

export function exportLexemes(entries: readonly Lexeme[]): string {
  const sorted = [...entries].sort(compareLexemes);
  const columns = columnsFor(sorted);
  return writeTable(
    columns,
    sorted.map((entry) => rowFor(entry, columns)),
  );
}

/** A one line summary of a lexeme, for a log or a report rather than a file. */
export function describeLexeme(entry: Lexeme): string {
  const inherent = bundleKey(entry.inherent);
  const tail = inherent === '' ? '' : ` (${inherent})`;
  return `${entry.lemma} ${entry.classKey}${tail}: ${entry.glosses.join('; ')}`;
}
