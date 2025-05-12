import { describe, expect, it } from 'vitest';
import { ValidationError } from '../../src/domain/errors.js';
import type { LexemeContext } from '../../src/domain/lexicon/lexeme.js';
import { stemRoles } from '../../src/domain/lexicon/stem.js';
import {
  collisionsWith,
  describeImport,
  importCsv,
  isClean,
  requireClean,
} from '../../src/io/lexicon-import.js';
import { latinClasses } from '../support/classes.js';
import { latinFullSchema, puella } from '../support/lexemes.js';

const context: LexemeContext = { schema: latinFullSchema, classes: latinClasses };

const header = 'lemma,class,stem,gloss,tags';
const good = `${header}\npuella,first-declension,puell,girl;maiden,core\nbellum,second-declension.neuter,bell,war,core\n`;

describe('importCsv', () => {
  it('accepts every good row', () => {
    const report = importCsv(context, good);
    expect(report.total).toBe(2);
    expect(report.accepted).toHaveLength(2);
    expect(report.rejected).toEqual([]);
    expect(isClean(report)).toBe(true);
  });

  it('splits glosses and tags on semicolons', () => {
    const entry = importCsv(context, good).accepted[0]!;
    expect(entry.glosses).toEqual(['girl', 'maiden']);
    expect(entry.tags).toEqual(['core']);
  });

  it('reads extra stem columns', () => {
    const text = `${header},stem.oblique\nrex,third-declension,reg,king,core,regi\n`;
    const entry = importCsv(context, text).accepted[0]!;
    expect(stemRoles(entry.stems)).toEqual(['base', 'oblique']);
  });

  it('ignores an empty extra stem column', () => {
    const text = `${header},stem.oblique\nrex,third-declension,reg,king,core,\n`;
    expect(stemRoles(importCsv(context, text).accepted[0]!.stems)).toEqual(['base']);
  });

  it('reads inherent columns', () => {
    const text = `${header},inherent.gender\npuella,first-declension,puell,girl,core,fem\n`;
    expect(importCsv(context, text).accepted[0]!.inherent.pairs).toEqual([
      { dimension: 'gender', value: 'fem' },
    ]);
  });

  it('reads a homograph index', () => {
    const text = `${header},homograph\nmalus,second-declension,mal,apple tree,core,2\n`;
    expect(importCsv(context, text).accepted[0]!.homograph).toBe(2);
  });

  it('reads a header with no rows', () => {
    const report = importCsv(context, `${header}\n`);
    expect(report.total).toBe(0);
    expect(isClean(report)).toBe(true);
  });

  it('refuses a file missing a required column', () => {
    expect(() => importCsv(context, 'lemma,gloss\npuella,girl\n')).toThrow(
      /missing the columns class, stem/,
    );
  });
});

describe('rejected rows', () => {
  const mixed = `${header}\npuella,first-declension,puell,girl,core\nnauta,fourth-declension,naut,sailor,core\n,first-declension,x,nothing,core\n`;

  it('keeps going past a bad row', () => {
    const report = importCsv(context, mixed);
    expect(report.total).toBe(3);
    expect(report.accepted).toHaveLength(1);
    expect(report.rejected).toHaveLength(2);
  });

  it('names the line of each rejection', () => {
    expect(importCsv(context, mixed).rejected.map((row) => row.line)).toEqual([3, 4]);
  });

  it('carries the problems of each rejection', () => {
    const rejected = importCsv(context, mixed).rejected[0]!;
    expect(rejected.lemma).toBe('nauta');
    expect(rejected.problems[0]?.message).toContain('is not a class of latin');
  });

  it('records a row with no lemma at all', () => {
    const rejected = importCsv(context, mixed).rejected[1]!;
    expect(rejected.lemma).toBe('');
    expect(rejected.problems).toHaveLength(1);
  });
});

describe('requireClean', () => {
  it('gives the accepted rows when nothing was rejected', () => {
    expect(requireClean(importCsv(context, good))).toHaveLength(2);
  });

  it('refuses when anything was rejected and names the lines', () => {
    const mixed = `${header}\nnauta,fourth-declension,naut,sailor,core\n`;
    expect(() => requireClean(importCsv(context, mixed))).toThrow(ValidationError);
    expect(() => requireClean(importCsv(context, mixed))).toThrow(/1 of 1 rows were rejected/);
  });
});

describe('describeImport', () => {
  it('counts what is usable', () => {
    expect(describeImport(importCsv(context, good))[0]).toBe('2 of 2 rows are usable');
  });

  it('reads each rejection out with its line', () => {
    const mixed = `${header}\nnauta,fourth-declension,naut,sailor,core\n`;
    const lines = describeImport(importCsv(context, mixed));
    expect(lines[1]).toContain('line 2 nauta:');
  });

  it('marks a row with no lemma', () => {
    const mixed = `${header}\n,first-declension,x,nothing,core\n`;
    expect(describeImport(importCsv(context, mixed))[1]).toContain('(no lemma)');
  });
});

describe('collisionsWith', () => {
  it('names a row that would collide with something stored', () => {
    expect(collisionsWith(importCsv(context, good), [puella])).toEqual(['puella']);
  });

  it('names nothing when the lexicon is empty', () => {
    expect(collisionsWith(importCsv(context, good), [])).toEqual([]);
  });

  it('ignores case when comparing lemmas', () => {
    const text = `${header}\nPUELLA,first-declension,puell,girl,core\n`;
    expect(collisionsWith(importCsv(context, text), [puella])).toEqual(['PUELLA']);
  });
});
