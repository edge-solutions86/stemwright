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

- The flags more than one command declares now live in `cli.Flags`. The `--on`
  handling had already drifted into two spellings, and a second idea of what
  day it is is not something to leave lying around in a tool organised around
  days.

## Unreleased

### Added

- The paradigm layer: profiles, revisions, syncretism, cells, realisation.
- The drill layer: decks, membership, policies, queues, burial, review.
- The mastery layer: ladders, the strength ledger, credit, leeches, shields.
- The course layer: courses, enrolments, assignments, progress, authorisation.
- Reporting over coverage, forecasts, rollups and cohorts.
- Application ports with map backed stores behind them.
- Continuous integration: the Maven build with its format check, lint, suite
  and coverage floor on every push, and the timing suite as its own job.
- A coverage floor enforced by the build, so a change that drops coverage fails
  rather than being noticed a month later.
