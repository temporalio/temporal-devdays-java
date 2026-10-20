# Continue-As-New (Java)

**Level:** Advanced. Requires Temporal Essentials.
**Duration:** about 10 minutes of lab.
**Track:** `continue-as-new-java` (not yet published) ·
**Deck:** none yet

One Challenge, one concept. A subscription bills 30 cycles. Without
Continue-As-New the Temporal Service terminates it when its Event History passes
the limit. The learner adds Continue-As-New on the Service's suggestion
(`Workflow.getInfo().isContinueAsNewSuggested()`), carrying the billing count,
and runs it again: it finishes across a chain of runs.

| # | Challenge | Change | Check passes when |
|---|---|---|---|
| 1 | Continue-As-New | TODO 1, `SubscriptionWorkflowImpl.java` | the subscription rolls over on the Service's suggestion and bills every cycle |

The Sandbox's dev server lowers the history limits with two dynamic-config
values, so both are visible in seconds:

| Setting | Default | Track | Test |
|---|---|---|---|
| `limit.historyCount.suggestContinueAsNew` | 4,096 | 40 | 20 |
| `limit.historyCount.error` (terminates the run) | 51,200 | 200 | 100 |

Measured on the Track's values: the suggestion first appears at Event 42, the
unfixed run is terminated at Event 201 after cycle 18 (about 19 s), and the fixed
run bills 30 cycles across 8 runs in about 31 s.

## Layout

```
exercise/     learner starts here; TODOs between `// TODO N start` / `// TODO N end`
  TODOS.md    every TODO, in order
solution/     identical, TODOs done
  src/test/   SubscriptionWorkflowTest (tag challenge1) IS the Track's Check (ADR 0002);
              DevServer starts the real dev server it runs against
instruqt/     the Track: track.yml, config.yml, track_scripts/, 01-roll-it-over/
sandbox/
  Dockerfile      the Sandbox Image, FROM sandbox-base (ADR 0003)
  challenges.tsv  which Challenge owns which TODOs (for scripts/test-checks.sh)
```

Package: `io.temporal.devdays.subscription`. Task Queue: `subscription-tasks`.

## Run it locally

Java 21, Maven 3.9 and the Temporal CLI.

```bash
temporal server start-dev \
  --dynamic-config-value limit.historyCount.suggestContinueAsNew=40 \
  --dynamic-config-value limit.historyCount.error=200       # terminal 1
cd workshops/continue-as-new-subscription/solution          # or exercise
mvn -q compile exec:java@worker                             # terminal 2
mvn -q compile exec:java@starter                            # terminal 3, subscription-demo
mvn -q compile exec:java@starter -Dexec.args=mine           # subscription-mine
mvn -q test                                                 # starts its own dev server
```

Without the two flags the default limits apply and nothing visible happens in a
30-cycle run. In the lab the same commands are `run-worker` (restarts on save)
and `run-starter [tag]`.

## How the Check proves behavior

It does not read the source. The in-memory `TestWorkflowEnvironment` cannot
lower the history limits, so it never suggests Continue-As-New (a 500-cycle run
of 1,402 Events completes in one run there). `SubscriptionWorkflowTest` instead
starts a real `temporal server start-dev` on a free port with the Test limits
above (`DevServer.java`, a test-only copy of the template's helper), and bills
12 cycles. A cycle adds about 11 Events, so the fixed Workflow rolls over every
2 cycles:

- Terminated by the Service (after cycle 9, about 10 s): the run never rolled
  over. The message names TODO 1 and `isContinueAsNewSuggested()`.
- Never reaches 12 cycles (60 s execution timeout): it rolled over without
  carrying `cyclesCompleted`.
- Otherwise it expects 12 cycles and the first run Continued as New.

The passing test takes about 13 s; `mvn test` about 17 s with Maven start-up.

## Prove it

From the repo root:

```bash
(cd workshops/continue-as-new-subscription/solution && mvn -B -q test)    # Solution is proven
scripts/test-checks.sh workshops/continue-as-new-subscription             # every Check gates its Challenge
python3 scripts/check-snippets.py workshops/continue-as-new-subscription  # assignment snippets match the code
scripts/verify-content.sh                                                 # house rules

docker buildx build --platform linux/arm64 --build-arg BASE=devdays-java-sandbox-base:local \
  -f sandbox/Dockerfile -t continue-as-new-java-sandbox:local --load workshops/continue-as-new-subscription
scripts/test-track-local.sh workshops/continue-as-new-subscription continue-as-new-java-sandbox:local
```

## Instruqt Track

Every Challenge has the same four tabs, so tab buttons never shift:

| Tab | Type | Target |
|---|---|---|
| `tab-0` Exercise | service | code-server on 8080, `/?folder=/root/workshop/continue-as-new-subscription` |
| `tab-1` Worker | terminal | `exercise/` |
| `tab-2` Client | terminal | `exercise/` |
| `tab-3` Temporal UI | service | 8233 |

### First publish

```bash
docker buildx build --platform linux/amd64 -f sandbox/Dockerfile \
  -t ghcr.io/nadvolod/continue-as-new-java-sandbox:latest --push workshops/continue-as-new-subscription
# Make the GHCR package public, then pin config.yml's image to the digest:
docker buildx imagetools inspect ghcr.io/nadvolod/continue-as-new-java-sandbox:latest --format "{{.Manifest.Digest}}"

cd workshops/continue-as-new-subscription/instruqt
instruqt track push
instruqt track pull            # writes the track id and tab ids; commit them
instruqt track test
```

## Known issues

- `config.yml` holds the placeholder digest `IMAGE_DIGEST` until the first
  push. Re-pin it after every rebuild; a moving tag can boot a cached older image.
- The lab's limits live in two places: `instruqt/track_scripts/setup-workshop`
  (40 / 200, quoted in the assignment) and `SubscriptionWorkflowTest.java`
  (20 / 100). Change the assignment's numbers and expected output if you change
  the first.
- The test's execution timeout is 60 s although a passing run takes about 13 s,
  so a freshly booted Sandbox has room. Only the "count reset" failure waits
  that long; it stays inside `workshop-check`'s 180 s limit.
- The starter replaces any subscription with the same id
  (`WorkflowIdConflictPolicy` `TERMINATE_EXISTING`), so a rerun never collides
  with the last one.
