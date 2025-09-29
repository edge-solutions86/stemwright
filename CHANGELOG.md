# Changelog

## 1.0.0 - 2025-09-29

### Added

- `stemwright doctor`, an editorial sweep over one language on one day. It
  reports the three different reasons a learner ends up with an empty queue,
  which are indistinguishable from the learner's side: a class with no paradigm
  bound, a profile with nothing in force yet, and a deck that selects nothing.
  It exits non zero on an error, and `--strict` widens that to a warning so a
  repository of teaching material can run it in its own ci.
- `stemwright deck` and `stemwright decks`, which read a deck the way an editor
  needs to rather than the way it was written: what it resolves to on a day, how
  far its busiest bundle sits above the average, and which lexemes fell out and
  why. `--cells` narrows a stored deck without saving the change.

### Changed

- The flags more than one command declares now live in `src/cli/flags.ts`. The
  `--on` handling had already drifted into two spellings, and a second idea of
  what day it is is not something to leave lying around in a tool organised
  around days.

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
