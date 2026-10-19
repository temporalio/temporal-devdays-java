# One shared Sandbox base image

Every Java Track boots a Sandbox Image built `FROM` one shared base,
`sandbox-base/`. The base holds JDK 21, Maven, the Temporal CLI, code-server
with Java language support, watchexec, and the `workshop-check` and
`workshop-solve` helpers. A Workshop's own `sandbox/Dockerfile` adds only its
Exercise and Solution code and a pre-warmed `~/.m2` cache.

The Python repo copies its helpers byte-for-byte into each Workshop and repeats
near-identical Dockerfiles. Java's toolchain is heavier (JDK, Maven cache, the
Java language server), so per-Workshop copies would multiply build time and let
a fix to a Check helper land in one Track and not the others.

## Considered Options

Per-Workshop self-contained Dockerfiles, mirroring the Python repo, were
rejected: the duplication is larger in Java and the helpers would drift.

## Consequences

A change to the base requires rebuilding and re-pinning every Workshop's
Sandbox Image. Each Track's `config.yml` still pins its own image by digest.
