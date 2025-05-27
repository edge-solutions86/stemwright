import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    include: ['tests/**/*.test.ts'],
    exclude: ['tests/performance/**'],
    environment: 'node',
    clearMocks: true,
    coverage: {
      provider: 'v8',
      include: ['src/**/*.ts'],
      exclude: ['src/bin/**', 'src/index.ts'],
      reporter: ['text-summary', 'lcov'],
      // Set just under what the suite actually reaches, so a change that
      // drops coverage fails rather than being noticed a month later.
      thresholds: {
        statements: 96,
        branches: 93,
        functions: 96,
        lines: 96,
      },
    },
  },
});
