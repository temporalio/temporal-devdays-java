# Track

The Instruqt Track for this Workshop: `track.yml`, `config.yml`,
`track_scripts/`, and one directory per Challenge with its `assignment.md` and
lifecycle scripts. Challenge folders hold only `assignment.md` and lifecycle
scripts; Instruqt parses anything else in them as an event script.

`01-challenge-slug/` holds the two lifecycle scripts only. Copy
[`ASSIGNMENT-SKELETON.md`](ASSIGNMENT-SKELETON.md) into each Challenge folder as
`assignment.md`; the skeleton itself lives here, outside any Challenge folder.

## Challenges are small

One concept, one change, one file, five minutes at most. A Challenge that asks
for two unrelated changes is two Challenges. Paired edits that are the same move
made twice (TODO 2a and 2b) stay together.

## Checks gate progress

Every Check fails until its Challenge is done, and says what is missing and
where ([ADR 0002](../../../docs/adr/0002-checks-gate-progress.md)).

- **Coding Challenge:** `check-workshop` is one line,
  `exec workshop-check /root/workshop/<slug>/exercise <N>`. The helper is baked
  into the base image ([`sandbox-base/bin/`](../../../sandbox-base/bin/)). It
  compiles the Exercise, then runs the tests tagged `challenge1`..`challengeN`
  and shows the first failing assertion message.
- **Observe-only Challenge:** check the outcome with `temporal workflow count`,
  e.g. a Failed Workflow of the expected type exists.
- **Skip** must leave the next Challenge runnable: `solve-workshop` splices the
  TODO in from the Solution with `workshop-solve`, keeping the learner's file
  as `<File>.java.bak`.

## Every assignment has the same shape

Copy [`ASSIGNMENT-SKELETON.md`](ASSIGNMENT-SKELETON.md). It shows the learner
the exact code to change, with its line range, so they never scroll a file to
find a TODO. Mark each snippet with `<!-- snippet: exercise/<path> TODO N -->`
and run `python3 scripts/check-snippets.py workshops/<slug>` from the repo root;
it fails when a snippet or line range no longer matches the code.

Every command a learner might run goes in its own `bash,run` block, even a
"restart it if it stopped" aside. An inline `` `command` `` in prose cannot be
clicked, and gets retyped by hand.

## Running code in the lab

- **Worker tab:** `run-worker`. It compiles and runs the Worker, and restarts
  it on every save.
- **Client tab:** `run-starter <args>`. It compiles and runs the starter.

## Editor

code-server on port 8080, one instance rooted at the Workshop folder, as a
`type: service` tab, with Red Hat's Java extension. Port 8080 must be listed
under `ports:` in `config.yml`, and the container needs `memory: 6144`.
