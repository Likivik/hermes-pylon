#!/usr/bin/env python3
"""Every production `runCatching` must be logged, documented, or on a shrinking budget.

The inventory (docs/plans/2026-11-17-runCatching-inventory.md) classified the existing
sites. This check makes that classification enforceable: a *new* silent swallow fails CI,
and the number of unclassified sites may only go down.

A site counts as classified when either:
  * a log call appears within the block (HermesLog / println / Logger), or
  * one of the markers `inventory: intentional-ignore` / `inventory: handled` appears
    within three lines above it (the reviewed decision, written next to the code).

The budget below is the current tail. Lower it whenever you classify a site; the check
fails if the budget is stale in either direction, so it cannot quietly drift.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

# Current number of unclassified production sites. Only ever decrease.
MAX_UNCLASSIFIED = 0

LOG_RE = re.compile(r"HermesLog\.|println\(|Logger\.")
MARKERS = ("inventory: intentional-ignore", "inventory: handled")
RUN_CATCHING_RE = re.compile(r"runCatching\s*\{")


def production_files(root: Path) -> list[Path]:
    files: list[Path] = []
    for pattern in ("*/src/*Main/**/*.kt",):
        for path in root.glob(pattern):
            if "build/" in path.parts:
                continue
            files.append(path)
    return sorted(set(files))


def unclassified_sites(root: Path) -> list[str]:
    sites: list[str] = []
    for path in production_files(root):
        rel = path.relative_to(root).as_posix()
        text = path.read_text(encoding="utf-8", errors="ignore")
        lines = text.split("\n")
        for match in RUN_CATCHING_RE.finditer(text):
            line_no = text[: match.start()].count("\n") + 1
            block_tail = text[match.end() : match.end() + 700]
            if LOG_RE.search(block_tail):
                continue
            context = "\n".join(lines[max(0, line_no - 4) : line_no + 1])
            if any(marker in context for marker in MARKERS):
                continue
            sites.append(f"{rel}:{line_no}")
    return sites


def check(root: Path, budget: int) -> tuple[list[str], list[str]]:
    sites = unclassified_sites(root)
    problems: list[str] = []
    notes: list[str] = []
    if len(sites) > budget:
        problems.append(
            f"{len(sites)} unclassified silent catches, budget is {budget} — "
            "log it, or mark it `inventory: intentional-ignore` / `inventory: handled`"
        )
        for site in sites:
            notes.append(f"  unclassified {site}")
    elif len(sites) < budget:
        problems.append(
            f"{len(sites)} unclassified silent catches but the budget is {budget} — "
            f"lower MAX_UNCLASSIFIED to {len(sites)} so the ratchet keeps its value"
        )
    return problems, notes


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-root", default=Path(__file__).resolve().parent.parent)
    args = parser.parse_args()
    root = Path(args.repo_root).resolve()

    problems, notes = check(root, MAX_UNCLASSIFIED)
    if problems:
        for problem in problems:
            print(f"FAIL {problem}")
        for note in notes[:20]:
            print(note)
        if len(notes) > 20:
            print(f"  ... and {len(notes) - 20} more")
        return 1

    print(f"OK {MAX_UNCLASSIFIED} unclassified silent catches, no new ones (budget is a ratchet)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
