# Architecture

Four layers, and the rule is that a layer may only reach downwards.

    src/http, src/cli   take a request, hand it to a service, format the answer
    src/app             load what the domain needs, check who is asking, announce
    src/domain          every rule, no io and no clock it was not handed
    src/infra           stores, the journal, snapshots, backups

## What lives where

**`src/domain` has no io and no clock of its own.** Every function that depends
on the day takes the day. That is what lets a term of drilling be simulated in a
test in under a second, and it is why the same code answers both what a learner
would see today and what they saw in March.

**`src/app` is where a request meets the rules.** It loads, it checks
permissions, it writes, and it announces. It holds no rule that could be stated
in the domain, and the two or three that live here are marked as such: a deck
may not be saved empty, an import is a dry run unless told otherwise, and a
revision may not be given a date that has already passed.

**`src/infra` is replaceable.** The stores are interfaces in `src/app/ports.ts`
and the in memory implementations are one of several possible answers. The
journal is the only durable thing; everything else can be rebuilt from it.

## The dependency that shapes everything

A cell is graded against the paradigm revision in force on the day of the
answer. That single decision reaches:

- the expected form, realised from that revision's profile
- the twin set, taken from that revision's syncretism map
- credit, which flows to those twins and no others
- burial, which hides those twins for the rest of the sitting
- tomorrow's queue, which is what is left after burial
- a report, which resolves the same way and therefore agrees with the
  transcript the learner was shown

Nothing reaches for the newest revision. `revisionInForce` returns `undefined`
rather than falling back, because a silent fallback is exactly the bug the
module exists to prevent.

## Writing an answer

Three writes, in this order, and the order is the recovery story.

1. the attempt log
2. the open session
3. the mastery ledger

An attempt recorded without a ledger update is a lost schedule change that the
next review corrects. A ledger update without the attempt is a mark nobody can
account for. So the ledger goes last.

## Where the tests live

| directory           | what it holds                                            |
| ------------------- | -------------------------------------------------------- |
| `tests/unit`        | one module against its contract                          |
| `tests/integration` | services, routes and the cli over a store                |
| `tests/invariants`  | facts that must hold after real use, plus source hygiene |
| `tests/property`    | seeded draws over features, mastery, ortho and reports   |
| `tests/performance` | timing ceilings, run as their own job                    |
