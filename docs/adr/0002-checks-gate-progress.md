# Checks gate progress

Every Challenge's Check fails until that Challenge's work is done, and says what
is missing and where. A Check never passes unconditionally.

This carries over the decision of `temporal-devdays-python` (its ADR 0002). The
wrap-up of a customer Dev Day (2026-09-15/16) ranked instant-pass Checks as its
top finding (P0): learners clicked Check without doing the work, advanced, and
hit a failure three Challenges later with no way to tell which step they had
missed.

## How a Check decides

A Check runs, in order, and stops at the first failure:

1. **The code still compiles.** `mvn compile test-compile` over the Exercise. A
   completed step must never leave the tree broken. The learner sees the first
   compiler error, with its file and line.
2. **This Challenge's tests pass.** The Exercise's own JUnit 5 tests, selected
   by `@Tag("challengeN")`, run against `TestWorkflowEnvironment`, or a dev
   server the test starts itself where the in-memory server cannot reproduce
   the behaviour. Earlier Challenges' tests run too, so an undone earlier step
   is reported first.
3. **The failure is specific.** The learner sees the failing test's assertion
   message, which names the file, the method and the TODO. Test assertions
   are therefore written for learners, not for authors.

Observe-only Challenges check the outcome instead: the Workflow the learner was
asked to run exists, in the expected state, in the local dev server.

## Considered Options

Instant-pass Checks were rejected. Their two arguments — gating pushes stuck
learners to Skip, and Check latency costs minutes across a room — are answered
by Skip that leaves the next Challenge runnable (the solve script splices in
that TODO from the Solution, backing up the learner's file) and by Checks that
finish in seconds against a pre-warmed, offline Maven cache.

Grepping the source for the expected change was rejected: it passes code that
is present but wrong and fails correct code written differently.

## Consequences

Tests and Checks are one artifact: the Check is the Exercise's test suite plus a
translation of the first failure into a message. A Workshop whose tests cannot
tell one TODO from another cannot have a gated Check, so tests are written one
group per Challenge.

Assignments show the code to change with its line range. `scripts/check-snippets.py`
fails when an assignment's snippet or line range no longer matches the file, so
instructions and the running lab cannot drift apart.
