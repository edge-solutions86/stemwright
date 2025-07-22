# stemwright

A drill desk for inflectional paradigms.

The thing a learner is drilled on is a **cell**: one lexeme crossed with one
feature bundle, such as `oikos` in `gen.sg`. A cell is not a card. Cells
collapse into one another when the paradigm profile for that inflection class
declares the two bundles syncretic, and profiles are revised over time, so which
cells count as the same answer depends on which revision was in force when the
learner typed it.

Everything downstream inherits that dependency, and that is the shape of the
whole system:

    the revision in force decides the expected form and the twin set
    the deck and the cell decide the tolerance
    the tolerance decides the verdict
    the verdict decides the schedule
    the schedule and the twin set decide tomorrow's queue
    the lapse count decides whether the cell survives at all

Nothing reaches for the newest revision. Everything resolves on the day of the
answer, so a transcript printed next year says what the learner was shown.

## What is in here

| directory    | what it holds                                                                          |
| ------------ | -------------------------------------------------------------------------------------- |
| `src/domain` | every rule. No io, no clock it did not get handed, no framework.                       |
| `src/app`    | services, ports, events, wiring. Loads what the domain needs and checks who is asking. |
| `src/infra`  | stores, the append only journal, snapshots, backups.                                   |
| `src/http`   | a hand rolled router over `node:http`, and the routes.                                 |
| `src/cli`    | argument parsing, rendering and the commands.                                          |
| `src/io`     | csv, the paradigm text format, import reports.                                         |
| `tests`      | unit, integration, invariants, property and timing suites.                             |

**Zero runtime dependencies.** The store is an append only journal on the
filesystem, the http layer is a router of about a hundred lines, and the cli
parses its own arguments. A drill desk that ships to a university lab has to
install behind a proxy, so the dependency budget is spent on nothing.

## Running it

```
npm ci
npm run verify        # format, lint, types, build and the suite
npm test              # the suite on its own
npm run test:coverage # with the coverage floor enforced
npm run test:perf     # the timing suite, which npm test excludes
npm run docs          # regenerate docs/surface.md
```

The cli is `npm run cli -- <command>`:

```
npm run cli -- lexemes --language latin
npm run cli -- paradigm --language latin --lexeme lex_xxxx --rows case --columns number
npm run cli -- queue --language latin --deck all --learner lrn_one
npm run cli -- coverage --language latin --deck all --learner lrn_one
npm run cli -- routes
```

## Things worth knowing before changing it

- **A profile is never edited in place.** A change publishes a revision with a
  day it takes effect from, and grading resolves the revision of the answer's
  day. `src/domain/paradigm/revision.ts` is the whole argument.
- **Syncretism is declared, not inferred.** What the endings happen to produce
  and what the grammar treats as one form are different claims, and
  `reconcileSyncretism` reports where they disagree rather than picking one.
- **A validation failure carries its content in `details`.** The summary string
  is for a human; a test or a route that reads only the summary is asserting
  nothing.
- **The generated surface file is sorted by code point.** `localeCompare`
  orders `Fourth` and `first` differently depending on the runner's locale,
  which turns a checked in generated file into a coin toss.

## Data on disk

    <dataDir>/journal.ndjson       every write, in order, append only
    <dataDir>/snapshots/<n>.json   a point a replay can start from

A snapshot is a shortcut and never a source of truth: deleting every snapshot
and replaying the journal from the beginning gives the same state. Compaction
writes a fresh snapshot and prunes old ones; it never removes a journal entry.
