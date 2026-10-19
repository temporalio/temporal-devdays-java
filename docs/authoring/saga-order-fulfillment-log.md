# Authoring log: The Saga Pattern (Java)

Port of the Saga Pattern Workshop (`saga-order-fulfillment`). Track slug
`saga-java`. Times are UTC. No Deck yet.

| Phase | Start | End |
|---|---|---|
| Track: read repo rules, template, source Workshop, Saga API at SDK 1.40.0 | 2026-10-19T17:10:10Z | 2026-10-19T17:12:39Z |
| Track: code (Exercise, Solution, tests, challenges.tsv, test-checks) | 2026-10-19T17:12:39Z | 2026-10-19T17:17:20Z |
| Track: Instruqt Track (track.yml, config.yml, assignments, scripts, README) | 2026-10-19T17:17:20Z | 2026-10-19T17:19:00Z |
| Track: image build and local Track test, real-output capture, content checks | 2026-10-19T17:19:00Z | 2026-10-19T17:21:24Z |

Notes:

- `scripts/test-checks.sh` was run with a local `mktemp` shim on PATH: on macOS,
  BSD `mktemp` does not randomise `XXXXXX` followed by `.log`, so parallel
  `workshop-check` runs collide on `/tmp/workshop-check-XXXXXX.log`.
