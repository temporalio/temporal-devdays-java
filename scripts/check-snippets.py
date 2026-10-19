#!/usr/bin/env python3
"""Fail when an assignment's code snippet has drifted from the Exercise.

Assignments show the learner the exact code to change, so they never scroll to
find it (ADR 0002). That only helps while the snippet and its line range are
true. Mark each snippet in an assignment.md like this:

    <!-- snippet: exercise/src/main/java/io/temporal/devdays/saga/OrderWorkflowImpl.java TODO 1 -->
    Open `OrderWorkflowImpl.java`. Lines 18-22 look like this:

    ```java
    ...
    ```

The snippet is the region of the file from the `// TODO 1 start` line to the
`// TODO 1 end` line, inclusive. Add `before=N` to the marker to include N lines
above the start marker, e.g. the method signature. Paths are relative to the
Workshop directory (the parent of `instruqt/`).

The prose between the marker and the fence must say `lines A-B` with the
region's real line numbers.

    python3 scripts/check-snippets.py [workshops/<slug>] [--fix]

With no path, checks every Workshop. --fix rewrites the fences and line ranges
in place instead of failing.
"""

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
MARKER = re.compile(r"<!--\s*snippet:\s*(\S+)\s+(TODO \w+)((?:\s+\w+=\d+)*)\s*-->")
LINES = re.compile(r"[Ll]ines? (\d+)-(\d+)")
FENCE = re.compile(r"^```[^\n]*\n(.*?)^```", re.M | re.S)


def region(path: Path, todo: str, before: int) -> tuple[int, int, str]:
    lines = path.read_text().splitlines()
    start = end = None
    for i, line in enumerate(lines):
        if re.search(rf"//\s*{todo} start\b", line):
            start = i
        elif re.search(rf"//\s*{todo} end\b", line):
            end = i
    if start is None or end is None or end < start:
        raise ValueError(f"{path.relative_to(REPO)} has no '// {todo} start' / '// {todo} end' pair")
    start = max(0, start - before)
    return start + 1, end + 1, "\n".join(lines[start : end + 1]) + "\n"


def check(assignment: Path, fix: bool) -> list[str]:
    workshop = assignment.parents[2]
    text = assignment.read_text()
    problems: list[str] = []
    out, pos = [], 0
    for m in MARKER.finditer(text):
        rel, todo, attrs = m.group(1), m.group(2), m.group(3)
        before = int(dict(a.split("=") for a in attrs.split()).get("before", 0))
        where = f"{assignment.relative_to(REPO)}: {rel} {todo}"
        fence = FENCE.search(text, m.end())
        if not fence:
            problems.append(f"{where}: no code fence after the marker")
            continue
        try:
            first, last, code = region(workshop / rel, todo, before)
        except (OSError, ValueError) as e:
            problems.append(f"{where}: {e}")
            continue
        prose = text[m.end() : fence.start()]
        rng = LINES.search(prose)
        if not rng:
            problems.append(f"{where}: the text before the snippet must say 'lines {first}-{last}'")
        elif (int(rng.group(1)), int(rng.group(2))) != (first, last):
            problems.append(f"{where}: says lines {rng.group(1)}-{rng.group(2)}, the code is at lines {first}-{last}")
        if fence.group(1) != code:
            problems.append(f"{where}: snippet does not match the file")
        if fix:
            if rng:
                prose = prose[: rng.start(1)] + f"{first}-{last}" + prose[rng.end(2) :]
            header = text[fence.start() : fence.start(1)]
            out.append(text[pos : m.end()] + prose + header + code + "```")
            pos = fence.end()
    if fix and out:
        assignment.write_text("".join(out) + text[pos:])
    return problems


def main() -> int:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    fix = "--fix" in sys.argv
    roots = [Path(a).resolve() for a in args] or [p for p in (REPO / "workshops").iterdir() if p.is_dir() and p.name != "_template"]
    assignments = sorted(a for r in roots for a in r.glob("instruqt/*/assignment.md"))
    problems = [p for a in assignments for p in check(a, fix)]
    snippets = sum(len(MARKER.findall(a.read_text())) for a in assignments)
    if problems and not fix:
        print("\n".join(f"[FAIL] {p}" for p in problems))
        print(f"{len(problems)} drifted snippet(s). Re-run with --fix to rewrite them from the code.")
        return 1
    print(f"[ OK ] {snippets} snippet(s) in {len(assignments)} assignment(s) match the code" + (" (fixed)" if fix and problems else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
