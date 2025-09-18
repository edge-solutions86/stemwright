import { performance } from 'node:perf_hooks';
import { argv, exit, stdout } from 'node:process';

const NEWLINE = String.fromCodePoint(0x0a);

/**
 * A benchmark runner small enough to trust.
 *
 * It reports the median rather than the mean, because one garbage collection
 * pause in twenty runs moves a mean by more than most changes worth measuring
 * and moves a median not at all. It also reports the spread, so a reader can
 * see whether the median means anything on the machine it was taken on.
 *
 * Nothing here decides whether a number is good. That belongs to the timing
 * suite, which has ceilings; this is for a person comparing two branches.
 */
export function median(values) {
  if (values.length === 0) {
    return 0;
  }
  const sorted = [...values].sort((a, b) => a - b);
  const middle = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
}

export function spread(values) {
  if (values.length === 0) {
    return 0;
  }
  return Math.max(...values) - Math.min(...values);
}

export function summarise(name, samples) {
  return {
    name,
    runs: samples.length,
    medianMs: median(samples),
    fastestMs: samples.length === 0 ? 0 : Math.min(...samples),
    slowestMs: samples.length === 0 ? 0 : Math.max(...samples),
    spreadMs: spread(samples),
  };
}

export function formatSummary(summary) {
  const parts = [
    summary.name.padEnd(28),
    `${summary.medianMs.toFixed(2)}ms median`,
    `${summary.fastestMs.toFixed(2)}ms fastest`,
    `${summary.spreadMs.toFixed(2)}ms spread`,
    `over ${summary.runs} runs`,
  ];
  return parts.join('  ');
}

export function timeOnce(body) {
  const started = performance.now();
  body();
  return performance.now() - started;
}

/** Runs a warm up pass that is thrown away, then the measured ones. */
export function bench(name, body, runs = 20, warmUp = 3) {
  for (let index = 0; index < warmUp; index += 1) {
    body();
  }
  const samples = [];
  for (let index = 0; index < runs; index += 1) {
    samples.push(timeOnce(body));
  }
  return summarise(name, samples);
}

/**
 * The entry point is deliberately thin. A benchmark needs the built output and
 * a scenario, and both belong to whoever is comparing two branches rather than
 * to this file, so it prints how to use it rather than guessing.
 */
export function usage() {
  return [
    'stemwright bench',
    '',
    'This module is a runner, not a suite. Import bench and summarise from it',
    'and time whatever you are comparing, or run npm run test:perf for the',
    'timing suite with its ceilings.',
  ].join(NEWLINE);
}

if (argv[1]?.endsWith('bench.mjs') === true) {
  stdout.write(usage() + NEWLINE);
  exit(0);
}
