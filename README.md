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

## Tech stack

- Java 21, built with Maven through the wrapper (`./mvnw`, Maven 3.9.16).
- One runtime dependency: [picocli](https://picocli.info) 4.7.7, as the entry point
  of the command line. The command grammar itself, its help text and its exit
  codes are the program's own.
- The http surface is a router of about a hundred lines over the JDK's built in
  `com.sun.net.httpserver`, answering RFC 8259 JSON written by the project's own
  canonical encoder.
- The store is an append only journal on the filesystem, with snapshots as a
  shortcut. A drill desk that ships to a university lab has to install behind a
  proxy, so the dependency budget is spent on almost nothing.
- Tests: JUnit 5 and AssertJ. Formatting: Spotless with palantir-java-format.
  Coverage: JaCoCo, with the build refusing to pass below 80% of lines.

## Building and testing

```
./mvnw verify            # format check, compile with -Xlint:all, the suite, the coverage floor
./mvnw test              # the suite on its own
./mvnw -B -Pperf test    # the timing suite, which the default build leaves out
./mvnw spotless:apply    # format the sources
```

`./mvnw verify` also packages two jars in `target/`: the library,
`stemwright-1.0.0.jar`, and a runnable `stemwright-1.0.0-cli.jar` with
everything it needs.

The developer tools live with the tests. Compile them once with
`./mvnw -q test-compile`, then:

```
java -cp target/classes:target/test-classes io.github.rkaushikdev.stemwright.tooling.Surface          # regenerate docs/surface.md
java -cp target/classes:target/test-classes io.github.rkaushikdev.stemwright.tooling.Surface check    # fail if it is out of date
java -cp target/classes:target/test-classes io.github.rkaushikdev.stemwright.tooling.Release          # pom, cli and changelog versions agree
java -cp target/classes:target/test-classes io.github.rkaushikdev.stemwright.tooling.Verify [stage]   # format, lint, types, build, test, fastest first
java -cp target/classes:target/test-classes io.github.rkaushikdev.stemwright.tooling.Bench            # how to time a change
```

## What is in here

Every package lives under `io.github.rkaushikdev.stemwright`.

| package     | what it holds                                                                           |
| ----------- | --------------------------------------------------------------------------------------- |
| `domain`    | every rule. No io, no clock it did not get handed, no framework.                        |
| `app`       | services, ports, events, wiring. Loads what the domain needs and checks who is asking.  |
| `infra`     | stores, the append only journal, snapshots, backups, the JSON model and codec.          |
| `io`        | csv, the paradigm text format, lexicon import and export reports.                       |
| `http`      | the router, problems, authentication, rate limiting, the server and the routes.        |
| `cli`       | the argument grammar, rendering and the commands.                                       |
| `bin`       | the two entry points: `Stemwright` (the cli) and `Serve` (the http server).            |

Under `domain`: `features` (schemas, bundles, notation, selectors), `lexicon`,
`paradigm` (profiles, revisions, syncretism, cells, realisation, tables),
`ortho` (folding, tolerance, comparison, hints, transliteration), `mastery`
(ladders, strengths, the ledger, credit, leeches, shields, attempts), `drill`
(policies, decks, membership, burial, queues, sessions, review), `course`
(courses, enrolments, assignments, progress, authorisation), `report`
(coverage, forecasts, rollups, cohorts, deck statistics, health), `text` and
`time`. `docs/architecture.md` describes the layers and `docs/surface.md` lists
the public surface.

## The command line

```
java -jar target/stemwright-1.0.0-cli.jar <command> [options]
java -jar target/stemwright-1.0.0-cli.jar help <command>
```

| command    | what it does                                                             |
| ---------- | ------------------------------------------------------------------------ |
| `queue`    | show what a learner would be asked today                                 |
| `paradigm` | print the table a lexeme was graded against on a day                     |
| `coverage` | show how much of a deck a learner holds                                  |
| `lexemes`  | list or search the lexicon of a language                                 |
| `deck`     | show what a deck holds and where it is unbalanced                        |
| `decks`    | list the decks of a language with the size each one resolves to         |
| `doctor`   | check a language for the holes that leave a learner with nothing to drill |
| `import`   | read a csv word list and report what it would add                        |
| `routes`   | list the http routes this build serves                                   |

```
stemwright lexemes --language latin --search girl
stemwright paradigm --language latin --lexeme lex_xxxx --rows case --columns number
stemwright queue --language latin --deck all --learner lrn_one
stemwright coverage --language latin --deck all --learner lrn_one
stemwright doctor --language latin --strict
stemwright import --language latin --file words.csv --apply
```

Every command that resolves against a day takes `--on yyyy-mm-dd` and otherwise
uses today. A flag must be declared by the command, so a typo is an error rather
than an ignored option, and `--` ends the flags, so a lemma that begins with a
dash can still be typed. Output goes to standard output on success and to
standard error on failure. The exit code is 0 when the command ran, 1 when it
ran and failed, and 2 when it was called wrongly; `doctor` exits 1 on an error,
or on a warning too under `--strict`.

## The http server

```
STEMWRIGHT_PORT=8080 \
STEMWRIGHT_VERSION=1.0.0 \
STEMWRIGHT_ACCOUNTS='{"tok":{"id":"lrn_one","role":"learner"}}' \
java -cp target/stemwright-1.0.0-cli.jar io.github.rkaushikdev.stemwright.bin.Serve
```

`STEMWRIGHT_PORT` defaults to 8080 and `STEMWRIGHT_VERSION` to `dev`.
`STEMWRIGHT_ACCOUNTS` maps a bearer token to an account with an `id` and a
`role` (`learner`, `instructor` or `admin`); a deployment replaces this
placeholder directory with whatever the institution already runs. Requests
carry `Authorization: Bearer <token>`, and the two report routes also take
`?course=<courseKey>` so the server can check who may read whose progress.

| method   | path                                                                  | what it answers                      |
| -------- | --------------------------------------------------------------------- | ------------------------------------ |
| `GET`    | `/health`                                                             | the process is up, and its version   |
| `GET`    | `/ready`                                                              | the process can serve                |
| `GET`    | `/learners/:learnerId/languages/:language/decks/:deckKey/queue`       | today's queue for a deck             |
| `POST`   | `/learners/:learnerId/languages/:language/decks/:deckKey/answers`     | grade one answer                     |
| `POST`   | `/learners/:learnerId/languages/:language/decks/:deckKey/answers/bulk`| grade a batch of answers             |
| `POST`   | `/learners/:learnerId/sessions/:sessionId/finish`                     | close a session and summarise it     |
| `GET`    | `/learners/:learnerId/languages/:language/decks/:deckKey/coverage`    | counts, weakest bundles, a forecast  |
| `GET`    | `/learners/:learnerId/activity`                                       | answers per day and the hardest cells |
| `GET`    | `/languages/:language/lexemes`                                        | list or search a lexicon             |
| `POST`   | `/languages/:language/lexemes`                                        | add a lexeme                         |
| `GET`    | `/lexemes/:lexemeId`                                                  | one lexeme                           |
| `DELETE` | `/lexemes/:lexemeId`                                                  | remove a lexeme                      |

A failure answers with a problem body, `{"code", "message", "details"}`, and a
status from its code: 400 for a validation failure, 403 forbidden, 404 not
found, 409 conflict, 422 refused by a policy, 501 unsupported, and a bare 500
that never carries an internal message.

## Things worth knowing before changing it

- **A profile is never edited in place.** A change publishes a revision with a
  day it takes effect from, and grading resolves the revision of the answer's
  day. `domain.paradigm.RevisionHistory` is the whole argument.
- **Syncretism is declared, not inferred.** What the endings happen to produce
  and what the grammar treats as one form are different claims, and
  `SyncretismMap.reconcileWith` reports where they disagree rather than picking
  one.
- **A validation failure carries its content in `details()`.** The message is
  for a human; a test or a route that reads only the message is asserting
  nothing.
- **Text is compared after it is normalised.** Learner input goes through
  `domain.text.Normalise` (invisible marks dropped, Unicode white space collapsed,
  NFC), and word lists sort by root-locale collation, lower case before capitals
  and accents at the second level, identically on every machine.
- **The generated surface file is sorted by code point.** A locale-aware order
  puts `Fourth` and `first` differently depending on the machine's locale, which
  turns a checked in generated file into a coin toss.

## Data on disk

    <dataDir>/journal.ndjson       every write, in order, append only
    <dataDir>/snapshots/<n>.json   a point a replay can start from

A snapshot is a shortcut and never a source of truth: deleting every snapshot
and replaying the journal from the beginning gives the same state. Compaction
writes a fresh snapshot and prunes old ones; it never removes a journal entry.
Lexeme and cell ids are digests of their content, so the same lexicon imported
twice produces the same ids and a replay is idempotent.
