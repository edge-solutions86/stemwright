import { describe, expect, it } from 'vitest';
import {
  bench,
  formatSummary,
  median,
  spread,
  summarise,
  timeOnce,
  usage,
} from '../../scripts/bench.mjs';

describe('median', () => {
  it('takes the middle of an odd list', () => {
    expect(median([3, 1, 2])).toBe(2);
  });

  it('averages the middle two of an even list', () => {
    expect(median([1, 2, 3, 4])).toBe(2.5);
  });

  it('is zero for an empty list', () => {
    expect(median([])).toBe(0);
  });

  it('is unmoved by one wild outlier', () => {
    expect(median([1, 1, 1, 1, 900])).toBe(1);
  });
});

describe('spread', () => {
  it('is the distance between the extremes', () => {
    expect(spread([2, 9, 4])).toBe(7);
  });

  it('is zero for one sample and for none', () => {
    expect(spread([5])).toBe(0);
    expect(spread([])).toBe(0);
  });
});

describe('summarise', () => {
  it('reports the shape of the samples', () => {
    const summary = summarise('queue', [4, 1, 2, 3]);
    expect(summary).toEqual({
      name: 'queue',
      runs: 4,
      medianMs: 2.5,
      fastestMs: 1,
      slowestMs: 4,
      spreadMs: 3,
    });
  });

  it('reports zeros for no samples', () => {
    expect(summarise('queue', [])).toMatchObject({ runs: 0, medianMs: 0, spreadMs: 0 });
  });
});

describe('formatSummary', () => {
  it('names the benchmark and the median first', () => {
    const line = formatSummary(summarise('queue', [1, 2, 3]));
    expect(line.startsWith('queue')).toBe(true);
    expect(line).toContain('2.00ms median');
    expect(line).toContain('over 3 runs');
  });
});

describe('timeOnce', () => {
  it('returns a number that is never negative', () => {
    expect(timeOnce(() => undefined)).toBeGreaterThanOrEqual(0);
  });

  it('runs the body exactly once', () => {
    let calls = 0;
    timeOnce(() => {
      calls += 1;
    });
    expect(calls).toBe(1);
  });
});

describe('bench', () => {
  it('runs the warm up passes and the measured ones', () => {
    let calls = 0;
    const summary = bench(
      'counting',
      () => {
        calls += 1;
      },
      5,
      2,
    );
    expect(calls).toBe(7);
    expect(summary.runs).toBe(5);
  });

  it('throws the warm up away rather than measuring it', () => {
    const summary = bench('counting', () => undefined, 4, 3);
    expect(summary.runs).toBe(4);
  });

  it('names the benchmark it was given', () => {
    expect(bench('queue assembly', () => undefined, 2, 0).name).toBe('queue assembly');
  });
});

describe('usage', () => {
  it('says what the module is for', () => {
    expect(usage()).toContain('runner, not a suite');
    expect(usage()).toContain('npm run test:perf');
  });
});
