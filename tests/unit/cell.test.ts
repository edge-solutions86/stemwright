import { describe, expect, it } from 'vitest';
import { featureBundle } from '../../src/domain/features/bundle.js';
import {
  cell,
  cellIdFor,
  cellsInBundle,
  cellsOfLexeme,
  compareCells,
  expandCells,
  formatCellRef,
  indexCells,
  parseCellRef,
  sameCell,
  skippedBundles,
} from '../../src/domain/paradigm/cell.js';
import { paradigmProfile } from '../../src/domain/paradigm/profile.js';
import { featureSchema } from '../../src/domain/features/schema.js';
import { lexeme } from '../../src/domain/lexicon/lexeme.js';
import { stemSet } from '../../src/domain/lexicon/stem.js';
import { isId } from '../../src/domain/identifiers.js';
import { latinClasses } from '../support/classes.js';
import { firstDeclension, latinNoun, secondNeuter } from '../support/profiles.js';

const gendered = featureSchema({
  language: 'latin',
  dimensions: [
    { key: 'case', values: ['nom', 'gen', 'dat', 'acc', 'abl'] },
    { key: 'number', values: ['sg', 'pl'] },
    { key: 'gender', values: ['masc', 'fem', 'neut'], optional: true },
  ],
});

const context = { schema: gendered, classes: latinClasses };

const puella = lexeme(context, {
  lemma: 'puella',
  classKey: 'first-declension',
  stems: stemSet([{ role: 'base', form: 'puell' }]),
  inherent: { gender: 'fem' },
});

const bellum = lexeme(context, {
  lemma: 'bellum',
  classKey: 'second-declension.neuter',
  stems: stemSet([{ role: 'base', form: 'bell' }]),
  inherent: { gender: 'neut' },
});

const genderedProfile = paradigmProfile(gendered, {
  key: 'gendered',
  classKey: 'first-declension',
  slots: [
    { bundle: 'nom.sg.fem', affix: 'a' },
    { bundle: 'gen.sg.fem', affix: 'ae' },
    { bundle: 'nom.sg.masc', affix: 'us' },
    { bundle: 'gen.sg.masc', affix: 'i' },
  ],
});

describe('cellIdFor', () => {
  it('is stable and well formed', () => {
    const id = cellIdFor(puella.id, 'case=gen;number=sg');
    expect(id).toBe(cellIdFor(puella.id, 'case=gen;number=sg'));
    expect(isId('cell', id)).toBe(true);
  });

  it('separates bundles and lexemes', () => {
    expect(cellIdFor(puella.id, 'case=gen;number=sg')).not.toBe(
      cellIdFor(puella.id, 'case=dat;number=sg'),
    );
    expect(cellIdFor(puella.id, 'case=gen;number=sg')).not.toBe(
      cellIdFor(bellum.id, 'case=gen;number=sg'),
    );
  });
});

describe('expandCells', () => {
  it('makes one cell per slot when nothing is ruled out', () => {
    expect(expandCells(latinNoun, puella, firstDeclension)).toHaveLength(10);
  });

  it('carries the profile key onto every cell', () => {
    const cells = expandCells(latinNoun, bellum, secondNeuter);
    expect(new Set(cells.map((entry) => entry.profileKey))).toEqual(new Set(['second-neuter']));
  });

  it('skips a slot the inherent gender rules out', () => {
    const cells = expandCells(gendered, puella, genderedProfile);
    expect(cells).toHaveLength(2);
    expect(cells.map((entry) => entry.bundle)).toEqual([
      'case=nom;number=sg;gender=fem',
      'case=gen;number=sg;gender=fem',
    ]);
  });

  it('names what it skipped', () => {
    expect(skippedBundles(gendered, puella, genderedProfile)).toEqual([
      'case=nom;number=sg;gender=masc',
      'case=gen;number=sg;gender=masc',
    ]);
  });

  it('skips nothing for a lexeme with no inherent features', () => {
    const bare = lexeme(context, {
      lemma: 'res',
      classKey: 'third-declension',
      stems: stemSet([{ role: 'base', form: 'r' }]),
    });
    expect(skippedBundles(gendered, bare, genderedProfile)).toEqual([]);
    expect(expandCells(gendered, bare, genderedProfile)).toHaveLength(4);
  });

  it('gives the same ids on a second expansion', () => {
    const first = expandCells(latinNoun, puella, firstDeclension).map((entry) => entry.id);
    const second = expandCells(latinNoun, puella, firstDeclension).map((entry) => entry.id);
    expect(second).toEqual(first);
  });
});

describe('cell references', () => {
  it('prints a lemma and a dotted bundle', () => {
    const bundle = featureBundle(latinNoun, { case: 'gen', number: 'sg' });
    expect(formatCellRef(latinNoun, puella, bundle)).toBe('puella/gen.sg');
  });

  it('reads a plain reference back', () => {
    expect(parseCellRef('puella/gen.sg')).toEqual({
      lemma: 'puella',
      homograph: 1,
      notation: 'gen.sg',
    });
  });

  it('reads a homograph index', () => {
    expect(parseCellRef('malus(2)/nom.pl')).toEqual({
      lemma: 'malus',
      homograph: 2,
      notation: 'nom.pl',
    });
  });

  it('tolerates space around the homograph marker', () => {
    expect(parseCellRef('malus (2) / nom.pl').lemma).toBe('malus');
  });

  it('refuses a reference with no bundle', () => {
    expect(() => parseCellRef('puella')).toThrow(/lemma and a bundle/);
    expect(() => parseCellRef('puella/')).toThrow(/lemma and a bundle/);
    expect(() => parseCellRef('/gen.sg')).toThrow(/lemma and a bundle/);
  });

  it('refuses a homograph index below one', () => {
    expect(() => parseCellRef('malus(0)/nom.pl')).toThrow(/homograph index below one/);
  });
});

describe('cell collections', () => {
  const cells = [
    ...expandCells(latinNoun, puella, firstDeclension),
    ...expandCells(latinNoun, bellum, secondNeuter),
  ];

  it('filters by lexeme', () => {
    expect(cellsOfLexeme(cells, bellum.id)).toHaveLength(10);
  });

  it('filters by bundle', () => {
    const bundle = featureBundle(latinNoun, { case: 'gen', number: 'pl' });
    expect(cellsInBundle(cells, bundle)).toHaveLength(2);
  });

  it('indexes by id', () => {
    const index = indexCells(cells);
    expect(index.size).toBe(20);
    expect(index.get(cells[0]!.id)).toBe(cells[0]);
  });

  it('compares by lexeme then bundle', () => {
    const a = cell('lex_aaaaaa', 'case=nom;number=sg', 'p');
    const b = cell('lex_aaaaaa', 'case=nom;number=pl', 'p');
    const c = cell('lex_bbbbbb', 'case=nom;number=sg', 'p');
    expect(compareCells(a, c)).toBeLessThan(0);
    expect(compareCells(a, b)).toBeGreaterThan(0);
    expect(compareCells(a, a)).toBe(0);
  });

  it('calls two cells the same when the lexeme and bundle match', () => {
    expect(sameCell(cell('lex_a', 'b', 'p1'), cell('lex_a', 'b', 'p2'))).toBe(true);
    expect(sameCell(cell('lex_a', 'b', 'p1'), cell('lex_a', 'c', 'p1'))).toBe(false);
  });
});
