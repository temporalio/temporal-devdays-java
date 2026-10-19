# Sandbox Image for Continue-As-New (Java). FROM the shared base (ADR 0003), which
# already holds JDK 21, Maven, the Temporal CLI, code-server with Java support,
# watchexec, the Check/Skip/run helpers and a warm ~/.m2.
# Build context is workshops/continue-as-new-subscription/ so this can COPY exercise/ and solution/.
#
#   docker buildx build --platform linux/amd64 \
#     -f sandbox/Dockerfile -t ghcr.io/nadvolod/continue-as-new-java-sandbox:latest --push .
#
# Local test on Apple Silicon, native and against a local base:
#   docker buildx build --platform linux/arm64 --build-arg BASE=devdays-java-sandbox-base:local \
#     -f sandbox/Dockerfile -t continue-as-new-java-sandbox:local --load .
#   scripts/test-track-local.sh workshops/continue-as-new-subscription continue-as-new-java-sandbox:local

# Pinned by digest, not :latest. Re-pin after every base rebuild with:
#   docker buildx imagetools inspect ghcr.io/nadvolod/devdays-java-sandbox-base:latest --format "{{.Manifest.Digest}}"
ARG BASE=ghcr.io/nadvolod/devdays-java-sandbox-base@sha256:d0ac271ce50f195b9e55059a57dd5c5e8da831e43c26d944e240e224ec8a6714
FROM ${BASE}

# --- Bake the workshop source -------------------------------------------------
# Straight to /root/workshop. Skip splices single TODOs from solution/ into
# exercise/ (workshop-solve), so no pristine copy is needed.
ENV WORKSHOP=/root/workshop/continue-as-new-subscription
COPY exercise/ ${WORKSHOP}/exercise/
COPY solution/ ${WORKSHOP}/solution/

# --- Pre-warm and prove -------------------------------------------------------
# The Solution's tests must pass, offline, or the build fails. They start their
# own `temporal server start-dev` (DevServer.java) on a free port, with the
# Temporal CLI the base image holds, so PROVE needs no network either. The Exercise must
# compile as shipped; compiling both its builds now makes the learner's first
# run-worker and first Check take seconds.
#
# PROVE=0 skips running the Solution's tests (still compiles them). Use it only
# for an amd64 build under emulation on Apple Silicon; prove the same commit
# with an arm64 build first.
ARG PROVE=1
WORKDIR ${WORKSHOP}/solution
RUN if [ "$PROVE" = "1" ]; then mvn -B -q -o test; else mvn -B -q -o test-compile; fi \
    && rm -rf target

WORKDIR ${WORKSHOP}/exercise
RUN mvn -B -q -o compile \
    && mvn -B -q -o -Dbuild.dir=target-check test-compile

WORKDIR /root
CMD ["/bin/bash"]
