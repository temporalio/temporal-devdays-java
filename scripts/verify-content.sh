#!/bin/bash
# ABOUTME: Repo-local content guardrails.
#
#   1. Every assignment snippet matches its code (scripts/check-snippets.py).
#   2. One SDK per Workshop (AGENTS.md): no Workshop, Track or Deck names another
#      Temporal SDK language.
#   3. Every Challenge ends by asking for feedback (AGENTS.md).
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FAIL=0

python3 "$ROOT/scripts/check-snippets.py" || FAIL=1

DIRS=()
for d in "$ROOT/workshops" "$ROOT/decks"; do [ -d "$d" ] && DIRS+=("$d"); done
HITS="$(grep -rnIE '\b(Python|TypeScript|JavaScript|Golang|Go SDK|Python SDK|\.NET|C#|Ruby SDK|PHP SDK)\b' \
    "${DIRS[@]}" \
    --exclude-dir=target --exclude-dir=target-check --exclude-dir=node_modules \
    --exclude-dir=_template --exclude-dir=theme \
    --exclude='package-lock.json' 2>/dev/null || true)"
if [ -n "$HITS" ]; then
    echo "[FAIL] content names another SDK language (one SDK per Workshop):"
    echo "$HITS" | sed 's/^/       - /'
    FAIL=1
else
    echo "[ OK ] no other SDK language named in Workshops or Decks"
fi

MISSING=()
for f in "$ROOT"/workshops/*/instruqt/*/assignment.md; do
    [ -e "$f" ] || continue
    tail -n 4 "$f" | grep -q 'Please share your feedback' || MISSING+=("${f#$ROOT/}")
done
if [ ${#MISSING[@]} -gt 0 ]; then
    echo "[FAIL] Challenge does not end by asking for feedback (copy the block from the assignment skeleton):"
    printf '       - %s\n' "${MISSING[@]}"
    FAIL=1
else
    echo "[ OK ] every Challenge ends by asking for feedback"
fi
exit $FAIL
