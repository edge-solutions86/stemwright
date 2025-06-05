import { defineConfig } from 'vitest/config';

/**
 * The timing suite runs under its own config so it can never be pulled into
 * `npm test` by accident, and so coverage instrumentation is off: instrumented
 * code is several times slower and makes every ceiling here meaningless.
 */
export default defineConfig({
  test: {
    include: ['tests/performance/**/*.perf.test.ts'],
    environment: 'node',
    testTimeout: 60_000,
  },
});
