#!/bin/bash
# ABOUTME: Proves a Workshop's gated Checks (ADR 0002). For each coding Challenge,
# in a scratch copy of the Exercise: the Check must FAIL with that Challenge's TODOs
# undone (earlier ones done), and PASS once they are spliced in from the Solution.
#
#   scripts/test-checks.sh workshops/<slug>
#
# Reads workshops/<slug>/sandbox/challenges.tsv: one line per coding Challenge,
#   <challenge-number> <TAB> <file> <TAB> <TODO label>[,<TODO label>...]
# Runs the helpers from sandbox-base/bin, so it needs Java 21, Maven and (for
# tests that start a dev server) the temporal CLI locally. Set
# WORKSHOP_MVN_FLAGS= to let Maven download on a cold ~/.m2.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WS="$(cd "${1:?usage: test-checks.sh workshops/<slug>}" && pwd)"
BIN="$ROOT/sandbox-base/bin"
MAP="$WS/sandbox/challenges.tsv"
SCRATCH="$(mktemp -d /tmp/test-checks-XXXXXX)"
trap 'rm -rf "$SCRATCH"' EXIT
cp -R "$WS/exercise" "$SCRATCH/exercise"
rm -rf "$SCRATCH/exercise/target" "$SCRATCH/exercise/target-check"
EXCLUDES=(-x target -x target-check -x '*.bak')
FAILS=0

# Rows that share a Challenge number (one Challenge, TODOs in several files) are
# one group: check once before, splice every row, check once after.
ROWS=()
while IFS= read -r line; do
    [ -z "$line" ] || [ "${line:0:1}" = "#" ] && continue
    ROWS+=("$line")
done <"$MAP"

i=0
while [ "$i" -lt "${#ROWS[@]}" ]; do
    N="${ROWS[$i]%%$'\t'*}"
    GROUP=()
    while [ "$i" -lt "${#ROWS[@]}" ] && [ "${ROWS[$i]%%$'\t'*}" = "$N" ]; do
        GROUP+=("${ROWS[$i]}"); i=$((i + 1))
    done
    LABELS="$(for row in "${GROUP[@]}"; do echo "${row##*$'\t'}"; done | paste -sd',' -)"

    printf 'Challenge %s, before %s: ' "$N" "$LABELS"
    if MSG="$("$BIN/workshop-check" "$SCRATCH/exercise" "$N" 2>&1)"; then
        echo "PASSED, but must fail"; FAILS=$((FAILS + 1))
    else
        echo "fails as it should"; echo "    learner sees: ${MSG#FAIL: }"
    fi
    for row in "${GROUP[@]}"; do
        IFS=$'\t' read -r _ FILE TODOS <<<"$row"
        IFS=',' read -r -a LIST <<<"$TODOS"
        "$BIN/workshop-solve" "$SCRATCH/exercise" "$WS/solution" "$FILE" "${LIST[@]}" >/dev/null
    done
    printf 'Challenge %s, after  %s: ' "$N" "$LABELS"
    if MSG="$("$BIN/workshop-check" "$SCRATCH/exercise" "$N" 2>&1)"; then
        echo "passes"
    else
        echo "FAILED, must pass"; echo "    ${MSG}"; FAILS=$((FAILS + 1))
    fi
done

# Every TODO spliced in, the scratch Exercise must equal the Solution.
if diff -rq "${EXCLUDES[@]}" "$SCRATCH/exercise" "$WS/solution" >/dev/null; then
    echo "All TODOs done: the Exercise is identical to the Solution."
else
    echo "All TODOs done, but the Exercise still differs from the Solution:"
    diff -r "${EXCLUDES[@]}" "$SCRATCH/exercise" "$WS/solution" | head -20
    FAILS=$((FAILS + 1))
fi

[ "$FAILS" -eq 0 ] && echo "OK: every Check gates its Challenge." || echo "$FAILS problem(s)."
exit $((FAILS > 0))
