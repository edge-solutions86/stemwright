import { describe, expect, it } from 'vitest';
import {
  bar,
  columnWidths,
  indent,
  keyValues,
  padTo,
  percent,
  pluralise,
  section,
  table,
  truncate,
} from '../../src/cli/render.js';

describe('padTo', () => {
  it('pads to the right by default', () => {
    expect(padTo('ab', 5)).toBe('ab   ');
  });

  it('pads to the left when asked', () => {
    expect(padTo('ab', 5, 'right')).toBe('   ab');
  });

  it('leaves a value already at or over the width alone', () => {
    expect(padTo('abcdef', 3)).toBe('abcdef');
  });

  it('counts code points rather than utf-16 units', () => {
    const astral = String.fromCodePoint(0x1d7d9);
    expect(padTo(astral, 3)).toBe(`${astral}  `);
  });
});

describe('columnWidths', () => {
  it('takes the widest cell of each column', () => {
    expect(
      columnWidths([
        ['a', 'bbb'],
        ['cc', 'd'],
      ]),
    ).toEqual([2, 3]);
  });

  it('tolerates ragged rows', () => {
    expect(columnWidths([['a'], ['bb', 'ccc']])).toEqual([2, 3]);
  });

  it('is empty for no rows', () => {
    expect(columnWidths([])).toEqual([]);
  });
});

describe('table', () => {
  const columns = [{ header: 'cell' }, { header: 'due', align: 'right' as const }];

  it('puts a rule under the header', () => {
    const lines = table(columns, [['gen.sg', '3']]).split('\n');
    expect(lines[0]).toBe('cell    due');
    expect(lines[1]).toBe('------  ---');
    expect(lines[2]).toBe('gen.sg    3');
  });

  it('pads a short row out rather than refusing it', () => {
    const lines = table(columns, [['gen.sg']]).split('\n');
    expect(lines[2]).toBe('gen.sg');
  });

  it('prints just the header and rule for no rows', () => {
    expect(table(columns, []).split('\n')).toHaveLength(2);
  });

  it('never leaves trailing spaces on a line', () => {
    const lines = table(columns, [['a', 'b']]).split('\n');
    expect(lines.every((line) => line === line.trimEnd())).toBe(true);
  });
});

describe('keyValues', () => {
  it('aligns the values', () => {
    const rendered = keyValues([
      ['deck', 'all'],
      ['learner', 'lrn_one'],
    ]);
    expect(rendered).toBe('deck     all\nlearner  lrn_one');
  });

  it('prints nothing for no pairs', () => {
    expect(keyValues([])).toBe('');
  });
});

describe('bar', () => {
  it('fills in proportion', () => {
    expect(bar(0, 4)).toBe('....');
    expect(bar(0.5, 4)).toBe('##..');
    expect(bar(1, 4)).toBe('####');
  });

  it('clamps out of range shares', () => {
    expect(bar(-1, 4)).toBe('....');
    expect(bar(9, 4)).toBe('####');
    expect(bar(Number.NaN, 4)).toBe('....');
  });

  it('takes the characters it is given', () => {
    expect(bar(0.5, 4, '=', ' ')).toBe('==  ');
  });
});

describe('percent', () => {
  it('prints a share as a percentage', () => {
    expect(percent(0.5)).toBe('50%');
    expect(percent(0.333, 1)).toBe('33.3%');
  });

  it('prints nothing sensible as zero', () => {
    expect(percent(Number.NaN)).toBe('0%');
  });
});

describe('pluralise', () => {
  it('uses the singular for one', () => {
    expect(pluralise(1, 'cell')).toBe('1 cell');
    expect(pluralise(2, 'cell')).toBe('2 cells');
    expect(pluralise(0, 'cell')).toBe('0 cells');
  });

  it('takes an irregular plural', () => {
    expect(pluralise(2, 'lexis', 'lexeis')).toBe('2 lexeis');
  });
});

describe('indent and section', () => {
  it('indents every non empty line', () => {
    expect(indent('a\n\nb')).toBe('  a\n\n  b');
  });

  it('says so when a section has nothing in it', () => {
    expect(section('Weakest', '')).toBe('Weakest\n  nothing to show');
  });

  it('indents the body of a section', () => {
    expect(section('Weakest', 'gen.sg')).toBe('Weakest\n  gen.sg');
  });
});

describe('truncate', () => {
  it('leaves a short value alone', () => {
    expect(truncate('abc', 5)).toBe('abc');
  });

  it('marks a value it cut', () => {
    expect(truncate('abcdef', 4)).toBe('abc~');
  });

  it('handles a width of one or zero', () => {
    expect(truncate('abcdef', 1)).toBe('a');
    expect(truncate('abcdef', 0)).toBe('');
  });
});
