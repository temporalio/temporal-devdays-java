# Activity Heartbeats (Java)

**Level:** Advanced. Requires Temporal Essentials.
**Duration:** about 10 minutes of lab.
**Track:** `heartbeats-java` (not yet published) ·
**Deck:** none yet

One Challenge, one concept. A packing Activity works through 40 items, 0.3 s
each, under a 20 s start-to-close timeout, with up to 10 attempts. The learner
crashes its Worker with Ctrl+C partway through, twice:

- **Without Heartbeats:** the retry waits out the attempt's timeout (measured:
  killed at item 10, attempt 2 started 19 s later) and starts again at item 0.
- **With Heartbeats:** the retry starts as soon as a replacement Worker is up,
  about 2 s after the kill (the Heartbeat Timeout), and resumes from the last
  Heartbeat (measured: killed at item 10, resumed at item 6, the gap being
  Heartbeat throttling).

| # | Challenge | Change | Check passes when |
|---|---|---|---|
| 1 | Activity Heartbeats | TODO 1a `ShipmentWorkflowImpl.java`; 1b, 1c `ShipmentActivitiesImpl.java` | a silent attempt is retried in seconds, every item is Heartbeated, and a retry resumes from the last Heartbeat |

Cancellation through the Heartbeat is a closing note in the assignment, not a
step: the next `heartbeat()` call throws `ActivityCompletionException`, and
`packShipment` lets it out.

## Layout

```
exercise/     learner starts here; TODOs between `// TODO 1x start` / `// TODO 1x end`
  TODOS.md    every TODO, in order
solution/     identical, TODOs done
  src/test/   ShipmentHeartbeatTest, one test per part of TODO 1, tag challenge1;
              it IS the Track's Check (ADR 0002)
instruqt/     the Track: track.yml, config.yml, track_scripts/, 01-heartbeats/
sandbox/
  Dockerfile      the Sandbox Image, FROM sandbox-base (ADR 0003)
  challenges.tsv  which TODOs the Challenge owns, one line per file
                  (both lines are Challenge 1)
```

Package: `io.temporal.devdays.shipment`. Task Queue: `shipment-tasks`.

## Run it locally

Java 21, Maven 3.9 and the Temporal CLI.

```bash
temporal server start-dev                                 # terminal 1
cd workshops/heartbeats-shipment/solution                 # or exercise
mvn -q compile exec:java@worker                           # terminal 2; Ctrl+C it around item 10, then rerun
mvn -q compile exec:java@starter                          # terminal 3, shipment-demo
mvn -q compile exec:java@starter -Dexec.args=mine         # shipment-mine
mvn -q test                                               # no server needed
```

Ctrl+C is a crash: the Worker registers no shutdown hook, so the JVM exits
without failing the running attempt, and Temporal only learns of it from a
timeout. In the lab the Worker runs under `run-worker`, which restarts it on
save; a restart on save is a crash too, and Ctrl+C stops `run-worker` along
with the Worker.

## How the Check proves behavior

None of it reads the source. The tests run in order (`@Order`), so the first
missing part is the one reported. About 5 s of tests on the Solution, 16 s on the
untouched Exercise.

- **1a** runs the learner's Workflow in `TestWorkflowEnvironment` against a
  stand-in `packShipment` whose attempt 1 goes silent and never reports back,
  the way a dead Worker does (a Heartbeat Timeout counts from the attempt's
  start, so it needs no Heartbeat). With an 8 s execution timeout,
  only a Heartbeat Timeout gets attempt 2 started in time. The in-memory server
  enforces the Heartbeat Timeout, and it does so in real time because time
  skipping is held while an Activity runs: measured 2.8 s to pass, 8.7 s to fail.
  No dev server is needed.
- **1b** runs the learner's real `packShipment` in `TestActivityEnvironment` and
  expects Heartbeats `1, 2, 3` for 3 items. The stub's Heartbeat Timeout is
  100 ms, so the SDK's throttle (0.8 of the timeout) lets every Heartbeat through.
- **1c** sets Heartbeat details `16` and expects the first Heartbeat to report 17
  and all 18 items packed; and with no details, expects it to start at 0.
  `TestActivityEnvironment` cannot set the attempt number (it is always 0), so
  the tests depend only on the details, as the code does.

## Prove it

From the repo root:

```bash
(cd workshops/heartbeats-shipment/solution && mvn -B -q test)    # Solution is proven
scripts/test-checks.sh workshops/heartbeats-shipment             # every Check gates its Challenge
python3 scripts/check-snippets.py workshops/heartbeats-shipment  # assignment snippets match the code
scripts/verify-content.sh                                        # house rules

docker buildx build --platform linux/arm64 --build-arg BASE=devdays-java-sandbox-base:local \
  -f sandbox/Dockerfile -t heartbeats-java-sandbox:local --load workshops/heartbeats-shipment
scripts/test-track-local.sh workshops/heartbeats-shipment heartbeats-java-sandbox:local
```

## Instruqt Track

Every Challenge has the same four tabs, so tab buttons never shift:

| Tab | Type | Target |
|---|---|---|
| `tab-0` Exercise | service | code-server on 8080, `/?folder=/root/workshop/heartbeats-shipment` |
| `tab-1` Worker | terminal | `exercise/`; `run-worker` runs here |
| `tab-2` Client | terminal | `exercise/`; `run-starter` runs here |
| `tab-3` Temporal UI | service | 8233 |

### First publish

```bash
gh auth refresh -s write:packages,read:packages
docker buildx build --platform linux/amd64 --build-arg PROVE=0 -f sandbox/Dockerfile \
  -t ghcr.io/nadvolod/heartbeats-java-sandbox:latest --push workshops/heartbeats-shipment
# Make the GHCR package public, then pin config.yml's image to the digest:
docker buildx imagetools inspect ghcr.io/nadvolod/heartbeats-java-sandbox:latest --format "{{.Manifest.Digest}}"

cd workshops/heartbeats-shipment/instruqt
instruqt track create heartbeats-java --title "Activity Heartbeats (Java)"   # once; keep your local files
instruqt track push --force
instruqt track pull            # writes the track id and tab ids; commit them
instruqt track test
```

## Known issues

- `config.yml` is pinned to an image digest placeholder (`IMAGE_DIGEST`), and the
  Sandbox Image's `BASE` to `BASE_DIGEST`. Pin both before the first push.
- TODO 1 spans two files, so `sandbox/challenges.tsv` has two lines for
  Challenge 1 and the solve script splices both.
- Order matters for a learner who runs early: a Heartbeat Timeout (1a) with no
  Heartbeats (1b) fails every attempt after 2 s. The assignment says to do all
  three parts before running, and the Worker restarts on every save.
- Heartbeats carry a plain `Integer`, read back with
  `getHeartbeatDetails(Integer.class)`.
