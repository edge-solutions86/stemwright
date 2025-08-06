import { describe, expect, it } from 'vitest';
import { stemSet } from '../../src/domain/lexicon/stem.js';
import {
  BASE_COLUMNS,
  columnsFor,
  describeLexeme,
  exportLexemes,
  inherentColumnsFor,
  rowFor,
  stemColumnsFor,
} from '../../src/io/lexicon-export.js';
import { importCsv } from '../../src/io/lexicon-import.js';
import { readTable } from '../../src/io/csv.js';
import { latinClasses } from '../support/classes.js';
import { ager, bellum, latinFullSchema, makeLexeme, puella } from '../support/lexemes.js';

const context = { schema: latinFullSchema, classes: latinClasses };
const entries = [puella, bellum, ager];

const withStems = makeLexeme({
  lemma: 'fero',
  classKey: 'third-conjugation',
  stems: stemSet([
    { role: 'base', form: 'fer' },
    { role: 'perfect', form: 'tul', suppletive: true },
  ]),
  glosses: ['carry'],
});

describe('columnsFor', () => {
  it('always starts with the base columns', () => {
    expect(columnsFor(entries).slice(0, BASE_COLUMNS.length)).toEqual([...BASE_COLUMNS]);
  });

  it('adds a column for every extra stem role in use', () => {
    expect(stemColumnsFor([withStems])).toEqual(['stem.perfect']);
    expect(stemColumnsFor(entries)).toEqual([]);
  });

  it('adds a column for every inherent dimension in use', () => {
    expect(inherentColumnsFor(entries)).toEqual(['inherent.gender']);
    expect(inherentColumnsFor([withStems])).toEqual([]);
  });

  it('sorts the extra columns so two exports of one lexicon match', () => {
    const columns = columnsFor([...entries, withStems]);
    expect(columns).toContain('stem.perfect');
    expect(columns).toContain('inherent.gender');
  });
});

describe('rowFor', () => {
  it('writes the base fields', () => {
    const row = rowFor(puella, columnsFor(entries));
    expect(row['lemma']).toBe('puella');
    expect(row['class']).toBe('first-declension');
    expect(row['stem']).toBe('puell');
    expect(row['homograph']).toBe('1');
  });

  it('joins glosses and tags with semicolons', () => {
    expect(rowFor(bellum, columnsFor(entries))['gloss']).toBe('war; campaign');
    expect(rowFor(puella, columnsFor(entries))['tags']).toBe('core; chapter-one');
  });

  it('writes the inherent value into its column', () => {
    expect(rowFor(puella, columnsFor(entries))['inherent.gender']).toBe('fem');
  });

  it('writes an empty cell for a role or dimension this lexeme lacks', () => {
    const columns = columnsFor([...entries, withStems]);
    expect(rowFor(puella, columns)['stem.perfect']).toBe('');
    expect(rowFor(withStems, columns)['inherent.gender']).toBe('');
  });
});

describe('exportLexemes', () => {
  it('writes a header and one row per lexeme', () => {
    const table = readTable(exportLexemes(entries));
    expect(table.rows).toHaveLength(3);
    expect(table.headers).toContain('lemma');
  });

  it('sorts the rows by lemma', () => {
    const table = readTable(exportLexemes(entries));
    expect(table.rows.map((row) => row['lemma'])).toEqual(['ager', 'bellum', 'puella']);
  });

  it('writes an empty file with only a header for no lexemes', () => {
    expect(readTable(exportLexemes([])).rows).toEqual([]);
  });

  it('round trips through the importer', () => {
    const report = importCsv(context, exportLexemes(entries));
    expect(report.rejected).toEqual([]);
    expect(report.accepted.map((entry) => entry.id).sort()).toEqual(
      entries.map((entry) => entry.id).sort(),
    );
  });

  it('round trips the extra stems and the inherent features', () => {
    const report = importCsv(context, exportLexemes([puella, withStems]));
    const carried = report.accepted.find((entry) => entry.lemma === 'fero')!;
    expect(carried.stems.stems.map((stem) => stem.role)).toEqual(['base', 'perfect']);
    const girl = report.accepted.find((entry) => entry.lemma === 'puella')!;
    expect(girl.inherent.pairs).toEqual([{ dimension: 'gender', value: 'fem' }]);
  });

  it('round trips a homograph index', () => {
    const second = makeLexeme({
      lemma: 'malus',
      classKey: 'second-declension',
      stems: stemSet([{ role: 'base', form: 'mal' }]),
      homograph: 2,
    });
    const report = importCsv(context, exportLexemes([second]));
    expect(report.accepted[0]?.homograph).toBe(2);
  });
});

describe('describeLexeme', () => {
  it('names the class and the inherent features', () => {
    expect(describeLexeme(puella)).toBe('puella first-declension (gender=fem): girl');
  });

  it('leaves the brackets out when there are none', () => {
    expect(describeLexeme(withStems)).toBe('fero third-conjugation: carry');
  });
});
