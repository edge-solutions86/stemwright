import { describe, expect, it } from 'vitest';
import { featureBundle } from '../../src/domain/features/bundle.js';
import { featureSchema } from '../../src/domain/features/schema.js';
import { lexeme } from '../../src/domain/lexicon/lexeme.js';
import { stemSet } from '../../src/domain/lexicon/stem.js';
import { paradigmProfile } from '../../src/domain/paradigm/profile.js';
import { NO_RULES } from '../../src/domain/paradigm/realise.js';
import {
  blockedCount,
  buildTable,
  filledCount,
  flatRows,
  holeCount,
  renderTable,
  tableCell,
  tableToText,
} from '../../src/domain/paradigm/table.js';
import { latinClasses } from '../support/classes.js';
import { firstDeclension, latinNoun } from '../support/profiles.js';

const context = { schema: latinNoun, classes: latinClasses };
const puella = lexeme(context, {
  lemma: 'puella',
  classKey: 'first-declension',
  stems: stemSet([{ role: 'base', form: 'puell' }]),
});

const table = buildTable(latinNoun, NO_RULES, puella, firstDeclension, {
  rowDimension: 'case',
  columnDimension: 'number',
});

describe('buildTable', () => {
  it('has one row per case and one column per number', () => {
    expect(table.rowValues).toEqual(['nom', 'gen', 'dat', 'acc', 'abl']);
    expect(table.columnValues).toEqual(['sg', 'pl']);
    expect(table.cells).toHaveLength(5);
    expect(table.cells[0]).toHaveLength(2);
  });

  it('fills every cell the profile covers', () => {
    expect(filledCount(table)).toBe(10);
    expect(holeCount(table)).toBe(0);
    expect(blockedCount(table)).toBe(0);
  });

  it('puts the right form in the right cell', () => {
    expect(tableCell(table, 'gen', 'sg')?.form).toBe('puellae');
    expect(tableCell(table, 'abl', 'pl')?.form).toBe('puellis');
  });

  it('gives nothing for a row or column that is not there', () => {
    expect(tableCell(table, 'voc', 'sg')).toBeUndefined();
    expect(tableCell(table, 'gen', 'du')).toBeUndefined();
  });

  it('refuses to use one dimension twice', () => {
    expect(() =>
      buildTable(latinNoun, NO_RULES, puella, firstDeclension, {
        rowDimension: 'case',
        columnDimension: 'case',
      }),
    ).toThrow(/both rows and columns/);
  });

  it('refuses a dimension the schema does not have', () => {
    expect(() =>
      buildTable(latinNoun, NO_RULES, puella, firstDeclension, {
        rowDimension: 'tense',
        columnDimension: 'number',
      }),
    ).toThrow(/not in the latin schema/);
  });
});

describe('holes', () => {
  it('counts a cell the profile says nothing about', () => {
    const partial = paradigmProfile(latinNoun, {
      key: 'p',
      classKey: 'first-declension',
      slots: [
        { bundle: 'nom.sg', affix: 'a' },
        { bundle: 'gen.sg', affix: 'ae' },
      ],
    });
    const sparse = buildTable(latinNoun, NO_RULES, puella, partial, {
      rowDimension: 'case',
      columnDimension: 'number',
    });
    expect(filledCount(sparse)).toBe(2);
    expect(holeCount(sparse)).toBe(8);
  });
});

describe('rendering', () => {
  it('puts the headers first and the row labels down the side', () => {
    const grid = renderTable(table);
    expect(grid[0]).toEqual(['', 'sg', 'pl']);
    expect(grid[1]).toEqual(['nom', 'puella', 'puellae']);
  });

  it('marks an empty cell with the blank it was given', () => {
    const partial = paradigmProfile(latinNoun, {
      key: 'p',
      classKey: 'first-declension',
      slots: [{ bundle: 'nom.sg', affix: 'a' }],
    });
    const sparse = buildTable(latinNoun, NO_RULES, puella, partial, {
      rowDimension: 'case',
      columnDimension: 'number',
    });
    expect(renderTable(sparse, 'x')[1]).toEqual(['nom', 'puella', 'x']);
  });

  it('pads columns to a fixed width in the text form', () => {
    const lines = tableToText(table).split('\n');
    expect(lines[0]).toBe('     sg       pl');
    expect(lines[1]).toBe('nom  puella   puellae');
    expect(lines.every((line) => line === line.trimEnd())).toBe(true);
  });
});

describe('a third dimension', () => {
  const gendered = featureSchema({
    language: 'latin',
    dimensions: [
      { key: 'case', values: ['nom', 'gen'] },
      { key: 'number', values: ['sg', 'pl'] },
      { key: 'gender', values: ['masc', 'fem'] },
    ],
  });
  const genderedContext = { schema: gendered, classes: latinClasses };
  const bonus = lexeme(genderedContext, {
    lemma: 'bonus',
    classKey: 'first-declension',
    stems: stemSet([{ role: 'base', form: 'bon' }]),
  });
  const profile = paradigmProfile(gendered, {
    key: 'p',
    classKey: 'first-declension',
    slots: [
      { bundle: 'nom.sg.masc', affix: 'us' },
      { bundle: 'gen.sg.masc', affix: 'i' },
      { bundle: 'nom.pl.masc', affix: 'i' },
      { bundle: 'gen.pl.masc', affix: 'orum' },
      { bundle: 'nom.sg.fem', affix: 'a' },
      { bundle: 'gen.sg.fem', affix: 'ae' },
      { bundle: 'nom.pl.fem', affix: 'ae' },
      { bundle: 'gen.pl.fem', affix: 'arum' },
    ],
  });

  it('refuses to leave the third dimension open', () => {
    expect(() =>
      buildTable(gendered, NO_RULES, bonus, profile, {
        rowDimension: 'case',
        columnDimension: 'number',
      }),
    ).toThrow(/leaves gender open/);
  });

  it('builds once the third dimension is fixed', () => {
    const fixed = buildTable(gendered, NO_RULES, bonus, profile, {
      rowDimension: 'case',
      columnDimension: 'number',
      fixed: featureBundle(gendered, { gender: 'fem' }),
    });
    expect(tableCell(fixed, 'gen', 'sg')?.form).toBe('bonae');
    expect(filledCount(fixed)).toBe(4);
  });

  it('blocks cells the inherent features rule out', () => {
    const feminine = lexeme(genderedContext, {
      lemma: 'puella',
      classKey: 'first-declension',
      stems: stemSet([{ role: 'base', form: 'puell' }]),
      inherent: { gender: 'fem' },
    });
    const masculine = buildTable(gendered, NO_RULES, feminine, profile, {
      rowDimension: 'case',
      columnDimension: 'number',
      fixed: featureBundle(gendered, { gender: 'masc' }),
    });
    expect(blockedCount(masculine)).toBe(4);
    expect(filledCount(masculine)).toBe(0);
    expect(holeCount(masculine)).toBe(0);
  });
});

describe('flatRows', () => {
  it('finds a row whose forms are all the same', () => {
    const profile = paradigmProfile(latinNoun, {
      key: 'p',
      classKey: 'first-declension',
      slots: [
        { bundle: 'nom.sg', affix: 'a' },
        { bundle: 'nom.pl', affix: 'a' },
        { bundle: 'gen.sg', affix: 'ae' },
        { bundle: 'gen.pl', affix: 'arum' },
      ],
    });
    const built = buildTable(latinNoun, NO_RULES, puella, profile, {
      rowDimension: 'case',
      columnDimension: 'number',
    });
    expect(flatRows(built)).toEqual(['nom']);
  });

  it('does not call a single filled cell a flat row', () => {
    const profile = paradigmProfile(latinNoun, {
      key: 'p',
      classKey: 'first-declension',
      slots: [{ bundle: 'nom.sg', affix: 'a' }],
    });
    const built = buildTable(latinNoun, NO_RULES, puella, profile, {
      rowDimension: 'case',
      columnDimension: 'number',
    });
    expect(flatRows(built)).toEqual([]);
  });
});
