# Changelog

## Unreleased

### Changed

- Toolchain moved to the February 2025 set: TypeScript 5.7.3, vitest 3.0.5,
  eslint 9.19.0 with typescript-eslint 8.23.0, prettier 3.4.2, node types 22.
- ESLint moved from `.eslintrc.cjs` to flat config in `eslint.config.js`. The
  type checked preset is kept, and `vitest.config.ts` and the config file itself
  are opted out of it, because neither is inside the project tsconfig.

### Added

- The paradigm layer: profiles, revisions, syncretism, cells, realisation.
- The drill layer: decks, membership, policies, queues, burial, review.
- The mastery layer: ladders, the strength ledger, credit, leeches, shields.
- The course layer: courses, enrolments, assignments, progress, authorisation.
- Reporting over coverage, forecasts, rollups and cohorts.
- Application ports with map backed stores behind them.

### Added

- Continuous integration: format, lint, types and build on one job, the suite on
  three cells (ubuntu on node 20 and 22, windows on node 22), and coverage with
  an artifact.
- Coverage thresholds enforced in `vitest.config.ts`, set just under what the
  suite reaches so a regression fails the build.
