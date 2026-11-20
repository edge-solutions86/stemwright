# Architecture

Four layers, and the rule is that a layer may only reach downwards. Every package below lives
under `io.github.rkaushikdev.stemwright`.

    http, cli, bin   take a request, hand it to a service, format the answer
    app              load what the domain needs, check who is asking, announce
    domain           every rule, no io and no clock it was not handed
    infra, io        stores, the journal, snapshots, backups, file formats

## What lives where

**`domain` has no io and no clock of its own.** Every function that depends on the day takes the
day. That is what lets a term of drilling be simulated in a test in under a second, and it is why
the same code answers both what a learner would see today and what they saw in March.

**`app` is where a request meets the rules.** It loads, it checks permissions, it writes, and it
announces. It holds no rule that could be stated in the domain, and the two or three that live
here are marked as such: a deck may not be saved empty, an import is a dry run unless told
otherwise, and a revision may not be given a date that has already passed.

**`infra` is replaceable.** The stores are interfaces in `app` (`Stores` and its members) and the
in memory implementations in `infra.MemoryStores` are one of several possible answers. The
journal is the only durable thing; everything else can be rebuilt from it.

## The dependency that shapes everything

A cell is graded against the paradigm revision in force on the day of the answer. That single
decision reaches:

- the expected form, realised from that revision's profile
- the twin set, taken from that revision's syncretism map
- credit, which flows to those twins and no others
- burial, which hides those twins for the rest of the sitting
- tomorrow's queue, which is what is left after burial
- a report, which resolves the same way and therefore agrees with the transcript the learner was
  shown

Nothing reaches for the newest revision. `RevisionHistory.revisionInForce` returns an empty
`Optional` rather than falling back, because a silent fallback is exactly the bug the type exists
to prevent.

## Writing an answer

Three writes, in this order, and the order is the recovery story.

1. the attempt log
2. the open session
3. the mastery ledger

An attempt recorded without a ledger update is a lost schedule change that the next review
corrects. A ledger update without the attempt is a mark nobody can account for. So the ledger goes
last.

## Where the tests live

Every suite sits in `src/test/java`, in the package of the code it checks.

| suite                     | what it holds                                                    |
| ------------------------- | ---------------------------------------------------------------- |
| `<Type>Test`              | one type against its contract, and services, routes and the cli over a store |
| `*InvariantsTest`         | facts that must hold after real use, plus source hygiene         |
| `*PropertyTest`           | seeded draws over features, mastery, orthography and reports     |
| `performance` package     | timing ceilings, run on their own with `./mvnw -B -Pperf test`   |
| `support` package         | the shared fixtures: schemas, profiles, lexemes, stores, http    |
| `tooling` package         | the surface, release, verify and bench developer tools           |
