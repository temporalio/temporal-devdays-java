#!/bin/bash
# ABOUTME: Fails when a Deck or Workshop folder has no row in the README index.
# Every PR that adds decks/<slug>/ or workshops/<slug>/ adds its README row in
# the same PR (AGENTS.md). Templates are skipped.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
README="$ROOT/README.md"
FAIL=0

for kind in decks workshops; do
  [ -d "$ROOT/$kind" ] || continue
  for dir in "$ROOT/$kind"/*/; do
    slug="$(basename "$dir")"
    [ "$slug" = "_template" ] && continue
    if ! grep -q "$kind/$slug/" "$README"; then
      echo "[FAIL] $kind/$slug/ has no row in README.md. Add it to the Workshops table."
      FAIL=1
    fi
  done
done

[ $FAIL -eq 0 ] && echo "[ OK ] every Deck and Workshop is in the README index"
exit $FAIL
