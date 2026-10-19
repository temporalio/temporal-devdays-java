# The Saga Pattern (Java)

**Level:** Advanced. Requires Temporal Essentials.
**Duration:** 45 minutes: about 20 minutes of Deck, then the lab.
**Track:** `saga-java` (not yet published) ·
**Deck:** Pending

An order reserves stock, charges a card, and books a courier. The courier fails.
Learners wire the SDK's `io.temporal.workflow.Saga` class into the Workflow and
make its undos safe, in four Challenges of one change each:

| # | Challenge | Change | Check passes when |
|---|---|---|---|
| 1 | Watch it fail | none | a Failed `OrderFulfillmentWorkflow` exists |
| 2 | Unwind | TODO 1, `OrderFulfillmentWorkflowImpl.java` | undos run newest first; one failing undo does not stop the rest |
| 3 | Register before you run | TODO 2a/2b, `OrderFulfillmentWorkflowImpl.java` | a step that lands and then fails is still undone |
| 4 | Make the undo safe to repeat | TODO 3a/3b, `OrderActivitiesImpl.java` | each undo is a no-op with nothing to undo, and acts once when run twice |

## How the `Saga` class maps to the TODOs

| TODO | `Saga` API | Lesson |
|---|---|---|
| 1 | `new Saga(new Saga.Options.Builder().setContinueWithError(true).build())`, `saga.compensate()` | Sequential (the default) runs undos newest first. The default also stops at the first failing undo; `continueWithError` keeps going, silently. |
| 2a/2b | `saga.addCompensation(activities::undo, order)` above each forward call | The Saga can only run undos it was given. A step that lands and then throws never reaches a line below it. |
| 3a/3b | none: plain guard clauses in the undo Activities | The Saga cannot know what an undo did. Registering first means undos run with nothing to undo, so each must be idempotent. |

## Layout

```
exercise/     learner starts here; TODOs between `// TODO N start` / `// TODO N end`
  TODOS.md    every TODO, in order
solution/     identical, TODOs done
  src/test/   one test group per Challenge (@Tag challenge2..challenge4);
              these ARE the Track's Checks (ADR 0002)
instruqt/     the Track: track.yml, config.yml, track_scripts/, 01..04/
sandbox/
  Dockerfile      the Sandbox Image, FROM the shared base (ADR 0003)
  challenges.tsv  which Challenge owns which TODOs (for scripts/test-checks.sh)
```

Code lives in `src/main/java/io/temporal/devdays/saga/`.

## Run it locally

```bash
temporal server start-dev                                  # terminal 1
cd workshops/saga-order-fulfillment/solution               # or exercise
mvn -q compile exec:java@worker                            # terminal 2
mvn -q compile exec:java@starter -Dexec.args="--fail-dispatch"   # terminal 3
mvn -q test                                                # no server needed
```

In the lab the same two commands are `run-worker` (which restarts the Worker on
every save) and `run-starter --fail-dispatch`.

Scenarios: no flag (clean order), `--fail-dispatch`, `--charge-times-out`,
`--fail-reserve`.

## Prove it

From the repo root:

```bash
(cd workshops/saga-order-fulfillment/solution && mvn -B -q test)  # Solution is proven
scripts/test-checks.sh workshops/saga-order-fulfillment            # every Check gates its Challenge
python3 scripts/check-snippets.py workshops/saga-order-fulfillment # assignment snippets match the code
scripts/verify-content.sh                                          # house rules

# Native build on Apple Silicon, against a local base. The build runs the
# Solution's tests, so it fails if the Workshop is broken.
docker buildx build --platform linux/arm64 --build-arg BASE=devdays-java-sandbox-base:local \
  -f sandbox/Dockerfile -t saga-java-sandbox:local --load workshops/saga-order-fulfillment
scripts/test-track-local.sh workshops/saga-order-fulfillment saga-java-sandbox:local
```

`test-track-local.sh` is `instruqt track test` without the push: it boots the
image, runs track setup, and for each Challenge requires the Check to fail, the
solve script to succeed, and the Check to pass.

## Instruqt Track

Every Challenge has the same four tabs, so tab buttons never shift:

| Tab | Type | Target |
|---|---|---|
| `tab-0` Exercise | service | code-server on 8080, `/?folder=/root/workshop/saga-order-fulfillment` |
| `tab-1` Worker | terminal | `exercise/` |
| `tab-2` Client | terminal | `exercise/` |
| `tab-3` Temporal UI | service | 8233 |

### First publish

```bash
gh auth refresh -s write:packages,read:packages
# Instruqt needs linux/amd64. On Apple Silicon that build is emulated, so add
# --build-arg PROVE=0 there, after the native build above has proven the same
# commit. On an amd64 host, omit it.
docker buildx build --platform linux/amd64 --build-arg PROVE=0 -f sandbox/Dockerfile \
  -t ghcr.io/nadvolod/saga-java-sandbox:latest --push workshops/saga-order-fulfillment
# Make the GHCR package public, then pin config.yml's image to the digest:
docker buildx imagetools inspect ghcr.io/nadvolod/saga-java-sandbox:latest --format "{{.Manifest.Digest}}"

instruqt update && instruqt auth login
cd workshops/saga-order-fulfillment/instruqt
instruqt track create saga-java --title "The Saga Pattern (Java)"   # once; keep your local files
instruqt track push --force
instruqt track pull            # writes the track id and tab ids; commit them
instruqt track test            # simulates a learner via the solve scripts
```

## Known issues

- `config.yml` is pinned to an image digest placeholder until the first push.
  Re-pin it after every rebuild; a moving tag can boot a cached older image.
- The Worker keeps the ledger in memory, so a save (which restarts it) clears
  the ledger. Each order has a new id, so nothing a learner reads is affected.
- With `setContinueWithError(true)` the `Saga` swallows a failing undo without
  logging it; `compensate()` returns normally. The failed Activity is in the
  Event History. Production code would alert a human there.
- Cancellation is out of scope. `fulfill` catches `ActivityFailure` only, so a
  cancelled Workflow does not unwind. Production code runs `compensate()` in a
  detached `CancellationScope` (`Workflow.newDetachedCancellationScope`) so the
  undos survive cancellation. Worth one sentence in the debrief.
- The code-server tab opens the folder, not the file. Assignments give the file,
  the line range and the `Ctrl+P` / `Ctrl+G` keys instead.
