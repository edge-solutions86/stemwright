import { describe, expect, it } from 'vitest';
import { countBy, distinct, groupBy, groupSorted, indexBy } from '../../src/domain/collections.js';

const rows = [
  { id: 'a', kind: 'noun', weight: 3 },
  { id: 'b', kind: 'verb', weight: 1 },
  { id: 'c', kind: 'noun', weight: 2 },
  { id: 'd', kind: 'noun', weight: 2 },
];

describe('groupBy', () => {
  it('buckets by the key it is given', () => {
    const grouped = groupBy(rows, (row) => row.kind);
    expect([...grouped.keys()]).toEqual(['noun', 'verb']);
    expect(grouped.get('noun')).toHaveLength(3);
  });

  it('keeps the order items arrived in', () => {
    const grouped = groupBy(rows, (row) => row.kind);
    expect(grouped.get('noun')?.map((row) => row.id)).toEqual(['a', 'c', 'd']);
  });

  it('gives an empty map for an empty input', () => {
    expect(groupBy([], () => 'x').size).toBe(0);
  });

  it('takes any iterable, not only an array', () => {
    const grouped = groupBy(new Set(rows), (row) => row.kind);
    expect(grouped.size).toBe(2);
  });
});

describe('groupSorted', () => {
  it('sorts each bucket by the comparator', () => {
    const grouped = groupSorted(
      rows,
      (row) => row.kind,
      (a, b) => a.weight - b.weight || a.id.localeCompare(b.id),
    );
    expect(grouped.get('noun')?.map((row) => row.id)).toEqual(['c', 'd', 'a']);
  });

  it('leaves a single item bucket alone', () => {
    const grouped = groupSorted(
      rows,
      (row) => row.kind,
      (a, b) => a.weight - b.weight,
    );
    expect(grouped.get('verb')?.map((row) => row.id)).toEqual(['b']);
  });
});

describe('countBy', () => {
  it('counts the members of each bucket', () => {
    const counts = countBy(rows, (row) => row.kind);
    expect(counts.get('noun')).toBe(3);
    expect(counts.get('verb')).toBe(1);
    expect(counts.get('adjective')).toBeUndefined();
  });

  it('counts nothing for an empty input', () => {
    expect(countBy([], () => 'x').size).toBe(0);
  });
});

describe('distinct', () => {
  it('keeps the first of each key', () => {
    expect(distinct(rows, (row) => String(row.weight)).map((row) => row.id)).toEqual([
      'a',
      'b',
      'c',
    ]);
  });

  it('keeps everything when every key differs', () => {
    expect(distinct(rows, (row) => row.id)).toHaveLength(4);
  });
});

describe('indexBy', () => {
  it('keys each item by the function', () => {
    const index = indexBy(rows, (row) => row.id);
    expect(index.size).toBe(4);
    expect(index.get('c')?.weight).toBe(2);
  });

  it('lets a later item win a repeated key', () => {
    const index = indexBy(rows, (row) => row.kind);
    expect(index.get('noun')?.id).toBe('d');
  });
});
