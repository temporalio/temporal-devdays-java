# Child Workflows (Java)

**Level:** Foundations. Requires Temporal Essentials.
**Duration:** 15 minutes of lab, after the Child Workflows section of the Deck.
**Track:** `child-workflows-java` (not yet published) ·
**Deck:** Pending

Run a child, chain children five deep, fan out, fan in. Challenges 1 to 3 are
run and observe: the learner runs one starter command and reads the result in
the Temporal UI. Challenge 4 is the one TODO: write the fan in.

| # | Challenge | Learner does | Check passes when |
|---|---|---|---|
| 1 | Run a child | `run-starter child` | a `ShipmentWorkflow` child has Completed |
| 2 | A deeply nested chain | `run-starter chain --fail` | six `ChainWorkflow`s have Failed (one five-level chain) |
| 3 | Fan out | `run-starter pack`, TODO 1 still undone | five `PackItemWorkflow`s were Terminated |
| 4 | Fan in | TODO 1, then `run-starter pack` | the `challenge1`..`challenge4` tests pass, and five `PackItemWorkflow`s have Completed |

Challenge 3 is meant to look broken: with TODO 1 undone, `PackWorkflowImpl`
returns without waiting, and the default `TERMINATE` Parent Close Policy kills
all five children. Challenge 4 fixes that same method with `Promise.allOf`.

| TODO | Challenge | File | What to do |
|---|---|---|---|
| 1 | 4 | `PackWorkflowImpl.java` | Wait for every child with `Promise.allOf(boxes)`, return each box in item order. |

## Layout

```
exercise/     the learner's terminals start here; TODO 1 undone
solution/     the proven code, identical except inside TODO 1
  src/test/   ChildWorkflowsTest, one group per Challenge (@Tag("challengeN"))
instruqt/     the Track: track.yml, config.yml, track_scripts/, 01..04/
sandbox/
  Dockerfile      the Sandbox Image, FROM the shared base
  challenges.tsv  the coding Challenge and the TODO its Skip splices in
```

The tests tagged `challenge1` to `challenge3` pass on the shipped Exercise:
those Challenges are observe-only, and their Checks count Workflows in the dev
server instead (ADR 0002). The `challenge4` tests fail until TODO 1 is done.

## Run it locally

```bash
temporal server start-dev                                        # terminal 1
cd workshops/child-workflows/exercise                            # or solution
mvn -q compile exec:java@worker                                  # terminal 2
mvn -q compile exec:java@starter -Dexec.args="child"             # terminal 3
mvn -q compile exec:java@starter -Dexec.args="chain --fail"
mvn -q compile exec:java@starter -Dexec.args="chain --depth 3"
mvn -q compile exec:java@starter -Dexec.args="pack"
mvn -q test                                                      # no server needed
```

## Prove it

```bash
(cd workshops/child-workflows/solution && mvn -B -q test)   # Solution is proven
bash scripts/test-checks.sh workshops/child-workflows       # Challenge 4's Check gates TODO 1
python3 scripts/check-snippets.py workshops/child-workflows

docker buildx build --platform linux/arm64 --build-arg BASE=devdays-java-sandbox-base:local \
  -f sandbox/Dockerfile -t child-workflows-java-sandbox:local --load workshops/child-workflows
bash scripts/test-track-local.sh workshops/child-workflows child-workflows-java-sandbox:local
```

## Instruqt Track

Every Challenge has the same four tabs, so tab buttons never shift:

| Tab | Type | Target |
|---|---|---|
| `tab-0` Exercise | service | code-server on 8080, `/?folder=/root/workshop/child-workflows` |
| `tab-1` Worker | terminal | `exercise/` |
| `tab-2` Client | terminal | `exercise/` |
| `tab-3` Temporal UI | service | 8233 |

### First publish

```bash
docker buildx build --platform linux/amd64 -f sandbox/Dockerfile \
  -t ghcr.io/nadvolod/child-workflows-java-sandbox:latest --push workshops/child-workflows
# Make the GHCR package public, then pin config.yml's image to the digest:
docker buildx imagetools inspect ghcr.io/nadvolod/child-workflows-java-sandbox:latest --format "{{.Manifest.Digest}}"

cd workshops/child-workflows/instruqt
instruqt track create child-workflows-java --title "Child Workflows (Java)"   # once
instruqt track push --force
instruqt track pull            # writes the track id and tab ids; commit them
instruqt track test
```

## Known issues

- `config.yml` holds the placeholder digest `IMAGE_DIGEST` until the first
  push. Re-pin it after every rebuild; a moving tag can boot a cached older
  image.
- The observe-only Checks count Workflows in the dev server, so they pass for
  anyone who ran the starter, even from `solution/`. That is the point of an
  observe-only Check.
- Challenge 3 relies on TODO 1 being undone. A learner who writes the fan in
  early gets five Completed children instead of five Terminated, and
  Challenge 3's Check (and its Skip) cannot pass; undo TODO 1 or restore
  `PackWorkflowImpl.java` to run it.
- Each Challenge opens fresh terminals, so the Worker does not survive into
  the next one. Every assignment starts it again.
