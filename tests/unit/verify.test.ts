import { describe, expect, it } from 'vitest';
import { STAGES, planFor, stageNamed } from '../../scripts/verify.mjs';

describe('the verify plan', () => {
  it('declares the five stages', () => {
    expect(STAGES.map((stage) => stage.name)).toEqual(['format', 'lint', 'types', 'build', 'test']);
  });

  it('runs the fastest checks first', () => {
    const names = STAGES.map((stage) => stage.name);
    expect(names.indexOf('format')).toBeLessThan(names.indexOf('test'));
    expect(names.indexOf('lint')).toBeLessThan(names.indexOf('types'));
  });

  it('gives every stage a command and arguments', () => {
    for (const stage of STAGES) {
      expect(typeof stage.command).toBe('string');
      expect(Array.isArray(stage.args)).toBe(true);
      expect(stage.args.length).toBeGreaterThan(0);
    }
  });
});

describe('stageNamed', () => {
  it('finds a stage and reports a miss', () => {
    expect(stageNamed('lint')?.name).toBe('lint');
    expect(stageNamed('nowhere')).toBeUndefined();
  });
});

describe('planFor', () => {
  it('runs everything when nothing is named', () => {
    expect(planFor([])).toHaveLength(STAGES.length);
  });

  it('runs only the stages named, in the order given', () => {
    expect(planFor(['test', 'lint']).map((stage) => stage.name)).toEqual(['test', 'lint']);
  });

  it('refuses a stage nobody declared and lists the real ones', () => {
    expect(() => planFor(['nowhere'])).toThrow(/there is no stage named nowhere/);
    expect(() => planFor(['nowhere'])).toThrow(/format, lint, types, build, test/);
  });
});
