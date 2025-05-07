import { describe, expect, it } from 'vitest';
import { ValidationError } from '../../src/domain/errors.js';
import {
  formatCsv,
  formatField,
  formatRow,
  parseCsv,
  readTable,
  requireColumns,
  writeTable,
} from '../../src/io/csv.js';

describe('formatField', () => {
  it('leaves a plain value alone', () => {
    expect(formatField('puella')).toBe('puella');
  });

  it('quotes a value with a comma, a quote or a newline', () => {
    expect(formatField('a,b')).toBe('"a,b"');
    expect(formatField('a\nb')).toBe('"a\nb"');
    expect(formatField('say "hello"')).toBe('"say ""hello"""');
  });

  it('leaves an empty value unquoted', () => {
    expect(formatField('')).toBe('');
  });
});

describe('formatRow and formatCsv', () => {
  it('joins fields with commas', () => {
    expect(formatRow(['a', 'b,c'])).toBe('a,"b,c"');
  });

  it('ends the file with a newline', () => {
    expect(formatCsv([['a'], ['b']])).toBe('a\nb\n');
  });

  it('writes nothing for no rows', () => {
    expect(formatCsv([])).toBe('');
  });
});

describe('parseCsv', () => {
  it('reads plain rows', () => {
    expect(parseCsv('a,b\nc,d\n')).toEqual([
      ['a', 'b'],
      ['c', 'd'],
    ]);
  });

  it('does not make a row out of the trailing newline', () => {
    expect(parseCsv('a,b\n')).toHaveLength(1);
  });

  it('reads a file with no trailing newline', () => {
    expect(parseCsv('a,b')).toEqual([['a', 'b']]);
  });

  it('reads a quoted field with a comma in it', () => {
    expect(parseCsv('"a,b",c')).toEqual([['a,b', 'c']]);
  });

  it('reads a doubled quote as one quote', () => {
    expect(parseCsv('"say ""hello""",c')).toEqual([['say "hello"', 'c']]);
  });

  it('reads a newline inside a quoted field', () => {
    expect(parseCsv('"a\nb",c')).toEqual([['a\nb', 'c']]);
  });

  it('normalises carriage returns', () => {
    expect(parseCsv('a,b\r\nc,d\r\n')).toEqual([
      ['a', 'b'],
      ['c', 'd'],
    ]);
  });

  it('keeps an empty trailing field', () => {
    expect(parseCsv('a,')).toEqual([['a', '']]);
  });

  it('keeps a row that is only an empty quoted field', () => {
    expect(parseCsv('""')).toEqual([['']]);
  });

  it('reads nothing from an empty file', () => {
    expect(parseCsv('')).toEqual([]);
  });

  it('refuses a file that ends inside a quoted field', () => {
    expect(() => parseCsv('"a,b')).toThrow(/ends inside a quoted field/);
  });

  it('refuses a quote that opens in the middle of a field', () => {
    expect(() => parseCsv('a"b"')).toThrow(ValidationError);
  });

  it('round trips whatever it formatted', () => {
    const rows = [
      ['lemma', 'gloss'],
      ['puella', 'girl, maiden'],
      ['bellum', 'war\nor campaign'],
      ['malus', 'say "bad"'],
    ];
    expect(parseCsv(formatCsv(rows))).toEqual(rows);
  });
});

describe('readTable', () => {
  const text = 'lemma,gloss\npuella,girl\nbellum,war\n';

  it('reads named records', () => {
    const table = readTable(text);
    expect(table.headers).toEqual(['lemma', 'gloss']);
    expect(table.rows).toEqual([
      { lemma: 'puella', gloss: 'girl' },
      { lemma: 'bellum', gloss: 'war' },
    ]);
  });

  it('reads a header with no rows under it', () => {
    expect(readTable('lemma,gloss\n').rows).toEqual([]);
  });

  it('refuses a file with no header', () => {
    expect(() => readTable('')).toThrow(/has no header row/);
  });

  it('refuses a header naming a column twice', () => {
    expect(() => readTable('a,a\n1,2\n')).toThrow(/names a column twice/);
  });

  it('names the line of a row of the wrong width', () => {
    expect(() => readTable('lemma,gloss\npuella\n')).toThrow(
      /line 2 has 1 fields where the header has 2/,
    );
  });
});

describe('writeTable', () => {
  it('writes the columns in header order', () => {
    const text = writeTable(['lemma', 'gloss'], [{ gloss: 'girl', lemma: 'puella' }]);
    expect(text).toBe('lemma,gloss\npuella,girl\n');
  });

  it('writes an empty cell for a missing column', () => {
    expect(writeTable(['lemma', 'gloss'], [{ lemma: 'puella' }])).toBe('lemma,gloss\npuella,\n');
  });

  it('round trips through readTable', () => {
    const rows = [{ lemma: 'puella', gloss: 'girl, maiden' }];
    expect(readTable(writeTable(['lemma', 'gloss'], rows)).rows).toEqual(rows);
  });
});

describe('requireColumns', () => {
  it('passes when every column is present', () => {
    expect(() => requireColumns(readTable('a,b\n1,2\n'), ['a'])).not.toThrow();
  });

  it('names every missing column', () => {
    expect(() => requireColumns(readTable('a,b\n1,2\n'), ['c', 'd'])).toThrow(
      /missing the columns c, d/,
    );
  });
});
