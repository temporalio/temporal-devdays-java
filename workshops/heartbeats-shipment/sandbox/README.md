# Sandbox Image

`Dockerfile` builds the image Instruqt boots for this Track, `FROM` the shared
base in [`sandbox-base/`](../../../sandbox-base/) (ADR 0003). It adds only this
Workshop's `exercise/` and `solution/`.

- The Check (`workshop-check`), Skip (`workshop-solve`) and the `run-worker` /
  `run-starter` helpers come from the base image, at `/usr/local/bin/`.
- The build fails if the Solution's tests do not pass offline, or the Exercise
  does not compile as shipped.
- `challenges.tsv` maps each coding Challenge to the file and TODOs its Skip
  splices in. `scripts/test-checks.sh` reads it.
- Build for `linux/amd64` to push; an arm64 image fails to start on Instruqt
  with an empty log. Build arm64 locally to test.
