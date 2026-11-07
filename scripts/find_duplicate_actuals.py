#!/usr/bin/env python3
"""Report `actual` declarations whose bodies are identical across targets.

`check_kmp_boundary.py` guards imports and missing actuals. It does not notice
that two targets carry the same implementation — which is what makes a dedupe
possible and, before this, had to be discovered by hand.

Usage:
    python3 scripts/find_duplicate_actuals.py [ui/src ...]

Output, one line per symbol that has more than one `actual`:

    SAME  <symbol>  [androidMain, jvmMain]
    DIFF  <symbol>  [androidMain, wasmJsMain]

Exit status is always 0: this reports, it does not gate. A dedupe PR decides
what to do with the SAME list, and the DIFF list is the evidence for what must
stay platform-specific.
"""

import argparse
import re
import sys
from pathlib import Path

PLATFORM_SETS = ("androidMain", "jvmMain", "wasmJsMain", "jsMain", "iosMain", "jvmAndroidMain", "desktopMain")
DECL = re.compile(r"^\s*(?:public |internal |private )?actual (?:inline )?(fun|val|var|class|object) (\w+)", re.MULTILINE)
COMMENT = re.compile(r"/\*.*?\*/", re.DOTALL)


def normalise(text: str) -> str:
    """Strip comments and blank lines so KDoc-only differences do not read as real ones."""
    text = COMMENT.sub("", text)
    lines = [line.strip() for line in text.splitlines()]
    return "\n".join(line for line in lines if line)


def collect(root: Path) -> dict:
    found: dict = {}
    for source_set in PLATFORM_SETS:
        base = root / source_set / "kotlin"
        if not base.is_dir():
            continue
        for path in sorted(base.rglob("*.kt")):
            text = path.read_text(encoding="utf-8")
            for kind, name in DECL.findall(text):
                found.setdefault(name, []).append((source_set, normalise(text), path))
    return found


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("roots", nargs="*", default=["ui/src"])
    args = parser.parse_args()

    duplicates = 0
    for root_arg in args.roots:
        root = Path(root_arg)
        if not root.is_dir():
            print(f"skip: {root} is not a directory", file=sys.stderr)
            continue
        found = collect(root)
        for name, entries in sorted(found.items()):
            if len(entries) < 2:
                continue
            # Group by body, not "all equal": a three-way symbol is often
            # android-differs / desktop-and-web-identical, and that pair is
            # exactly what a dedupe looks for.
            groups: dict = {}
            for source_set, body, _ in entries:
                groups.setdefault(body, set()).add(source_set)
            pairs = [sets for sets in groups.values() if len(sets) > 1]
            every = sorted({s for s, _, _ in entries})
            if pairs:
                for group in pairs:
                    duplicates += 1
                    print(f"SAME\t{name}\t{sorted(group)}\tof {every}")
            else:
                print(f"DIFF\t{name}\t{every}")

    print(f"\n{duplicates} identical actual(s) — dedupe candidates", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
